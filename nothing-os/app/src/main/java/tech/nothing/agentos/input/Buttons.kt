package tech.nothing.agentos.input

import android.media.AudioManager
import android.os.SystemClock
import android.view.KeyEvent

/** What a hardware button can do in Agent OS. */
enum class ButtonAction { WAKE_AGENT, TOGGLE_INFO, DISMISS, PUSH_TO_TALK }

/**
 * @param tap  action on a short press.
 * @param hold action while held (fires on hold, and again with `released = true` on release).
 *             null keeps the key's normal hold behaviour (volume keys keep changing volume).
 */
data class Binding(val tap: ButtonAction, val hold: ButtonAction?)

/**
 * Button map for the Phone (3). Change features here.
 *
 * Power stays with the system (apps can't intercept it). The Glyph Button on the back is
 * handled by [tech.nothing.agentos.glyph.AgentGlyphToy], not here.
 *
 * Essential Key: the keycode it delivers to apps (if any) isn't documented. Until it's
 * confirmed, any unmapped key shows its code on screen. Press it once, then add its code here.
 */
object ButtonMap {
    val bindings: Map<Int, Binding> = mapOf(
        KeyEvent.KEYCODE_VOLUME_UP to Binding(tap = ButtonAction.TOGGLE_INFO, hold = null),
        KeyEvent.KEYCODE_VOLUME_DOWN to Binding(tap = ButtonAction.DISMISS, hold = null),
        // Essential Key candidates. Tap wakes the agent, hold is push-to-talk.
        KeyEvent.KEYCODE_ASSIST to Binding(tap = ButtonAction.WAKE_AGENT, hold = ButtonAction.PUSH_TO_TALK),
        KeyEvent.KEYCODE_VOICE_ASSIST to Binding(tap = ButtonAction.WAKE_AGENT, hold = ButtonAction.PUSH_TO_TALK),
    )
}

/**
 * Turns raw key events into taps and holds. Feed it from Activity.onKeyDown / onKeyUp.
 */
class Buttons(
    private val audio: AudioManager,
    private val onAction: (action: ButtonAction, released: Boolean) -> Unit,
    private val onUnmappedKey: (keyCode: Int) -> Unit,
) {
    private val holding = HashSet<Int>()
    private var lastVolumeStep = 0L

    fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val binding = ButtonMap.bindings[keyCode]
        if (binding == null) {
            if (event.repeatCount == 0 && keyCode !in SYSTEM_KEYS) onUnmappedKey(keyCode)
            return false
        }
        if (event.repeatCount == 0) {
            event.startTracking()
            return true
        }
        // Held: the first repeat arrives after the long-press timeout.
        if (holding.add(keyCode)) binding.hold?.let { onAction(it, false) }
        if (binding.hold == null) stepVolume(keyCode)
        return true
    }

    fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        val binding = ButtonMap.bindings[keyCode] ?: return false
        if (holding.remove(keyCode)) {
            binding.hold?.let { onAction(it, true) }
        } else if (!event.isCanceled) {
            onAction(binding.tap, false)
        }
        return true
    }

    /** Keep volume keys useful: holding them still changes volume, at a sane pace. */
    private fun stepVolume(keyCode: Int) {
        val direction = when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> AudioManager.ADJUST_RAISE
            KeyEvent.KEYCODE_VOLUME_DOWN -> AudioManager.ADJUST_LOWER
            else -> return
        }
        val now = SystemClock.uptimeMillis()
        if (now - lastVolumeStep < VOLUME_STEP_MS) return
        lastVolumeStep = now
        audio.adjustSuggestedStreamVolume(direction, AudioManager.USE_DEFAULT_STREAM_TYPE, AudioManager.FLAG_SHOW_UI)
    }

    private companion object {
        const val VOLUME_STEP_MS = 140L
        val SYSTEM_KEYS = setOf(KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_HOME, KeyEvent.KEYCODE_APP_SWITCH, KeyEvent.KEYCODE_POWER)
    }
}
