package tech.nothing.agentos

import android.Manifest
import android.app.Application
import android.app.role.RoleManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tech.nothing.agentos.agent.AgentModel
import tech.nothing.agentos.agent.Mode
import tech.nothing.agentos.agent.Stimulus
import tech.nothing.agentos.device.AgentSettings
import tech.nothing.agentos.device.Apps
import tech.nothing.agentos.device.DeviceInfo
import tech.nothing.agentos.device.Exchange
import tech.nothing.agentos.device.LaunchableApp
import tech.nothing.agentos.device.Store
import tech.nothing.agentos.glyph.GlyphMatrix
import tech.nothing.agentos.glyph.MatrixFeed
import tech.nothing.agentos.glyph.MatrixRenderer
import tech.nothing.agentos.input.ButtonAction
import tech.nothing.agentos.voice.CommandRouter
import tech.nothing.agentos.voice.VoiceAgent

/** The off-canvas areas around the agent. */
enum class Panel { HOME, APPS, SETTINGS, HISTORY }

/** Everything the screen shows besides the agent's body. */
data class UiState(
    val mode: Mode = Mode.IDLE,
    val heard: String = "",
    val reply: String = "",
    val panel: Panel = Panel.HOME,
    val hint: String? = null,
)

data class Permissions(
    val microphone: Boolean = false,
    val defaultHome: Boolean = false,
    val glyphMatrix: Boolean = false,
)

/**
 * The OS's state machine.
 *
 *   IDLE --tap--> INFO --tap/timeout--> IDLE
 *   any --double tap / button--> LISTENING --speech end--> THINKING --> SPEAKING --> IDLE
 */
class AgentViewModel(app: Application) : AndroidViewModel(app), VoiceAgent.Listener {

    val model = AgentModel()

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui

    /** Read every frame by the renderer, so it lives in snapshot state rather than a flow. */
    val stimulus = mutableStateOf(Stimulus(Mode.IDLE))

    private val _askMicPermission = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val askMicPermission: SharedFlow<Unit> = _askMicPermission

    private val _apps = MutableStateFlow<List<LaunchableApp>>(emptyList())
    val apps: StateFlow<List<LaunchableApp>> = _apps

    private val store = Store(app)

    private val _history = MutableStateFlow(store.loadHistory())
    val history: StateFlow<List<Exchange>> = _history

    private val _settings = MutableStateFlow(store.loadSettings())
    val settings: StateFlow<AgentSettings> = _settings

    private val _permissions = MutableStateFlow(Permissions())
    val permissions: StateFlow<Permissions> = _permissions

    private val voice = VoiceAgent(app, this)
    private val router = CommandRouter(app) { _apps.value }
    private val matrix = GlyphMatrix(app, forToy = false)
    private val matrixRenderer = MatrixRenderer(model)

    private var rawLevel = 0f
    private var infoJob: Job? = null
    private var hintTimeout: Job? = null
    private var matrixLoop: Job? = null
    private var silentReplyTimeout: Job? = null

    val hasMicPermission: Boolean get() = _permissions.value.microphone

    init {
        refreshApps()
        refreshPermissions()
        // Smooth the mic level so the agent swells rather than jitters.
        viewModelScope.launch {
            while (isActive) {
                val s = stimulus.value
                val level = s.level + (rawLevel - s.level) * 0.25f
                if (level != s.level) stimulus.value = s.copy(level = level)
                MatrixFeed.stimulus = stimulus.value
                delay(16)
            }
        }
    }

    // --- Gestures & buttons ------------------------------------------------------------------

    fun onTap() = when (_ui.value.mode) {
        Mode.IDLE -> showInfo()
        Mode.LISTENING -> voice.finishListening()
        else -> dismiss()
    }

    fun onDoubleTap() = wake()

    fun onButton(action: ButtonAction, released: Boolean) {
        when (action) {
            ButtonAction.WAKE_AGENT -> if (_ui.value.mode == Mode.LISTENING) voice.finishListening() else wake()
            ButtonAction.TOGGLE_INFO -> if (_ui.value.mode == Mode.INFO) dismiss() else showInfo()
            ButtonAction.DISMISS -> if (_ui.value.panel != Panel.HOME) openPanel(Panel.HOME) else dismiss()
            ButtonAction.PUSH_TO_TALK -> if (released) voice.finishListening() else wake()
        }
    }

    fun onUnmappedKey(keyCode: Int) = hint("key $keyCode · unmapped")

    // --- Panels -------------------------------------------------------------------------------

    fun openPanel(panel: Panel) {
        if (panel == Panel.APPS) refreshApps()
        if (panel == Panel.SETTINGS) refreshPermissions()
        _ui.value = _ui.value.copy(panel = panel)
    }

    fun launch(app: LaunchableApp) {
        openPanel(Panel.HOME)
        getApplication<Application>().startActivity(app.intent())
    }

    fun setSpeakReplies(on: Boolean) = updateSettings(_settings.value.copy(speakReplies = on))
    fun setMatrixReplies(on: Boolean) = updateSettings(_settings.value.copy(matrixReplies = on))

    private fun updateSettings(s: AgentSettings) {
        _settings.value = s
        store.saveSettings(s)
        if (!s.speakReplies) voice.stopSpeaking()
        if (!s.matrixReplies) MatrixFeed.clear()
    }

    fun clearHistory() {
        _history.value = emptyList()
        store.saveHistory(emptyList())
    }

    /** Ask again: replays a past question as if it had just been spoken. */
    fun ask(text: String) {
        openPanel(Panel.HOME)
        voice.cancelListening()
        respond(text)
    }

    fun refreshPermissions() {
        val context = getApplication<Application>()
        val roles = context.getSystemService(Context.ROLE_SERVICE) as RoleManager
        _permissions.value = Permissions(
            microphone = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED,
            defaultHome = roles.isRoleAvailable(RoleManager.ROLE_HOME) && roles.isRoleHeld(RoleManager.ROLE_HOME),
            glyphMatrix = matrix.available,
        )
    }

    // --- States -------------------------------------------------------------------------------

    fun wake() {
        if (_ui.value.mode == Mode.LISTENING) return
        openPanel(Panel.HOME)
        if (!hasMicPermission) {
            _askMicPermission.tryEmit(Unit)
            return
        }
        if (!voice.canListen) {
            say("", "Speech recognition isn't available on this phone.")
            return
        }
        MatrixFeed.clear()
        setMode(Mode.LISTENING, heard = "", reply = "")
        voice.listen()
    }

    fun showInfo() {
        setMode(Mode.INFO)
        val context = getApplication<Application>()
        infoJob = viewModelScope.launch {
            val until = SystemClock.elapsedRealtime() + INFO_TIMEOUT_MS
            while (SystemClock.elapsedRealtime() < until) {
                MatrixFeed.clock = DeviceInfo.snapshot(context).time
                delay(1000)
            }
            if (_ui.value.mode == Mode.INFO) setMode(Mode.IDLE)
        }
    }

    fun dismiss() {
        voice.cancelListening()
        voice.stopSpeaking()
        MatrixFeed.clear()
        setMode(Mode.IDLE)
    }

    private fun setMode(mode: Mode, heard: String = _ui.value.heard, reply: String = _ui.value.reply) {
        infoJob?.cancel()
        silentReplyTimeout?.cancel()
        if (mode != Mode.INFO) MatrixFeed.clock = null
        if (mode != Mode.LISTENING) rawLevel = 0f
        _ui.value = _ui.value.copy(mode = mode, heard = heard, reply = reply)
        stimulus.value = stimulus.value.copy(mode = mode)
    }

    private fun say(heard: String, reply: String) {
        setMode(Mode.SPEAKING, heard = heard, reply = reply)
        if (_settings.value.matrixReplies) MatrixFeed.say(reply)
        if (_settings.value.speakReplies) {
            voice.speak(reply)
        } else {
            // Nothing to wait for: leave the reply on screen long enough to read.
            silentReplyTimeout = viewModelScope.launch {
                delay(1500L + reply.length * 50L)
                if (_ui.value.mode == Mode.SPEAKING) setMode(Mode.IDLE)
            }
        }
    }

    private fun respond(text: String) {
        setMode(Mode.THINKING, heard = text)
        viewModelScope.launch {
            delay(THINK_MS) // let the thinking ripple read before answering
            val reply = router.route(text)
            say(text, reply.say)
            record(text, reply.say)
            reply.action?.invoke()
            if (reply.showInfo) {
                delay(900)
                showInfo()
            }
        }
    }

    private fun record(heard: String, reply: String) {
        val updated = (listOf(Exchange(System.currentTimeMillis(), heard, reply)) + _history.value).take(Store.MAX_HISTORY)
        _history.value = updated
        store.saveHistory(updated)
    }

    private fun hint(text: String) {
        _ui.value = _ui.value.copy(hint = text)
        hintTimeout?.cancel()
        hintTimeout = viewModelScope.launch {
            delay(2500)
            _ui.value = _ui.value.copy(hint = null)
        }
    }

    // --- VoiceAgent.Listener ------------------------------------------------------------------

    override fun onLevel(level: Float) {
        rawLevel = level
    }

    override fun onPartial(text: String) {
        if (_ui.value.mode == Mode.LISTENING) _ui.value = _ui.value.copy(heard = text)
    }

    override fun onHeard(text: String) {
        if (_ui.value.mode == Mode.LISTENING) respond(text)
    }

    override fun onNothingHeard() {
        if (_ui.value.mode == Mode.LISTENING) setMode(Mode.IDLE)
    }

    override fun onDoneSpeaking() {
        viewModelScope.launch {
            delay(800)
            if (_ui.value.mode == Mode.SPEAKING) setMode(Mode.IDLE)
        }
    }

    // --- Glyph Matrix -------------------------------------------------------------------------

    /** Mirror the agent onto the Matrix while the OS is in the foreground. */
    fun startMatrix() {
        matrix.connect()
        if (matrixLoop?.isActive == true) return
        val start = SystemClock.elapsedRealtime()
        matrixLoop = viewModelScope.launch(Dispatchers.Default) {
            var wasAvailable = false
            while (isActive) {
                val available = matrix.available
                if (available) {
                    val t = (SystemClock.elapsedRealtime() - start) / 1000f
                    matrix.show(matrixRenderer.render(t))
                }
                if (available != wasAvailable) {
                    wasAvailable = available
                    withContext(Dispatchers.Main) { refreshPermissions() }
                }
                delay(MATRIX_FRAME_MS)
            }
        }
    }

    fun stopMatrix() {
        matrixLoop?.cancel()
        matrix.disconnect()
    }

    private fun refreshApps() {
        viewModelScope.launch {
            _apps.value = withContext(Dispatchers.Default) { Apps.load(getApplication()) }
        }
    }

    override fun onCleared() {
        stopMatrix()
        voice.release()
    }

    private companion object {
        const val INFO_TIMEOUT_MS = 10_000L
        const val THINK_MS = 600L
        const val MATRIX_FRAME_MS = 66L
    }
}
