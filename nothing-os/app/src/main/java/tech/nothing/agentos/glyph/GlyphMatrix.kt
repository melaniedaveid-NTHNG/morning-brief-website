package tech.nothing.agentos.glyph

import android.content.Context
import android.util.Log
import java.lang.reflect.Proxy

/**
 * Thin bridge to Nothing's Glyph Matrix SDK (com.nothing.ketchum.*).
 *
 * Everything goes through reflection so the project builds without the SDK AAR and runs on any
 * phone; on a Phone (3) with the AAR in app/libs it lights the Matrix. Calls before the service
 * connects, or without the SDK, are silently dropped.
 *
 * [forToy] picks the API: Glyph Toys own the Matrix through setMatrixFrame, while a foreground
 * app borrows it with setAppMatrixFrame / closeAppMatrix.
 */
class GlyphMatrix(context: Context, private val forToy: Boolean) {

    private val appContext = context.applicationContext
    private var manager: Any? = null
    @Volatile private var ready = false

    val available: Boolean get() = ready

    fun connect() {
        if (manager != null) return
        try {
            val managerClass = Class.forName("$PKG.GlyphMatrixManager")
            val callbackClass = Class.forName("$PKG.GlyphMatrixManager\$Callback")
            val m = managerClass.getMethod("getInstance", Context::class.java).invoke(null, appContext)
            val callback = Proxy.newProxyInstance(callbackClass.classLoader, arrayOf(callbackClass)) { proxy, method, args ->
                when (method.name) {
                    "onServiceConnected" -> { onConnected(m); null }
                    "onServiceDisconnected" -> { ready = false; null }
                    "hashCode" -> System.identityHashCode(proxy)
                    "equals" -> proxy === args?.firstOrNull()
                    "toString" -> "GlyphMatrixCallback"
                    else -> null
                }
            }
            managerClass.getMethod("init", callbackClass).invoke(m, callback)
            manager = m
        } catch (e: ReflectiveOperationException) {
            Log.i(TAG, "Glyph Matrix SDK not present; Matrix disabled (${e.javaClass.simpleName})")
        } catch (e: RuntimeException) {
            Log.w(TAG, "Glyph Matrix init failed", e)
        }
    }

    private fun onConnected(m: Any) {
        try {
            val device = Class.forName("$PKG.Glyph").getField("DEVICE_23112").get(null) as String
            m.javaClass.getMethod("register", String::class.java).invoke(m, device)
            ready = true
        } catch (e: Exception) {
            Log.w(TAG, "Glyph Matrix register failed", e)
        }
    }

    /** Push a 25x25 row-major brightness frame. */
    fun show(frame: IntArray) {
        val m = manager ?: return
        if (!ready) return
        try {
            val name = if (forToy) "setMatrixFrame" else "setAppMatrixFrame"
            m.javaClass.getMethod(name, IntArray::class.java).invoke(m, frame)
        } catch (e: Exception) {
            Log.w(TAG, "Glyph Matrix frame failed", e)
            ready = false
        }
    }

    fun disconnect() {
        val m = manager ?: return
        ready = false
        manager = null
        for (name in listOfNotNull(if (forToy) null else "closeAppMatrix", "turnOff", "unInit")) {
            try {
                m.javaClass.getMethod(name).invoke(m)
            } catch (_: Exception) {
                // Older/newer SDKs miss some of these; nothing to clean up then.
            }
        }
    }

    companion object {
        private const val TAG = "GlyphMatrix"
        private const val PKG = "com.nothing.ketchum"

        /**
         * Top of the brightness scale the SDK accepts per LED.
         * TODO: confirm on device: the kit docs are the source of truth for this range.
         */
        const val MAX_BRIGHTNESS = 4095
    }
}
