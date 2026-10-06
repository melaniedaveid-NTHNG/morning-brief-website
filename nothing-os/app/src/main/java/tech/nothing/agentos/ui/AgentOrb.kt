package tech.nothing.agentos.ui

import android.graphics.ColorMatrixColorFilter
import android.graphics.Shader
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import tech.nothing.agentos.agent.AgentModel
import tech.nothing.agentos.agent.AgentModel.Kind
import tech.nothing.agentos.agent.Stimulus
import kotlin.math.min

/** Model units the orb's canvas must fit: width x height. */
private const val SPAN_W = 10f
private const val SPAN_H = 14f

/**
 * The agent. Layers, back to front:
 *  1. shell bubbles, filled white and merged ("goo")
 *  2. the same bubbles inset by a hairline in background colour -> merged outlines
 *  3. a soft glow of the core
 *  4. the core, merged
 *  5. crisp details: ring holes, dots, pills, orbit lines
 *
 * "Goo" = blur then a steep alpha threshold, so nearby shapes melt into one blob.
 */
@Composable
fun AgentOrb(
    model: AgentModel,
    stimulus: State<Stimulus>,
    modifier: Modifier = Modifier,
) {
    val time = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) withFrameNanos { time.floatValue = (it - start) / 1e9f }
    }

    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val unit = with(density) { min(maxWidth.toPx() / SPAN_W, maxHeight.toPx() / SPAN_H) }
        val hairline = with(density) { 1.1.dp.toPx() }
        val goo = remember(unit) { gooEffect(unit * 0.12f) }
        val glowBlur = remember(unit) { blurEffect(unit * 0.55f) }

        fun Modifier.gooLayer() = graphicsLayer {
            renderEffect = goo
            compositingStrategy = CompositingStrategy.Offscreen
        }

        Box(Modifier.fillMaxSize()) {
            Canvas(Modifier.fillMaxSize().gooLayer()) {
                drawShell(model, time.floatValue, stimulus.value, unit, inset = 0f, color = Color.White)
            }
            Canvas(Modifier.fillMaxSize().gooLayer()) {
                drawShell(model, time.floatValue, stimulus.value, unit, inset = hairline, color = Palette.Background)
            }
            Canvas(
                Modifier.fillMaxSize().graphicsLayer {
                    renderEffect = glowBlur
                    alpha = model.glow(stimulus.value)
                }
            ) {
                drawCore(model, time.floatValue, stimulus.value, unit, grow = 1.25f)
            }
            Canvas(Modifier.fillMaxSize().gooLayer()) {
                drawCore(model, time.floatValue, stimulus.value, unit)
            }
            Canvas(Modifier.fillMaxSize()) {
                drawDetails(model, time.floatValue, stimulus.value, unit, hairline)
            }
        }
    }
}

private fun DrawScope.at(x: Float, y: Float, unit: Float) =
    Offset(center.x + x * unit, center.y + y * unit)

private fun DrawScope.drawShell(
    model: AgentModel, t: Float, s: Stimulus, unit: Float, inset: Float, color: Color,
) {
    for (b in model.bubbles) {
        val r = model.bubbleR(b, t) * unit - inset
        drawCircle(color, r, at(model.bubbleX(b, t, s), model.bubbleY(b, t, s), unit))
    }
    val spread = model.spread(s)
    for (p in model.pills) {
        val w = 0.62f * unit - 2 * inset
        val len = p.length * unit - 2 * inset
        val box = if (p.vertical) Size(w, len) else Size(len, w)
        val c = at(p.x * spread, p.y * spread, unit)
        drawRoundRect(color, Offset(c.x - box.width / 2, c.y - box.height / 2), box, CornerRadius(w / 2))
    }
}

private fun DrawScope.drawCore(model: AgentModel, t: Float, s: Stimulus, unit: Float, grow: Float = 1f) {
    for (l in model.links) {
        val scale = (model.cellScale(l.a, t, s) + model.cellScale(l.b, t, s)) / 2
        drawLine(
            Color.White,
            at(model.cellX(l.a, t, s), model.cellY(l.a, t, s), unit),
            at(model.cellX(l.b, t, s), model.cellY(l.b, t, s), unit),
            strokeWidth = l.width * scale * grow * unit,
            cap = StrokeCap.Round,
        )
    }
    for (i in model.cells.indices) {
        val r = model.cells[i].r * model.cellScale(i, t, s) * grow * unit
        drawCircle(Color.White, r, at(model.cellX(i, t, s), model.cellY(i, t, s), unit))
    }
}

private fun DrawScope.drawDetails(model: AgentModel, t: Float, s: Stimulus, unit: Float, hairline: Float) {
    // Ring cells: dark hole with a bright pip. Some plain cells carry a dark pip.
    for (i in model.cells.indices) {
        val c = model.cells[i]
        val r = c.r * model.cellScale(i, t, s) * unit
        val pos = at(model.cellX(i, t, s), model.cellY(i, t, s), unit)
        when {
            c.kind == Kind.RING -> {
                drawCircle(Palette.Background, r * 0.56f, pos)
                drawCircle(Color.White, r * 0.2f, pos)
            }
            c.pip -> drawCircle(Palette.Background, r * 0.17f, pos)
        }
    }
    // Shell bubbles: a centre dot, or a dim filled disc.
    for (b in model.bubbles) {
        val pos = at(model.bubbleX(b, t, s), model.bubbleY(b, t, s), unit)
        if (b.grey) drawCircle(Color.White.copy(alpha = 0.22f), 0.3f * unit, pos)
        else drawCircle(Color.White, 0.06f * unit, pos)
    }
    // Pills: bright ones are solid with a dark slot, the rest get a slot outline.
    val spread = model.spread(s)
    for (p in model.pills) {
        val c = at(p.x * spread, p.y * spread, unit)
        val slotW = 0.2f * unit
        val slotL = (p.length - 0.45f) * unit
        val slot = if (p.vertical) Size(slotW, slotL) else Size(slotL, slotW)
        val topLeft = Offset(c.x - slot.width / 2, c.y - slot.height / 2)
        if (p.bright) {
            val w = 0.62f * unit
            val outer = if (p.vertical) Size(w, p.length * unit) else Size(p.length * unit, w)
            drawRoundRect(Color.White, Offset(c.x - outer.width / 2, c.y - outer.height / 2), outer, CornerRadius(w / 2))
            drawRoundRect(Palette.Background, topLeft, slot, CornerRadius(slotW / 2))
        } else {
            drawRoundRect(Color.White, topLeft, slot, CornerRadius(slotW / 2), style = Stroke(hairline))
        }
    }
    // Two faint orbits around the core.
    val orbit = Color.White.copy(alpha = 0.28f)
    val o1 = Size(6.6f * unit * spread, 10.2f * unit * spread)
    drawOval(orbit, Offset(center.x - o1.width / 2, center.y - o1.height / 2), o1, style = Stroke(hairline))
    val o2 = Size(8.0f * unit * spread, 8.6f * unit * spread)
    drawArc(
        orbit, startAngle = 200f + t * 2f, sweepAngle = 140f, useCenter = false,
        topLeft = Offset(center.x - o2.width / 2, center.y - o2.height / 2 - 1.2f * unit),
        size = o2, style = Stroke(hairline),
    )
}

private fun blurEffect(radius: Float): RenderEffect =
    android.graphics.RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.DECAL).asComposeRenderEffect()

/** Blur, then push alpha through a steep curve centred on 0.5: soft blobs become crisp merged shapes. */
private fun gooEffect(radius: Float): RenderEffect {
    val threshold = android.graphics.ColorMatrix(
        floatArrayOf(
            1f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, 24f, -24f * 127f,
        )
    )
    val blur = android.graphics.RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.DECAL)
    return android.graphics.RenderEffect
        .createColorFilterEffect(ColorMatrixColorFilter(threshold), blur)
        .asComposeRenderEffect()
}
