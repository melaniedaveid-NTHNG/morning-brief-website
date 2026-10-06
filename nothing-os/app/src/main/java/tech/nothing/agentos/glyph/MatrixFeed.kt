package tech.nothing.agentos.glyph

import android.os.SystemClock
import tech.nothing.agentos.agent.Mode
import tech.nothing.agentos.agent.Stimulus

/**
 * What the Glyph Matrix should show, shared across the app process: the agent's current state
 * and the latest thing it said. Both the in-app Matrix loop and the Glyph Toy read from here,
 * so a reply shows on the Matrix whichever one owns it.
 */
object MatrixFeed {
    @Volatile var stimulus: Stimulus = Stimulus(Mode.IDLE)

    /** "HH:mm" while the info view is open, else null. */
    @Volatile var clock: String? = null

    @Volatile private var message: Message? = null

    class Message(val columns: IntArray, val startedAt: Long)

    /** Show the agent's reply on the Matrix: scrolled once if it's too wide, else held. */
    fun say(text: String) {
        val cols = MatrixFont.columns(text.trim())
        message = if (cols.isEmpty()) null else Message(cols, SystemClock.elapsedRealtime())
    }

    fun clear() {
        message = null
    }

    /** The message still on screen at [now], or null once it has finished. */
    fun activeMessage(now: Long): Message? {
        val m = message ?: return null
        val elapsed = now - m.startedAt
        val done = if (m.columns.size <= MatrixRenderer.SIZE) elapsed > HOLD_MS
        else elapsed > SCROLL_LEAD_MS + (m.columns.size + MatrixRenderer.SIZE) * 1000L / SCROLL_PX_PER_S
        if (done) {
            if (message === m) message = null
            return null
        }
        return m
    }

    const val HOLD_MS = 3500L
    const val SCROLL_LEAD_MS = 300L
    const val SCROLL_PX_PER_S = 14
}
