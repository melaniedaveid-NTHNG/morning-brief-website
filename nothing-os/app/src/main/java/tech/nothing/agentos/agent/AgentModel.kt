package tech.nothing.agentos.agent

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

/**
 * The agent's body: a mirrored cluster of "cells" on a grid (the bright core) wrapped in a
 * looser shell of outlined bubbles, pills and dots. Everything is in model units (one grid
 * step = 1); renderers scale it to pixels or to the 25x25 Glyph Matrix.
 *
 * The geometry is grown once from a seed, so the agent is always the same being, and animated
 * by the pose functions below, which react to a [Stimulus].
 *
 * prototype/agent.js is a line-by-line port of this file: keep the two in sync.
 */
class AgentModel(seed: Int = 7) {

    enum class Kind { FILL, RING, SATELLITE }

    class Cell(val x: Float, val y: Float, val r: Float, val kind: Kind, val phase: Float, val pip: Boolean)
    class Link(val a: Int, val b: Int, val width: Float)
    class Bubble(val x: Float, val y: Float, val r: Float, val grey: Boolean, val phase: Float)
    class Pill(val x: Float, val y: Float, val length: Float, val vertical: Boolean, val bright: Boolean)

    val cells = ArrayList<Cell>()
    val links = ArrayList<Link>()
    val bubbles = ArrayList<Bubble>()
    val pills = ArrayList<Pill>()

    init {
        build(Mulberry32(seed))
    }

    private fun build(rnd: Mulberry32) {
        val index = LinkedHashMap<Pair<Int, Int>, Int>()

        fun add(gx: Int, gy: Int, cell: Cell) {
            index[gx to gy] = cells.size
            cells += cell
        }

        // Core: grow the right half (gx >= 0) and mirror it.
        for (gy in -5..5) for (gx in 0..3) {
            val e = (gx / 2.7f).pow(2) + (gy / 4.7f).pow(2)
            if (e > 1f) continue
            if (rnd.f() > (if (e < 0.4f) 0.9f else 0.68f)) continue
            val ring = rnd.f() < (if (gx == 0) 0.4f else 0.14f)
            val r = if (ring) 0.48f else 0.33f + rnd.f() * 0.12f
            val phase = rnd.f() * TAU
            val pip = !ring && rnd.f() < 0.18f
            val kind = if (ring) Kind.RING else Kind.FILL
            add(gx, gy, Cell(gx.toFloat(), gy.toFloat(), r, kind, phase, pip))
            if (gx > 0) add(-gx, gy, Cell(-gx.toFloat(), gy.toFloat(), r, kind, phase + 0.7f, pip))
        }

        fun linkMirrored(ax: Int, ay: Int, bx: Int, by: Int, width: Float) {
            val a = index[ax to ay] ?: return
            val b = index[bx to by] ?: return
            links += Link(a, b, width)
            val ma = index[-ax to ay] ?: return
            val mb = index[-bx to by] ?: return
            val same = (ma == a && mb == b) || (ma == b && mb == a)
            if (!same) links += Link(ma, mb, width)
        }

        for (gy in -5..5) for (gx in 0..3) {
            if (index[gx to gy] == null) continue
            if (rnd.f() < 0.58f) linkMirrored(gx, gy, gx + 1, gy, 0.28f)
            if (rnd.f() < 0.58f) linkMirrored(gx, gy, gx, gy + 1, 0.28f)
        }

        // Ball-and-stick satellites poking out of the core's edge.
        val rightHalf = index.keys.filter { (gx, _) -> gx >= 0 }.sortedWith(compareBy({ it.second }, { it.first }))
        for ((gx, gy) in rightHalf) {
            if (rnd.f() > 0.3f) continue
            val dx = if (gx == 0) 1 else gx.sign
            val dy = if (rnd.b()) 1 else -1
            if (index[gx + dx to gy + dy] != null || index[gx + dx to gy] != null) continue
            val parent = index.getValue(gx to gy)
            for (mirror in if (gx == 0) listOf(1) else listOf(1, -1)) {
                val p = if (mirror == 1) parent else index[-gx to gy] ?: continue
                val sx = (gx + dx * 0.78f) * mirror
                val sy = gy + dy * 0.78f
                cells += Cell(sx, sy, 0.22f, Kind.SATELLITE, rnd.f() * TAU, pip = false)
                links += Link(p, cells.lastIndex, 0.22f)
            }
        }

        // Shell: a staggered lattice of loose bubbles around the core. Not mirrored.
        for (gy in -4..4) for (gx in -3..3) {
            val x = gx * 1.55f + if (gy % 2 != 0) 0.78f else 0f
            val y = gy * 1.45f
            val outer = (x / 4.5f).pow(2) + (y / 6.3f).pow(2)
            val inner = (x / 2.6f).pow(2) + (y / 4.5f).pow(2)
            if (outer > 1f || inner < 0.7f) continue
            if (rnd.f() > 0.75f) continue
            bubbles += Bubble(x, y, 0.68f + rnd.f() * 0.3f, rnd.f() < 0.22f, rnd.f() * TAU)
        }

        pills += Pill(-2.9f, 2.6f, 2.0f, vertical = true, bright = true)
        pills += Pill(2.4f, -5.5f, 1.8f, vertical = true, bright = false)
        pills += Pill(3.3f, -3.6f, 1.2f, vertical = false, bright = false)
        pills += Pill(3.0f, -1.4f, 1.1f, vertical = true, bright = false)
    }

    // --- Animation ---------------------------------------------------------------------------

    /** Size multiplier for a core cell at time [t]. Links use the mean of their two ends. */
    fun cellScale(i: Int, t: Float, s: Stimulus): Float {
        val c = cells[i]
        val breathe = 1f + 0.05f * sin(t * 1.1f + c.phase)
        val react = when (s.mode) {
            Mode.IDLE, Mode.INFO -> 1f
            Mode.LISTENING -> 1f + (0.12f + 0.42f * s.level) * (0.5f + 0.5f * sin(t * 6f + c.y * 1.3f + c.phase * 0.3f))
            Mode.THINKING -> 1f + 0.28f * max(0f, cos(hypot(c.x, c.y) * 1.3f - t * 5f)).pow(3)
            Mode.SPEAKING -> 1f + 0.2f * (0.5f + 0.5f * sin(t * 9f + c.phase)) * (0.5f + 0.5f * sin(t * 2.3f))
        }
        return breathe * react
    }

    fun cellX(i: Int, t: Float, s: Stimulus): Float = cells[i].x + wobble(s) * sin(t * 0.7f + cells[i].phase)
    fun cellY(i: Int, t: Float, s: Stimulus): Float = cells[i].y + wobble(s) * cos(t * 0.55f + cells[i].phase * 1.3f)

    private fun wobble(s: Stimulus) = 0.045f + if (s.mode == Mode.LISTENING) 0.06f * s.level else 0f

    /** The shell breathes outwards while the agent listens. */
    fun spread(s: Stimulus) = 1f + if (s.mode == Mode.LISTENING) 0.03f + 0.05f * s.level else 0f

    fun bubbleX(b: Bubble, t: Float, s: Stimulus) = (b.x + 0.08f * sin(t * 0.35f + b.phase)) * spread(s)
    fun bubbleY(b: Bubble, t: Float, s: Stimulus) = (b.y + 0.08f * cos(t * 0.3f + b.phase)) * spread(s)
    fun bubbleR(b: Bubble, t: Float) = b.r * (1f + 0.04f * sin(t * 0.8f + b.phase))

    /** How strongly the core glows, 0..1. */
    fun glow(s: Stimulus) = when (s.mode) {
        Mode.IDLE -> 0.45f
        Mode.INFO -> 0.3f
        Mode.LISTENING -> 0.65f + 0.35f * s.level
        Mode.THINKING -> 0.7f
        Mode.SPEAKING -> 0.75f
    }

    // --- Sampling (used by the Glyph Matrix) -------------------------------------------------

    /** Every cell's pose frozen at one instant, for sampling the shape many times per frame. */
    inner class Snapshot {
        private val x = FloatArray(cells.size)
        private val y = FloatArray(cells.size)
        private val scale = FloatArray(cells.size)

        fun update(t: Float, s: Stimulus): Snapshot {
            for (i in cells.indices) {
                x[i] = cellX(i, t, s)
                y[i] = cellY(i, t, s)
                scale[i] = cellScale(i, t, s)
            }
            return this
        }

        /**
         * Signed distance to the merged core (< 0 inside). The smooth-min melts nearby shapes
         * together the same way the screen's blur + threshold does.
         */
        fun distance(px: Float, py: Float): Float {
            var d = 1e9f
            for (i in cells.indices) {
                d = smin(d, hypot(px - x[i], py - y[i]) - cells[i].r * scale[i], GOO_K)
            }
            for (l in links) {
                val seg = segmentDistance(px, py, x[l.a], y[l.a], x[l.b], y[l.b])
                d = smin(d, seg - l.width * (scale[l.a] + scale[l.b]) / 4, GOO_K)
            }
            return d
        }

        /** 1 inside a ring cell's dark hole, 0 elsewhere (including the hole's bright pip). */
        fun hole(px: Float, py: Float): Float {
            for (i in cells.indices) {
                if (cells[i].kind != Kind.RING) continue
                val r = cells[i].r * scale[i]
                val d = hypot(px - x[i], py - y[i])
                if (d < r * 0.2f) return 0f
                if (d < r * 0.56f) return 1f
            }
            return 0f
        }
    }

    companion object {
        const val TAU = (2 * PI).toFloat()
        private const val GOO_K = 0.18f

        fun smin(a: Float, b: Float, k: Float): Float {
            val h = max(k - abs(a - b), 0f) / k
            return min(a, b) - h * h * k * 0.25f
        }

        fun segmentDistance(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
            val vx = bx - ax
            val vy = by - ay
            val len2 = vx * vx + vy * vy
            val u = if (len2 == 0f) 0f else (((px - ax) * vx + (py - ay) * vy) / len2).coerceIn(0f, 1f)
            return hypot(px - (ax + u * vx), py - (ay + u * vy))
        }

        fun smoothstep(a: Float, b: Float, x: Float): Float {
            val k = ((x - a) / (b - a)).coerceIn(0f, 1f)
            return k * k * (3 - 2 * k)
        }
    }

    /** mulberry32: tiny, fast, and trivially identical in JS (see prototype/agent.js). */
    private class Mulberry32(seed: Int) {
        private var a = seed

        fun f(): Float {
            a += 0x6d2b79f5
            var t = a
            t = (t xor (t ushr 15)) * (t or 1)
            t = t xor (t + (t xor (t ushr 7)) * (t or 61))
            return ((t xor (t ushr 14)).toUInt().toDouble() / 4294967296.0).toFloat()
        }

        fun b() = f() < 0.5f
    }
}

enum class Mode { IDLE, INFO, LISTENING, THINKING, SPEAKING }

/** What the agent is reacting to right now. [level] is the smoothed mic level, 0..1. */
data class Stimulus(val mode: Mode, val level: Float = 0f)
