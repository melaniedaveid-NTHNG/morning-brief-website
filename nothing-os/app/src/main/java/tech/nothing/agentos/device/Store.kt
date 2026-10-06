package tech.nothing.agentos.device

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** One round with the agent. */
data class Exchange(val at: Long, val heard: String, val reply: String)

data class AgentSettings(
    val speakReplies: Boolean = true,
    val matrixReplies: Boolean = true,
)

/** Small on-device persistence: recent exchanges and agent settings. */
class Store(context: Context) {
    private val prefs = context.getSharedPreferences("agent", Context.MODE_PRIVATE)

    fun loadHistory(): List<Exchange> = runCatching {
        val arr = JSONArray(prefs.getString(KEY_HISTORY, "[]"))
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Exchange(o.getLong("at"), o.getString("heard"), o.getString("reply"))
        }
    }.getOrDefault(emptyList())

    fun saveHistory(history: List<Exchange>) {
        val arr = JSONArray()
        history.take(MAX_HISTORY).forEach {
            arr.put(JSONObject().put("at", it.at).put("heard", it.heard).put("reply", it.reply))
        }
        prefs.edit().putString(KEY_HISTORY, arr.toString()).apply()
    }

    fun loadSettings() = AgentSettings(
        speakReplies = prefs.getBoolean(KEY_SPEAK, true),
        matrixReplies = prefs.getBoolean(KEY_MATRIX, true),
    )

    fun saveSettings(s: AgentSettings) {
        prefs.edit().putBoolean(KEY_SPEAK, s.speakReplies).putBoolean(KEY_MATRIX, s.matrixReplies).apply()
    }

    companion object {
        const val MAX_HISTORY = 50
        private const val KEY_HISTORY = "history"
        private const val KEY_SPEAK = "speak_replies"
        private const val KEY_MATRIX = "matrix_replies"
    }
}
