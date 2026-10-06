package tech.nothing.agentos.device

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class DeviceSnapshot(
    val time: String,
    val date: String,
    val battery: Int,
    val charging: Boolean,
    val nextAlarm: String?,
)

object DeviceInfo {
    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")
    private val dateFormat = DateTimeFormatter.ofPattern("EEE dd MMM", Locale.getDefault())

    fun snapshot(context: Context): DeviceSnapshot {
        val now = LocalDateTime.now()
        val battery = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val status = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val alarms = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val alarm = alarms.nextAlarmClock?.triggerTime?.let {
            LocalDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneId.systemDefault()).format(timeFormat)
        }
        return DeviceSnapshot(
            time = now.format(timeFormat),
            date = now.format(dateFormat).lowercase(),
            battery = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY),
            charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL,
            nextAlarm = alarm,
        )
    }
}
