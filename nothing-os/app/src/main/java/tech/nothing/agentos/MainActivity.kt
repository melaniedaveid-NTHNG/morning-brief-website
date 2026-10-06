package tech.nothing.agentos

import android.Manifest
import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import tech.nothing.agentos.agent.Mode
import tech.nothing.agentos.input.Buttons
import tech.nothing.agentos.ui.AgentScreen

/**
 * Agent OS entry point. Registered as a HOME activity so it can replace the launcher.
 */
class MainActivity : ComponentActivity() {

    private val vm: AgentViewModel by viewModels()
    private lateinit var buttons: Buttons

    /** Whether the pending microphone request came from waking the agent. */
    private var wakeAfterMic = false

    private val micPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.refreshPermissions()
        when {
            granted -> if (wakeAfterMic) vm.wake()
            // Denied for good: Android won't ask again, so the switch lives in App info.
            !shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO) -> openAppInfo()
        }
    }

    private val homeRole = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        vm.refreshPermissions()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        immersive()

        buttons = Buttons(
            getSystemService(AUDIO_SERVICE) as AudioManager,
            onAction = vm::onButton,
            onUnmappedKey = vm::onUnmappedKey,
        )

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.askMicPermission.collect { requestMic(wakeAfter = true) }
            }
        }

        setContent {
            BackHandler {
                val ui = vm.ui.value
                if (ui.panel != Panel.HOME) vm.openPanel(Panel.HOME) else if (ui.mode != Mode.IDLE) vm.dismiss()
                // As the home screen there's nothing to go back to: swallow it.
            }
            AgentScreen(
                vm,
                onRequestMic = { requestMic(wakeAfter = false) },
                onRequestHome = ::requestHomeRole,
                onOpen = ::openSafely,
            )
        }
        handle(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        when (intent?.action) {
            ACTION_WAKE, Intent.ACTION_ASSIST, Intent.ACTION_VOICE_COMMAND -> vm.wake()
            // Pressing Home while already home closes whatever panel is open.
            Intent.ACTION_MAIN -> if (intent.hasCategory(Intent.CATEGORY_HOME)) vm.openPanel(Panel.HOME)
        }
    }

    override fun onStart() {
        super.onStart()
        vm.refreshPermissions()
        vm.startMatrix()
    }

    override fun onStop() {
        vm.stopMatrix()
        super.onStop()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) immersive()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean =
        buttons.onKeyDown(keyCode, event) || super.onKeyDown(keyCode, event)

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean =
        buttons.onKeyUp(keyCode, event) || super.onKeyUp(keyCode, event)

    private fun requestMic(wakeAfter: Boolean) {
        wakeAfterMic = wakeAfter
        micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun requestHomeRole() {
        val roles = getSystemService(ROLE_SERVICE) as RoleManager
        if (roles.isRoleAvailable(RoleManager.ROLE_HOME) && !roles.isRoleHeld(RoleManager.ROLE_HOME)) {
            homeRole.launch(roles.createRequestRoleIntent(RoleManager.ROLE_HOME))
        } else {
            openSafely(Intent(Settings.ACTION_HOME_SETTINGS))
        }
    }

    private fun openAppInfo() =
        openSafely(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))

    private fun openSafely(intent: Intent) {
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    /** Minimal means no chrome: system bars stay hidden until swiped in. */
    private fun immersive() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    companion object {
        const val ACTION_WAKE = "tech.nothing.agentos.WAKE"
    }
}
