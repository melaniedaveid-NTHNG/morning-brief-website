package tech.nothing.agentos.glyph

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.SystemClock
import tech.nothing.agentos.MainActivity
import tech.nothing.agentos.agent.AgentModel

/**
 * The agent as a Glyph Toy: pick it with the Glyph Button on the back and it lives on the
 * Matrix, showing the agent's state and replies (see [MatrixFeed]). Long-press the Glyph
 * Button to wake the agent on screen.
 */
class AgentGlyphToy : Service() {

    private val model = AgentModel()
    private val renderer = MatrixRenderer(model)
    private lateinit var matrix: GlyphMatrix
    private val main = Handler(Looper.getMainLooper())
    private val start = SystemClock.elapsedRealtime()

    private val tick = object : Runnable {
        override fun run() {
            val t = (SystemClock.elapsedRealtime() - start) / 1000f
            matrix.show(renderer.render(t))
            main.postDelayed(this, FRAME_MS)
        }
    }

    private val messenger = Messenger(object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            if (msg.what != ToyEvents.MSG_GLYPH_TOY) return super.handleMessage(msg)
            when (msg.data?.getString(ToyEvents.MSG_GLYPH_TOY_DATA)) {
                ToyEvents.EVENT_CHANGE -> wakeAgent() // long-press on the Glyph Button
            }
        }
    })

    override fun onBind(intent: Intent?): IBinder {
        matrix = GlyphMatrix(this, forToy = true).also { it.connect() }
        main.post(tick)
        return messenger.binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        main.removeCallbacks(tick)
        matrix.disconnect()
        return false
    }

    private fun wakeAgent() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .setAction(MainActivity.ACTION_WAKE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
    }

    /** Constants from com.nothing.ketchum.GlyphToy, read reflectively with the documented defaults. */
    private object ToyEvents {
        private val cls = runCatching { Class.forName("com.nothing.ketchum.GlyphToy") }.getOrNull()
        @Suppress("UNCHECKED_CAST")
        private fun <T> field(name: String, fallback: T): T {
            val value = runCatching { cls?.getField(name)?.get(null) }.getOrNull()
            return (value as? T) ?: fallback
        }

        val MSG_GLYPH_TOY: Int = field("MSG_GLYPH_TOY", 1)
        val MSG_GLYPH_TOY_DATA: String = field("MSG_GLYPH_TOY_DATA", "data")
        val EVENT_CHANGE: String = field("EVENT_CHANGE", "change")
    }

    private companion object {
        const val FRAME_MS = 66L // ~15 fps is plenty for 625 LEDs
    }
}
