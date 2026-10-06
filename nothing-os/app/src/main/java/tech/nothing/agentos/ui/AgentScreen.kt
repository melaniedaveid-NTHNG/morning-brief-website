package tech.nothing.agentos.ui

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import tech.nothing.agentos.AgentViewModel
import tech.nothing.agentos.Panel
import tech.nothing.agentos.agent.Mode
import kotlin.math.abs

/**
 * The agent in the middle, with three areas just off screen:
 *
 *              settings ← [ agent ] → apps
 *                             ↓
 *                          history
 *
 * Swipe left for apps, right for settings, up for history. The screen follows your finger;
 * swipe back (or press Back / Home) to return to the agent.
 */
@Composable
fun AgentScreen(
    vm: AgentViewModel,
    onRequestMic: () -> Unit,
    onRequestHome: () -> Unit,
    onOpen: (Intent) -> Unit,
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val apps by vm.apps.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val permissions by vm.permissions.collectAsStateWithLifecycle()

    BoxWithConstraints(Modifier.fillMaxSize().background(Palette.Background)) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val scope = rememberCoroutineScope()

        // Camera position: where the agent's screen sits. A panel is in view when the agent's
        // screen has moved fully out the opposite side.
        val camera = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
        fun restingOffset(p: Panel) = when (p) {
            Panel.HOME -> Offset.Zero
            Panel.APPS -> Offset(-w, 0f)
            Panel.SETTINGS -> Offset(w, 0f)
            Panel.HISTORY -> Offset(0f, -h)
        }
        val settle = spring<Offset>(dampingRatio = 0.9f, stiffness = 380f)
        LaunchedEffect(ui.panel, w, h) { camera.animateTo(restingOffset(ui.panel), settle) }

        val panel by rememberUpdatedState(ui.panel)

        // The finger's position, tracked here so fast drag events never read a stale camera.
        val drag = remember { DragPosition() }

        fun grab() {
            drag.x = camera.value.x
            drag.y = camera.value.y
        }

        fun moveBy(dx: Float, dy: Float) {
            drag.x = (drag.x + dx).coerceIn(-w, w)
            drag.y = (drag.y + dy).coerceIn(-h, 0f)
            val to = Offset(drag.x, drag.y)
            scope.launch { camera.snapTo(to) }
        }

        /** After a drag, go to whichever panel the finger left us closest to committing to. */
        fun release() {
            val o = Offset(drag.x, drag.y)
            val target = when (panel) {
                Panel.HOME -> when {
                    o.x < -w * COMMIT -> Panel.APPS
                    o.x > w * COMMIT -> Panel.SETTINGS
                    o.y < -h * COMMIT -> Panel.HISTORY
                    else -> Panel.HOME
                }
                Panel.APPS -> if (o.x > -w * (1 - COMMIT)) Panel.HOME else Panel.APPS
                Panel.SETTINGS -> if (o.x < w * (1 - COMMIT)) Panel.HOME else Panel.SETTINGS
                Panel.HISTORY -> if (o.y > -h * (1 - COMMIT)) Panel.HOME else Panel.HISTORY
            }
            vm.openPanel(target)
            scope.launch { camera.animateTo(restingOffset(target), settle) }
        }

        // --- The agent ----------------------------------------------------------------------
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { translationX = camera.value.x; translationY = camera.value.y }
                .pointerInput(vm) {
                    detectTapGestures(onTap = { vm.onTap() }, onDoubleTap = { vm.onDoubleTap() })
                }
                .pointerInput(vm) {
                    var horizontal: Boolean? = null
                    detectDragGestures(
                        onDragStart = { horizontal = null; grab() },
                        onDragEnd = { release() },
                        onDragCancel = { release() },
                    ) { change, delta ->
                        change.consume()
                        val isHorizontal = horizontal ?: (abs(delta.x) >= abs(delta.y)).also { horizontal = it }
                        if (isHorizontal) moveBy(delta.x, 0f) else moveBy(0f, delta.y)
                    }
                }
        ) {
            AgentHome(vm, ui.mode, ui.heard, ui.reply, ui.hint)
        }

        // --- Right: apps --------------------------------------------------------------------
        AppsPanel(
            apps,
            onLaunch = vm::launch,
            modifier = Modifier
                .graphicsLayer { translationX = camera.value.x + w; translationY = camera.value.y }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(onDragStart = { grab() }, onDragEnd = { release() }, onDragCancel = { release() }) { change, dx ->
                        change.consume()
                        moveBy(dx, 0f)
                    }
                },
        )

        // --- Left: settings & permissions ---------------------------------------------------
        SettingsPanel(
            permissions,
            settings,
            onRequestMic = onRequestMic,
            onRequestHome = onRequestHome,
            onSpeakReplies = vm::setSpeakReplies,
            onMatrixReplies = vm::setMatrixReplies,
            onOpen = onOpen,
            modifier = Modifier
                .graphicsLayer { translationX = camera.value.x - w; translationY = camera.value.y }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(onDragStart = { grab() }, onDragEnd = { release() }, onDragCancel = { release() }) { change, dx ->
                        change.consume()
                        moveBy(dx, 0f)
                    }
                },
        )

        // --- Bottom: history ----------------------------------------------------------------
        HistoryPanel(
            history,
            onAsk = vm::ask,
            onClear = vm::clearHistory,
            handle = Modifier.pointerInput(Unit) {
                detectVerticalDragGestures(onDragStart = { grab() }, onDragEnd = { release() }, onDragCancel = { release() }) { change, dy ->
                    change.consume()
                    moveBy(0f, dy)
                }
            },
            modifier = Modifier.graphicsLayer { translationX = camera.value.x; translationY = camera.value.y + h },
        )
    }
}

private class DragPosition {
    var x = 0f
    var y = 0f
}

/** How far a drag must travel to commit to a move, as a share of the screen. */
private const val COMMIT = 0.22f

@Composable
private fun AgentHome(vm: AgentViewModel, mode: Mode, heard: String, reply: String, hint: String?) {
    // Where the agent sits: centred when idle, lifted and small for info, leaning in while active.
    val active = mode == Mode.LISTENING || mode == Mode.THINKING || mode == Mode.SPEAKING
    val scale by animateFloatAsState(
        when { mode == Mode.INFO -> 0.34f; active -> 1.04f; else -> 1f },
        spring(dampingRatio = 0.8f, stiffness = 120f), label = "orbScale",
    )
    val lift by animateFloatAsState(
        when { mode == Mode.INFO -> -0.34f; active -> -0.06f; else -> 0f },
        spring(dampingRatio = 0.8f, stiffness = 120f), label = "orbLift",
    )

    Box(Modifier.fillMaxSize()) {
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
            mode == Mode.INFO,
            enter = fadeIn(tween(400, delayMillis = 150)) + slideInVertically(tween(400, delayMillis = 150)) { it / 12 },
            exit = fadeOut(tween(200)),
            modifier = Modifier.align(Alignment.Center).padding(horizontal = 28.dp),
        ) {
            InfoPanel(Modifier.fillMaxWidth())
        }

        Caption(mode, heard, reply, Modifier.align(Alignment.BottomCenter).padding(start = 24.dp, end = 24.dp, bottom = 64.dp))

        hint?.let {
            BasicText(it, style = Type.Body.copy(fontSize = 11.sp), modifier = Modifier.align(Alignment.TopCenter).padding(top = 40.dp))
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
