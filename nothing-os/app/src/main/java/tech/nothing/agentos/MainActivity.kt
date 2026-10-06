package tech.nothing.agentos

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
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

    private val micPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.hasMicPermission = granted
        if (granted) vm.wake()
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
                vm.askMicPermission.collect { micPermission.launch(Manifest.permission.RECORD_AUDIO) }
            }
        }

        setContent {
            BackHandler {
                val ui = vm.ui.value
                if (ui.drawerOpen) vm.setDrawer(false) else if (ui.mode != Mode.IDLE) vm.dismiss()
                // As the home screen there's nothing to go back to: swallow it.
            }
            AgentScreen(vm)
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
            Intent.ACTION_MAIN -> if (intent.hasCategory(Intent.CATEGORY_HOME)) vm.setDrawer(false)
        }
    }

    override fun onStart() {
        super.onStart()
        vm.hasMicPermission =
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
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
