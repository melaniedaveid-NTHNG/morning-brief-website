package tech.nothing.agentos

import android.app.Application
import android.os.SystemClock
import androidx.compose.runtime.mutableStateOf
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
import tech.nothing.agentos.device.Apps
import tech.nothing.agentos.device.LaunchableApp
import tech.nothing.agentos.glyph.GlyphMatrix
import tech.nothing.agentos.glyph.MatrixRenderer
import tech.nothing.agentos.input.ButtonAction
import tech.nothing.agentos.voice.CommandRouter
import tech.nothing.agentos.voice.VoiceAgent

/** Everything the screen shows besides the agent's body. */
data class UiState(
    val mode: Mode = Mode.IDLE,
    val heard: String = "",
    val reply: String = "",
    val drawerOpen: Boolean = false,
    val hint: String? = null,
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

    private val voice = VoiceAgent(app, this)
    private val router = CommandRouter(app) { _apps.value }
    private val matrix = GlyphMatrix(app, forToy = false)
    private val matrixRenderer = MatrixRenderer(model)

    private var rawLevel = 0f
    private var infoTimeout: Job? = null
    private var hintTimeout: Job? = null
    private var matrixLoop: Job? = null
    var hasMicPermission = false

    init {
        refreshApps()
        // Smooth the mic level so the agent swells rather than jitters.
        viewModelScope.launch {
            while (isActive) {
                val s = stimulus.value
                val level = s.level + (rawLevel - s.level) * 0.25f
                if (level != s.level) stimulus.value = s.copy(level = level)
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
            ButtonAction.DISMISS -> dismiss()
            ButtonAction.PUSH_TO_TALK -> if (released) voice.finishListening() else wake()
        }
    }

    fun onUnmappedKey(keyCode: Int) = hint("key $keyCode · unmapped")

    fun setDrawer(open: Boolean) {
        if (open) refreshApps()
        _ui.value = _ui.value.copy(drawerOpen = open)
    }

    fun launch(app: LaunchableApp) {
        setDrawer(false)
        getApplication<Application>().startActivity(app.intent())
    }

    // --- States -------------------------------------------------------------------------------

    fun wake() {
        if (_ui.value.mode == Mode.LISTENING) return
        if (!hasMicPermission) {
            _askMicPermission.tryEmit(Unit)
            return
        }
        if (!voice.canListen) {
            say("", "Speech recognition isn't available on this phone.")
            return
        }
        setMode(Mode.LISTENING, heard = "", reply = "")
        voice.listen()
    }

    fun showInfo() {
        setMode(Mode.INFO)
        infoTimeout = viewModelScope.launch {
            delay(INFO_TIMEOUT_MS)
            if (_ui.value.mode == Mode.INFO) setMode(Mode.IDLE)
        }
    }

    fun dismiss() {
        voice.cancelListening()
        voice.stopSpeaking()
        setMode(Mode.IDLE)
    }

    private fun setMode(mode: Mode, heard: String = _ui.value.heard, reply: String = _ui.value.reply) {
        infoTimeout?.cancel()
        if (mode != Mode.LISTENING) rawLevel = 0f
        _ui.value = _ui.value.copy(mode = mode, heard = heard, reply = reply, drawerOpen = false)
        stimulus.value = stimulus.value.copy(mode = mode)
    }

    private fun say(heard: String, reply: String) {
        setMode(Mode.SPEAKING, heard = heard, reply = reply)
        voice.speak(reply)
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
        if (_ui.value.mode != Mode.LISTENING) return
        setMode(Mode.THINKING, heard = text)
        viewModelScope.launch {
            delay(THINK_MS) // let the thinking ripple read before answering
            val reply = router.route(text)
            say(text, reply.say)
            reply.action?.invoke()
            if (reply.showInfo) {
                delay(900)
                showInfo()
            }
        }
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
            while (isActive) {
                if (matrix.available) {
                    val t = (SystemClock.elapsedRealtime() - start) / 1000f
                    matrix.show(matrixRenderer.render(t, stimulus.value))
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
            _apps.value = withContext(Dispatchers.Default) {
                Apps.load(getApplication())
            }
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
