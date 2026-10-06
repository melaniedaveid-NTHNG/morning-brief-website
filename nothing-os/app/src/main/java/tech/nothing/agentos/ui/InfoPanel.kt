package tech.nothing.agentos.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import tech.nothing.agentos.device.DeviceInfo

/** Single tap: the essentials, nothing more. */
@Composable
fun InfoPanel(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var info by remember { mutableStateOf(DeviceInfo.snapshot(context)) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            info = DeviceInfo.snapshot(context)
        }
    }
    Column(modifier) {
        BasicText(info.time, style = Type.Big)
        Spacer(Modifier.height(8.dp))
        BasicText(info.date, style = Type.Body)
        Spacer(Modifier.height(28.dp))
        InfoRow("battery", "${info.battery}%" + if (info.charging) " · charging" else "")
        InfoRow("next alarm", info.nextAlarm ?: "none")
        InfoRow("agent", "double tap to talk")
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .drawBehind { drawLine(Palette.Rule, Offset.Zero, Offset(size.width, 0f), 1.dp.toPx()) }
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        BasicText(label, style = Type.Body)
        BasicText(value, style = Type.Body.copy(color = Palette.Foreground))
    }
}
