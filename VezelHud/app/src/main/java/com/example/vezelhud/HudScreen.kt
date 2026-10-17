package com.example.vezelhud

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private val Bg = Color.Black
private val Fg = Color(0xFFEAF2F0)
private val Dim = Color(0xFF7F9490)
private val Track = Color(0xFF151A1A)
private val Green = Color(0xFF2BE38A)
private val Blue = Color(0xFF2F8CFF)
private val Orange = Color(0xFFFF9F1C)
private val Alert = Color(0xFFFF3B30)
private val Regen = Color(0xFF35D0E8)

private const val START = 150f
private const val SWEEP = 240f

private data class Zone(val from: Float, val to: Float, val color: Color)

// RPM zones: 0-2 green, 2-5 blue, 5-6 orange, 6-7 red
private val Zones = listOf(
    Zone(0f, 2000f, Green),
    Zone(2000f, 5000f, Blue),
    Zone(5000f, 6000f, Orange),
    Zone(6000f, 7000f, Alert),
)

private fun zoneFor(v: Float) = Zones.last { v >= it.from }

private fun tempColor(v: Int, warn: Int, crit: Int) = when {
    v >= crit -> Alert
    v >= warn -> Orange
    else -> Fg
}

private fun activeAlert(d: HudData): String? = when {
    d.dctTempC >= Limits.DCT_CRIT -> "DCT TEMP HIGH"
    d.coolantC >= Limits.COOLANT_CRIT -> "COOLANT HIGH"
    d.oilC >= Limits.OIL_CRIT -> "OIL TEMP HIGH"
    d.battTempC >= Limits.BATT_TEMP_CRIT -> "HV BATTERY HOT"
    else -> null
}

@Composable
fun HudScreen(d: HudData, mirrored: Boolean, modifier: Modifier = Modifier) {
    val alert = activeAlert(d)
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(450, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulseAlpha",
    )
    Box(
        modifier
            .fillMaxSize()
            .graphicsLayer { scaleX = if (mirrored) -1f else 1f }
            .background(Bg)
            .then(if (alert != null) Modifier.border(4.dp, Alert.copy(alpha = pulse)) else Modifier)
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                RpmGauge(d.rpm, pulse, Modifier.fillMaxWidth())
                PedalBars(d.accelPct, d.brakePct, Modifier.fillMaxWidth().padding(top = 6.dp))
            }
            CenterColumn(d, Modifier.weight(0.9f))
            RightColumn(d, Modifier.weight(1f).padding(start = 20.dp))
        }
        if (alert != null) {
            Text(
                alert,
                color = Color.Black,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .background(Alert, RoundedCornerShape(6.dp))
                    .padding(horizontal = 14.dp, vertical = 3.dp),
            )
        }
    }
}

@Composable
private fun RpmGauge(rpm: Int, pulse: Float, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val maxV = Limits.RPM_MAX.toFloat()
    // Smooth, critically damped follow so the needle glides between samples
    val value by animateFloatAsState(
        targetValue = rpm.toFloat().coerceIn(0f, maxV),
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 260f),
        label = "rpm",
    )
    val zoneColor by animateColorAsState(zoneFor(value).color, tween(250), label = "zoneColor")

    Box(modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = size.minDimension * 0.07f
            val side = size.minDimension - stroke * 2.2f
            val arcSize = Size(side, side)
            val c = center
            val topLeft = Offset(c.x - side / 2, c.y - side / 2)
            val radius = side / 2
            fun angleOf(v: Float) = START + SWEEP * v / maxV
            fun pt(r: Float, deg: Float): Offset {
                val a = Math.toRadians(deg.toDouble())
                return Offset(c.x + r * cos(a).toFloat(), c.y + r * sin(a).toFloat())
            }

            // zone tracks (dim) + lit part up to the current value
            for (z in Zones) {
                val span = SWEEP * (z.to - z.from) / maxV
                drawArc(z.color.copy(alpha = 0.2f), angleOf(z.from), span, false, topLeft, arcSize, style = Stroke(stroke))
                val lit = minOf(value, z.to)
                if (lit > z.from) {
                    drawArc(z.color, angleOf(z.from), SWEEP * (lit - z.from) / maxV, false, topLeft, arcSize, style = Stroke(stroke))
                }
            }

            // glowing tip
            val tip = pt(radius, angleOf(value))
            val glow = if (value >= 6000f) 0.25f + 0.35f * pulse else 0.25f
            drawCircle(zoneColor.copy(alpha = glow), stroke * 1.7f, tip)
            drawCircle(Color.White, stroke * 0.36f, tip)

            // ticks every 500 rpm, numbers every 1000
            val outer = radius - stroke / 2 - 5.dp.toPx()
            for (i in 0..14) {
                val major = i % 2 == 0
                val len = if (major) 10.dp.toPx() else 5.dp.toPx()
                val deg = angleOf(i * 500f)
                drawLine(
                    if (major) Fg else Dim,
                    pt(outer, deg), pt(outer - len, deg),
                    strokeWidth = if (major) 2.dp.toPx() else 1.dp.toPx(),
                )
            }
            val style = TextStyle(color = Dim, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            val rNum = outer - 10.dp.toPx() - 12.dp.toPx()
            for (v in 0..7) {
                val label = v.toString()
                val sz = measurer.measure(label, style).size
                val p = pt(rNum, angleOf(v * 1000f))
                drawText(measurer, label, topLeft = Offset(p.x - sz.width / 2f, p.y - sz.height / 2f), style = style)
            }
            drawCircle(Track, rNum - 14.dp.toPx(), style = Stroke(1.dp.toPx()))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                ((value / 10).roundToInt() * 10).toString(),
                color = zoneColor, fontSize = 38.sp, fontWeight = FontWeight.Bold,
            )
            Text("RPM", color = Dim, fontSize = 11.sp, letterSpacing = 3.sp)
        }
    }
}

@Composable
private fun CenterColumn(d: HudData, modifier: Modifier = Modifier) {
    val modeColor by animateColorAsState(d.mode.color, tween(500), label = "modeColor")
    val speed by animateFloatAsState(d.speedKmh.toFloat(), spring(stiffness = 300f), label = "speed")
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Crossfade(d.mode, animationSpec = tween(300), label = "badge") { m ->
            Text(
                m.label, color = m.color, fontSize = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp,
                modifier = Modifier
                    .border(2.dp, m.color, RoundedCornerShape(50))
                    .padding(horizontal = 16.dp, vertical = 1.dp),
            )
        }
        Text(speed.roundToInt().toString(), color = modeColor, fontSize = 110.sp, fontWeight = FontWeight.Bold)
        Text("KM/H", color = Dim, fontSize = 13.sp, letterSpacing = 3.sp)
        Row(
            Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            for (g in 1..7) GearPip(g, g == d.gear, modeColor)
        }
    }
}

@Composable
private fun GearPip(g: Int, on: Boolean, accent: Color) {
    val size by animateFloatAsState(if (on) 34f else 18f, tween(200), label = "gearSize")
    val color by animateColorAsState(if (on) accent else Color(0xFF6F8581), tween(200), label = "gearColor")
    Text(g.toString(), color = color, fontSize = size.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun RightColumn(d: HudData, modifier: Modifier = Modifier) {
    val batColor = when {
        d.batteryPct <= 15 -> Alert
        d.batteryPct <= 30 -> Orange
        else -> Green
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column {
            Label("HYBRID BATTERY")
            Text("${d.batteryPct}%", color = Fg, fontSize = 34.sp, fontWeight = FontWeight.Bold)
            FillBar(d.batteryPct / 100f, batColor)
        }
        Column {
            Label("REGEN / ASSIST")
            val txt = if (d.powerKw >= 0) "${d.powerKw} kW assist" else "${-d.powerKw} kW regen"
            Text(txt, color = Fg, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            PowerBar(d.powerKw)
        }
        Column {
            Label("DCT TEMP")
            Text(
                "${d.dctTempC}\u00B0C",
                color = tempColor(d.dctTempC, Limits.DCT_WARN, Limits.DCT_CRIT),
                fontSize = 34.sp, fontWeight = FontWeight.Bold,
            )
            DctBar(d.dctTempC)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TempItem("COOLANT", d.coolantC, tempColor(d.coolantC, Limits.COOLANT_WARN, Limits.COOLANT_CRIT))
            TempItem("OIL", d.oilC, tempColor(d.oilC, Limits.OIL_WARN, Limits.OIL_CRIT))
            TempItem("HV BATT", d.battTempC, tempColor(d.battTempC, Limits.BATT_TEMP_WARN, Limits.BATT_TEMP_CRIT))
        }
    }
}

@Composable
private fun TempItem(label: String, value: Int, color: Color) {
    Column {
        Label(label)
        Text("$value\u00B0", color = color, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Label(text: String) =
    Text(text, color = Dim, fontSize = 11.sp, letterSpacing = 2.sp)

@Composable
private fun FillBar(frac: Float, color: Color) {
    val f by animateFloatAsState(frac.coerceIn(0f, 1f), tween(300), label = "fill")
    val c by animateColorAsState(color, tween(300), label = "fillColor")
    Canvas(Modifier.fillMaxWidth().height(8.dp)) {
        drawRect(Track, size = size)
        drawRect(c, size = Size(size.width * f, size.height))
    }
}

@Composable
private fun PowerBar(kw: Int) {
    val f by animateFloatAsState((kw / 40f).coerceIn(-1f, 1f), tween(200), label = "power")
    Canvas(Modifier.fillMaxWidth().height(8.dp)) {
        drawRect(Track, size = size)
        val mid = size.width / 2
        if (f >= 0) drawRect(Green, Offset(mid, 0f), Size(mid * f, size.height))
        else drawRect(Regen, Offset(mid + mid * f, 0f), Size(-mid * f, size.height))
        drawRect(Fg, Offset(mid - 1.dp.toPx(), -3.dp.toPx()), Size(2.dp.toPx(), size.height + 6.dp.toPx()))
    }
}

/** Scale 40..140 C: green to 100, amber to 120, red above. */
@Composable
private fun DctBar(temp: Int) {
    val t by animateFloatAsState(temp.toFloat(), tween(300), label = "dct")
    Canvas(Modifier.fillMaxWidth().height(8.dp)) {
        val w = size.width
        val h = size.height
        drawRect(Color(0xFF1F8A68), Offset(0f, 0f), Size(w * 0.6f, h))
        drawRect(Color(0xFFB9801A), Offset(w * 0.6f, 0f), Size(w * 0.2f, h))
        drawRect(Color(0xFFA8261F), Offset(w * 0.8f, 0f), Size(w * 0.2f, h))
        val x = w * ((t - 40) / 100f).coerceIn(0f, 1f)
        drawRect(Fg, Offset(x - 2.dp.toPx(), -4.dp.toPx()), Size(4.dp.toPx(), h + 8.dp.toPx()))
    }
}

@Composable
private fun PedalBars(accel: Int, brake: Int, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        PedalRow("ACCEL", accel, Green)
        PedalRow("BRAKE", brake, Alert)
    }
}

@Composable
private fun PedalRow(label: String, pct: Int, color: Color) {
    val f by animateFloatAsState(
        (pct / 100f).coerceIn(0f, 1f),
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 500f),
        label = label,
    )
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Dim, fontSize = 11.sp, letterSpacing = 2.sp, modifier = Modifier.width(48.dp))
        Canvas(Modifier.weight(1f).height(10.dp)) {
            val r = CornerRadius(size.height / 2)
            drawRoundRect(Track, cornerRadius = r)
            if (f > 0.01f) {
                val w = size.width * f
                drawRoundRect(
                    Brush.horizontalGradient(listOf(color.copy(alpha = 0.5f), color), startX = 0f, endX = w),
                    size = Size(w, size.height),
                    cornerRadius = r,
                )
            }
        }
        Text(
            "$pct%", color = Fg, fontSize = 13.sp, fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End, modifier = Modifier.width(40.dp),
        )
    }
}
