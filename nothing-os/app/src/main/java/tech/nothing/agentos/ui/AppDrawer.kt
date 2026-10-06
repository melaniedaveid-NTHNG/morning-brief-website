package tech.nothing.agentos.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tech.nothing.agentos.device.LaunchableApp

/** Swipe up: every app as a plain list. The agent is the main way around; this is the fallback. */
@Composable
fun AppDrawer(apps: List<LaunchableApp>, onLaunch: (LaunchableApp) -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier.fillMaxSize().background(Palette.Background),
        contentPadding = PaddingValues(horizontal = 28.dp, vertical = 72.dp),
    ) {
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
