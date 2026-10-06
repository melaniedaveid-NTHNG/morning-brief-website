package tech.nothing.agentos.ui

import android.content.ComponentName
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tech.nothing.agentos.Permissions
import tech.nothing.agentos.device.AgentSettings
import tech.nothing.agentos.device.Exchange
import tech.nothing.agentos.device.LaunchableApp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val PanelPadding = PaddingValues(start = 28.dp, end = 28.dp, top = 64.dp, bottom = 48.dp)

// --- Right: apps -----------------------------------------------------------------------------

/** Every app as a plain list. The agent is the main way around; this is the fallback. */
@Composable
fun AppsPanel(apps: List<LaunchableApp>, onLaunch: (LaunchableApp) -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(modifier.fillMaxSize().background(Palette.Background), contentPadding = PanelPadding) {
        item { PanelTitle("apps", "${apps.size}") }
        items(apps, key = { it.packageName + "/" + it.activity }) { app ->
            BasicText(
                app.label.lowercase(),
                style = Type.Body.copy(fontSize = 20.sp, color = Palette.Foreground),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onLaunch(app) }
                    .padding(vertical = 12.dp),
            )
        }
    }
}

// --- Left: settings & permissions ------------------------------------------------------------

@Composable
fun SettingsPanel(
    permissions: Permissions,
    settings: AgentSettings,
    onRequestMic: () -> Unit,
    onRequestHome: () -> Unit,
    onSpeakReplies: (Boolean) -> Unit,
    onMatrixReplies: (Boolean) -> Unit,
    onOpen: (Intent) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier.fillMaxSize().background(Palette.Background), contentPadding = PanelPadding) {
        item { PanelTitle("settings") }

        section("permissions")
        item {
            Column {
                StatusRow("microphone", permissions.microphone, okText = "allowed", fixText = "allow", onFix = onRequestMic)
                StatusRow("home app", permissions.defaultHome, okText = "agent", fixText = "make default", onFix = onRequestHome)
                // Nothing to grant here: it's connected or the phone has no Matrix service.
                SettingRow(
                    "glyph matrix",
                    if (permissions.glyphMatrix) "connected" else "not connected",
                    if (permissions.glyphMatrix) Palette.Dim else Palette.Red,
                ) {}
            }
        }

        section("agent")
        item {
            Column {
                ToggleRow("speak replies", settings.speakReplies, onSpeakReplies)
                ToggleRow("replies on matrix", settings.matrixReplies, onMatrixReplies)
                LinkRow("glyph toys") { onOpen(Intent().setComponent(GLYPH_TOYS_MANAGER)) }
                LinkRow("voice input") { onOpen(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS)) }
            }
        }

        section("phone")
        item {
            Column {
                LinkRow("wi-fi") { onOpen(Intent(Settings.ACTION_WIFI_SETTINGS)) }
                LinkRow("bluetooth") { onOpen(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
                LinkRow("display") { onOpen(Intent(Settings.ACTION_DISPLAY_SETTINGS)) }
                LinkRow("sound") { onOpen(Intent(Settings.ACTION_SOUND_SETTINGS)) }
                LinkRow("all settings") { onOpen(Intent(Settings.ACTION_SETTINGS)) }
            }
        }
    }
}

/** Nothing's "Manage Glyph Toys" screen, where a toy is added to the Glyph Button carousel. */
private val GLYPH_TOYS_MANAGER =
    ComponentName("com.nothing.thirdparty", "com.nothing.thirdparty.matrix.toys.manager.ToysManagerActivity")

// --- Bottom: history -------------------------------------------------------------------------

/**
 * Recent questions and answers, newest first. Tap one to ask it again.
 * [handle] is the drag area that swipes the panel closed (the list itself scrolls).
 */
@Composable
fun HistoryPanel(
    history: List<Exchange>,
    onAsk: (String) -> Unit,
    onClear: () -> Unit,
    handle: Modifier,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().background(Palette.Background)) {
        Column(handle.fillMaxWidth().padding(start = 28.dp, end = 28.dp, top = 20.dp)) {
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(width = 36.dp, height = 4.dp)
                    .background(Palette.Rule, RoundedCornerShape(2.dp))
            )
            Spacer(Modifier.height(28.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                PanelTitle("history", if (history.isEmpty()) null else "${history.size}")
                if (history.isNotEmpty()) {
                    BasicText("clear", style = Type.Body, modifier = Modifier.clickable(onClick = onClear).padding(4.dp))
                }
            }
        }
        if (history.isEmpty()) {
            BasicText(
                "nothing yet. double tap the agent and ask something.",
                style = Type.Body,
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 16.dp),
            )
        }
        LazyColumn(contentPadding = PaddingValues(start = 28.dp, end = 28.dp, bottom = 48.dp)) {
            items(history, key = { it.at }) { e ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .ruleAbove()
                        .clickable { onAsk(e.heard) }
                        .padding(vertical = 14.dp)
                ) {
                    BasicText(whenLabel(e.at), style = Type.Body.copy(fontSize = 11.sp))
                    Spacer(Modifier.height(6.dp))
                    BasicText(e.heard, style = Type.Body.copy(fontSize = 16.sp, color = Palette.Foreground))
                    Spacer(Modifier.height(4.dp))
                    BasicText(e.reply, style = Type.Body)
                }
            }
        }
    }
}

private val timeOnly = DateTimeFormatter.ofPattern("HH:mm")
private val dayAndTime = DateTimeFormatter.ofPattern("EEE dd MMM · HH:mm")

private fun whenLabel(at: Long): String {
    val t = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault())
    return if (t.toLocalDate() == LocalDate.now()) "today · ${t.format(timeOnly)}" else t.format(dayAndTime).lowercase()
}

// --- Shared pieces ---------------------------------------------------------------------------

@Composable
private fun PanelTitle(title: String, count: String? = null) {
    Row(Modifier.padding(bottom = 20.dp), verticalAlignment = Alignment.Bottom) {
        BasicText(title, style = Type.Body.copy(fontSize = 28.sp, color = Palette.Foreground))
        if (count != null) {
            Spacer(Modifier.width(10.dp))
            BasicText(count, style = Type.Body)
        }
    }
}

private fun LazyListScope.section(label: String) = item {
    BasicText(
        label.uppercase(),
        style = Type.Body.copy(fontSize = 11.sp, letterSpacing = 1.5.sp),
        modifier = Modifier.padding(top = 24.dp, bottom = 6.dp),
    )
}

private fun Modifier.ruleAbove() = drawBehind {
    drawLine(Palette.Rule, Offset.Zero, Offset(size.width, 0f), 1.dp.toPx())
}

@Composable
private fun SettingRow(label: String, value: String, valueColor: Color, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .ruleAbove()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(label, style = Type.Body.copy(fontSize = 16.sp, color = Palette.Foreground))
        BasicText(value, style = Type.Body.copy(color = valueColor))
    }
}

/** A permission: fine (dim), or needs you (red, tappable). */
@Composable
private fun StatusRow(label: String, ok: Boolean, okText: String, fixText: String, onFix: () -> Unit) =
    SettingRow(label, if (ok) okText else "$fixText →", if (ok) Palette.Dim else Palette.Red) { if (!ok) onFix() }

@Composable
private fun ToggleRow(label: String, on: Boolean, onChange: (Boolean) -> Unit) =
    SettingRow(label, if (on) "on" else "off", if (on) Palette.Foreground else Palette.Dim) { onChange(!on) }

@Composable
private fun LinkRow(label: String, onClick: () -> Unit) = SettingRow(label, "→", Palette.Dim, onClick)
