package com.example.vezelhud

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

/** Collects per-trip statistics from the live data. */
class TripTracker {
    var distanceKm by mutableStateOf(0.0)
        private set
    var seconds by mutableStateOf(0.0)
        private set
    var evSeconds by mutableStateOf(0.0)
        private set
    var maxSpeed by mutableStateOf(0)
        private set
    var maxDct by mutableStateOf(0)
        private set
    var maxCoolant by mutableStateOf(0)
        private set
    private var lastMs = 0L

    fun update(d: HudData, nowMs: Long) {
        if (!d.live) {
            lastMs = 0L
            return
        }
        if (lastMs != 0L) {
            val dt = (nowMs - lastMs) / 1000.0
            if (dt in 0.0..2.0) {
                seconds += dt
                distanceKm += d.speedKmh * dt / 3600.0
                if (d.evMode) evSeconds += dt
            }
        }
        lastMs = nowMs
        if (d.speedKmh > maxSpeed) maxSpeed = d.speedKmh
        if (d.dctTempC > maxDct) maxDct = d.dctTempC
        if (d.coolantC > maxCoolant) maxCoolant = d.coolantC
    }

    fun reset() {
        distanceKm = 0.0
        seconds = 0.0
        evSeconds = 0.0
        maxSpeed = 0
        maxDct = 0
        maxCoolant = 0
        lastMs = 0L
    }
}

private fun hms(sec: Double): String {
    val s = sec.toLong()
    return String.format(Locale.US, "%d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)
}

private fun f1(v: Double): String = String.format(Locale.US, "%.1f", v)

@Composable
fun TripScreen(trip: TripTracker, useMph: Boolean, onBack: () -> Unit) {
    val k = if (useMph) 0.621371 else 1.0
    val distUnit = if (useMph) "mi" else "km"
    val speedUnit = if (useMph) "mph" else "km/h"
    val hours = trip.seconds / 3600.0
    val avg = if (hours > 0.001) trip.distanceKm * k / hours else 0.0
    val evShare = if (trip.seconds > 1) trip.evSeconds / trip.seconds * 100 else 0.0

    val stats = listOf(
        "DISTANCE" to "${f1(trip.distanceKm * k)} $distUnit",
        "TIME" to hms(trip.seconds),
        "AVERAGE SPEED" to "${f1(avg)} $speedUnit",
        "TOP SPEED" to "${(trip.maxSpeed * k).toInt()} $speedUnit",
        "TIME IN EV MODE" to "${f1(evShare)} %",
        "MAX DCT TEMP" to "${trip.maxDct}\u00B0C",
        "MAX COOLANT" to "${trip.maxCoolant}\u00B0C",
    )

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Chip("< BACK", false, onBack)
            Spacer(Modifier.width(16.dp))
            Text("TRIP", color = Color(0xFFEAF2F0), fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
        }
        Spacer(Modifier.padding(8.dp))
        stats.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                pair.forEach { (label, value) ->
                    Column(Modifier.weight(1f)) {
                        Text(label, color = Color(0xFF7F9490), fontSize = 12.sp, letterSpacing = 2.sp)
                        Text(value, color = Color(0xFFEAF2F0), fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        Spacer(Modifier.padding(8.dp))
        Chip("RESET TRIP", false) { trip.reset() }
    }
}
