package tech.nothing.agentos.glyph

import android.os.SystemClock
import tech.nothing.agentos.agent.AgentModel
import tech.nothing.agentos.agent.AgentModel.Companion.smoothstep
import tech.nothing.agentos.agent.Mode
import tech.nothing.agentos.agent.Stimulus
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Samples the agent onto the Phone (3)'s round 25x25 Glyph Matrix. When the agent says
 * something, the words take over the Matrix; in the info view it shows the clock.
 */
class MatrixRenderer(private val model: AgentModel) {

    private val frame = IntArray(SIZE * SIZE)
    private val pose = model.Snapshot()

    /** The current Matrix frame for [MatrixFeed]: row-major brightness, 0..[GlyphMatrix.MAX_BRIGHTNESS]. */
    fun render(t: Float, now: Long = SystemClock.elapsedRealtime()): IntArray {
        val message = MatrixFeed.activeMessage(now)
        val clock = MatrixFeed.clock
        return when {
            message != null -> renderText(message.columns, scrollX(message, now))
            clock != null -> renderText(MatrixFont.columns(clock), centeredX(MatrixFont.columns(clock).size))
            else -> renderAgent(t, MatrixFeed.stimulus)
        }
    }

    /** Returns a row-major brightness frame. Reuses one buffer. */
    fun renderAgent(t: Float, s: Stimulus): IntArray {
        val step = SPAN / SIZE
        val half = (SIZE - 1) / 2f
        pose.update(t, s)
        for (row in 0 until SIZE) for (col in 0 until SIZE) {
            val dx = col - half
            val dy = row - half
            var b = 0f
            if (dx * dx + dy * dy <= (half + 0.5f) * (half + 0.5f)) {
                val x = dx * step
                val y = dy * step
                b = smoothstep(step * 0.5f, -step * 0.5f, pose.distance(x, y)) * (1f - pose.hole(x, y))
                if (s.mode == Mode.LISTENING) {
                    // A halo that swells with your voice.
                    val ring = abs(hypot(dx, dy) - (9f + 3f * s.level))
                    b = max(b, 0.35f * smoothstep(1.2f, 0f, ring))
                }
            }
            frame[row * SIZE + col] = (b * GlyphMatrix.MAX_BRIGHTNESS).roundToInt()
        }
        return frame
    }

    /** Draws text columns with the first column at matrix x = [x], vertically centred. */
    fun renderText(columns: IntArray, x: Int): IntArray {
        frame.fill(0)
        val top = (SIZE - MatrixFont.HEIGHT) / 2
        for (i in columns.indices) {
            val col = x + i
            if (col !in 0 until SIZE) continue
            for (row in 0 until MatrixFont.HEIGHT) {
                if (columns[i] and (1 shl row) != 0) frame[(top + row) * SIZE + col] = GlyphMatrix.MAX_BRIGHTNESS
            }
        }
        return frame
    }

    private fun centeredX(width: Int) = (SIZE - width) / 2

    private fun scrollX(m: MatrixFeed.Message, now: Long): Int {
        if (m.columns.size <= SIZE) return centeredX(m.columns.size)
        val elapsed = (now - m.startedAt - MatrixFeed.SCROLL_LEAD_MS).coerceAtLeast(0)
        return SIZE - (elapsed * MatrixFeed.SCROLL_PX_PER_S / 1000L).toInt()
    }

    companion object {
        const val SIZE = 25

        /** Model units across the panel. Smaller = more zoomed in on the core. */
        private const val SPAN = 11f
    }
}
