package tech.nothing.agentos.device

import android.content.Context
import android.content.Intent

data class LaunchableApp(val label: String, val packageName: String, val activity: String) {
    fun intent(): Intent = Intent(Intent.ACTION_MAIN)
        .addCategory(Intent.CATEGORY_LAUNCHER)
        .setClassName(packageName, activity)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
}

object Apps {
    /** Everything with a launcher icon, except us, sorted by name. */
    fun load(context: Context): List<LaunchableApp> {
        val pm = context.packageManager
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(main, 0)
            .filter { it.activityInfo.packageName != context.packageName }
            .map { LaunchableApp(it.loadLabel(pm).toString(), it.activityInfo.packageName, it.activityInfo.name) }
            .sortedBy { it.label.lowercase() }
    }

    /** Best label match for something spoken, e.g. "spotify" or "the camera". */
    fun find(apps: List<LaunchableApp>, spoken: String): LaunchableApp? {
        val q = spoken.lowercase().removePrefix("the ").removePrefix("my ").trim()
        if (q.isEmpty()) return null
        return apps.firstOrNull { it.label.lowercase() == q }
            ?: apps.firstOrNull { it.label.lowercase().startsWith(q) }
            ?: apps.firstOrNull { q in it.label.lowercase() }
            ?: apps.firstOrNull { it.label.lowercase() in q }
    }
}
