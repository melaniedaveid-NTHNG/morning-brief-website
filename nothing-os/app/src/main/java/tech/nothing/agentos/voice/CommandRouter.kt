package tech.nothing.agentos.voice

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import tech.nothing.agentos.device.Apps
import tech.nothing.agentos.device.DeviceInfo
import tech.nothing.agentos.device.LaunchableApp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** What the agent says back, and what it does to the phone. */
data class Reply(val say: String, val showInfo: Boolean = false, val action: (() -> Unit)? = null)

/**
 * First, deliberately simple brain: pattern-matched commands that drive the phone.
 * Anything it doesn't understand becomes a web search. This is the seam where an LLM goes later.
 */
class CommandRouter(private val context: Context, private val apps: () -> List<LaunchableApp>) {

    fun route(heard: String): Reply {
        val q = heard.lowercase(Locale.getDefault()).trim().trimEnd('.', '?', '!')
        if (Regex("""\btime\b""").containsMatchIn(q)) {
            return Reply("It's ${DeviceInfo.snapshot(context).time}.")
        }
        if (Regex("""\b(date|what day|today)\b""").containsMatchIn(q)) {
            val today = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault()))
            return Reply("It's $today.")
        }
        if ("battery" in q) {
            val s = DeviceInfo.snapshot(context)
            return Reply("Battery is at ${s.battery} percent${if (s.charging) " and charging" else ""}.")
        }
        if (Regex("""\b(info|status|overview|brief)\b""").containsMatchIn(q)) {
            return Reply("Here you go.", showInfo = true)
        }
        Regex("""timer (?:for )?(\d+|an?|one) (second|minute|hour)s?""").find(q)?.let { m ->
            val n = m.groupValues[1].toIntOrNull() ?: 1
            val unit = m.groupValues[2]
            val seconds = n * when (unit) { "hour" -> 3600; "minute" -> 60; else -> 1 }
            return Reply("Timer set for $n $unit${if (n == 1) "" else "s"}.") {
                launch(
                    Intent(AlarmClock.ACTION_SET_TIMER)
                        .putExtra(AlarmClock.EXTRA_LENGTH, seconds)
                        .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                )
            }
        }
        Regex("""(?:alarm|wake me)(?: up)? (?:for |at )?(\d{1,2})(?::| )?(\d{2})?\s*(am|pm|a\.m\.|p\.m\.)?""").find(q)?.let { m ->
            var hour = m.groupValues[1].toInt()
            val minute = m.groupValues[2].toIntOrNull() ?: 0
            val meridiem = m.groupValues[3]
            if (meridiem.startsWith("p") && hour < 12) hour += 12
            if (meridiem.startsWith("a") && hour == 12) hour = 0
            if (hour in 0..23 && minute in 0..59) {
                return Reply("Alarm set for %02d:%02d.".format(hour, minute)) {
                    launch(
                        Intent(AlarmClock.ACTION_SET_ALARM)
                            .putExtra(AlarmClock.EXTRA_HOUR, hour)
                            .putExtra(AlarmClock.EXTRA_MINUTES, minute)
                            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                    )
                }
            }
        }
        if (Regex("""\b(torch|flashlight|flash light)\b""").containsMatchIn(q)) {
            val on = !Regex("""\boff\b""").containsMatchIn(q)
            return if (setTorch(on)) Reply(if (on) "Torch on." else "Torch off.") else Reply("I can't reach the torch.")
        }
        if (Regex("""\b(camera|take a (photo|picture))\b""").containsMatchIn(q) && !q.startsWith("search")) {
            return Reply("Camera.") { launch(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)) }
        }
        Regex("""^(?:call|dial|ring) ([+\d ]{3,})$""").find(q)?.let { m ->
            val number = m.groupValues[1].replace(" ", "")
            return Reply("Calling $number.") { launch(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))) }
        }
        settingsFor(q)?.let { (name, action) ->
            return Reply("Opening ${if (name.isEmpty()) "" else "$name "}settings.") { launch(Intent(action)) }
        }
        Regex("""^(?:open|launch|start|go to) (.+)$""").find(q)?.let { m ->
            val wanted = m.groupValues[1]
            val app = Apps.find(apps(), wanted) ?: return Reply("I couldn't find $wanted.")
            return Reply("Opening ${app.label}.") { launch(app.intent()) }
        }
        if (Regex("""^(never ?mind|cancel|stop|nothing)$""").containsMatchIn(q)) return Reply("Okay.")
        if (Regex("""^(hi|hello|hey)\b""").containsMatchIn(q)) return Reply("Hi. What do you need?")

        return Reply("Searching for $heard.") {
            launch(Intent(Intent.ACTION_WEB_SEARCH).putExtra("query", heard))
        }
    }

    /** (what to say, settings screen) for settings-ish requests. */
    private fun settingsFor(q: String): Pair<String, String>? = when {
        "wi-fi" in q || "wifi" in q -> "Wi-Fi" to Settings.ACTION_WIFI_SETTINGS
        "bluetooth" in q -> "Bluetooth" to Settings.ACTION_BLUETOOTH_SETTINGS
        "display" in q || "brightness" in q -> "display" to Settings.ACTION_DISPLAY_SETTINGS
        "sound" in q || "ringtone" in q -> "sound" to Settings.ACTION_SOUND_SETTINGS
        q.endsWith("settings") -> "" to Settings.ACTION_SETTINGS
        else -> null
    }

    private fun launch(intent: Intent) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            // Nothing on the phone handles it. The spoken reply already went out; stay quiet.
        }
    }

    private fun setTorch(on: Boolean): Boolean {
        val cameras = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val id = cameras.cameraIdList.firstOrNull {
            cameras.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        } ?: return false
        return runCatching { cameras.setTorchMode(id, on) }.isSuccess
    }
}
