package tech.nothing.agentos.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tech.nothing.agentos.AgentViewModel
import tech.nothing.agentos.agent.Mode

@Composable
fun AgentScreen(vm: AgentViewModel) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val apps by vm.apps.collectAsStateWithLifecycle()
    val swipeOpen = with(LocalDensity.current) { 96.dp.toPx() }

    // Where the agent sits: centred when idle, lifted and small for info, leaning in while active.
    val active = ui.mode == Mode.LISTENING || ui.mode == Mode.THINKING || ui.mode == Mode.SPEAKING
    val scale by animateFloatAsState(
        when { ui.mode == Mode.INFO -> 0.34f; active -> 1.04f; else -> 1f },
        spring(dampingRatio = 0.8f, stiffness = 120f), label = "orbScale",
    )
    val lift by animateFloatAsState(
        when { ui.mode == Mode.INFO -> -0.34f; active -> -0.06f; else -> 0f },
        spring(dampingRatio = 0.8f, stiffness = 120f), label = "orbLift",
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(Palette.Background)
            .pointerInput(vm) {
                detectTapGestures(onTap = { vm.onTap() }, onDoubleTap = { vm.onDoubleTap() })
            }
            .pointerInput(vm) {
                var dragged = 0f
                detectVerticalDragGestures(
                    onDragStart = { dragged = 0f },
                    onDragEnd = { if (dragged < -swipeOpen) vm.setDrawer(true) },
                ) { change, dy ->
                    dragged += dy
                    change.consume()
                }
            }
    ) {
        AgentOrb(
            vm.model,
            vm.stimulus,
            Modifier
                .fillMaxSize()
                .padding(top = 48.dp, bottom = 112.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationY = lift * size.height
                },
        )

        AnimatedVisibility(
            ui.mode == Mode.INFO,
            enter = fadeIn(tween(400, delayMillis = 150)) + slideInVertically(tween(400, delayMillis = 150)) { it / 12 },
            exit = fadeOut(tween(200)),
            modifier = Modifier.align(Alignment.Center).padding(horizontal = 28.dp),
        ) {
            InfoPanel(Modifier.fillMaxWidth())
        }

        Caption(ui.mode, ui.heard, ui.reply, Modifier.align(Alignment.BottomCenter).padding(start = 24.dp, end = 24.dp, bottom = 64.dp))

        ui.hint?.let {
            BasicText(it, style = Type.Body.copy(fontSize = 11.sp), modifier = Modifier.align(Alignment.TopCenter).padding(top = 40.dp))
        }

        AnimatedVisibility(
            ui.drawerOpen,
            enter = fadeIn() + slideInVertically { it / 8 },
            exit = fadeOut() + slideOutVertically { it / 8 },
        ) {
            AppDrawer(apps, onLaunch = vm::launch)
        }
    }
}

@Composable
private fun Caption(mode: Mode, heard: String, reply: String, modifier: Modifier) {
    val center = Type.Body.copy(textAlign = TextAlign.Center)
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        when (mode) {
            Mode.LISTENING -> Row(verticalAlignment = Alignment.CenterVertically) {
                RecordingDot()
                Spacer(Modifier.width(8.dp))
                if (heard.isEmpty()) BasicText("listening", style = center)
                else BasicText(heard, style = center.copy(color = Palette.Foreground))
            }
            Mode.THINKING -> BasicText(heard, style = center.copy(color = Palette.Foreground))
            Mode.SPEAKING -> BasicText(reply, style = center)
            Mode.IDLE, Mode.INFO -> Unit
        }
    }
}

/** Nothing's red recording dot, blinking. */
@Composable
private fun RecordingDot() {
    val blink by rememberInfiniteTransition(label = "rec").animateFloat(
        1f, 0.2f, infiniteRepeatable(tween(500), RepeatMode.Reverse), label = "recAlpha",
    )
    Box(Modifier.size(7.dp).alpha(blink).background(Palette.Red, CircleShape))
}
