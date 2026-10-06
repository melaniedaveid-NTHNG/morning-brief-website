package tech.nothing.agentos.glyph

import tech.nothing.agentos.agent.AgentModel
import tech.nothing.agentos.agent.AgentModel.Companion.smoothstep
import tech.nothing.agentos.agent.Mode
import tech.nothing.agentos.agent.Stimulus
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

/** Samples the agent onto the Phone (3)'s round 25x25 Glyph Matrix. */
class MatrixRenderer(private val model: AgentModel) {

    private val frame = IntArray(SIZE * SIZE)
    private val pose = model.Snapshot()

    /** Returns a row-major brightness frame, 0..[GlyphMatrix.MAX_BRIGHTNESS]. Reuses one buffer. */
    fun render(t: Float, s: Stimulus): IntArray {
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

    companion object {
        const val SIZE = 25

        /** Model units across the panel. Smaller = more zoomed in on the core. */
        private const val SPAN = 11f
    }
}
