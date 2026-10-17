package com.example.vezelhud

import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.math.sin

/** Drive mode drives the speed color and the badge: EV = motor, ENGINE = fuel, SPORT = sport. */
enum class DriveMode(val label: String, val color: Color) {
    EV("EV", Color(0xFF2BE38A)),
    ENGINE("FUEL", Color(0xFF2F8CFF)),
    SPORT("SPORT", Color(0xFFFF3B30)),
}

/** One snapshot of everything the HUD shows. Later, the real OBD2 layer produces these too. */
data class HudData(
    val rpm: Int = 0,
    val speedKmh: Int = 0,
    val gear: Int = 1,
    val batteryPct: Int = 0,
    val powerKw: Int = 0,      // positive = assist, negative = regen
    val dctTempC: Int = 40,
    val coolantC: Int = 80,
    val oilC: Int = 80,
    val battTempC: Int = 30,   // hybrid (traction) battery temperature
    val accelPct: Int = 0,     // accelerator pedal position 0-100
    val brakePct: Int = 0,     // brake pressure 0-100
    val evMode: Boolean = false,
    val sport: Boolean = false,
) {
    val mode: DriveMode
        get() = when {
            sport -> DriveMode.SPORT
            evMode -> DriveMode.EV
            else -> DriveMode.ENGINE
        }
}

/** Tune these to your car. */
object Limits {
    const val RPM_MAX = 7000
    const val DCT_WARN = 100
    const val DCT_CRIT = 120
    const val COOLANT_WARN = 105
    const val COOLANT_CRIT = 112
    const val OIL_WARN = 130
    const val OIL_CRIT = 150
    const val BATT_TEMP_WARN = 45
    const val BATT_TEMP_CRIT = 55
}

/** Fake data source so you can build and test the UI without the car. */
class Simulator {
    @Volatile
    var forceOverheat = false

    @Volatile
    var forceSport = false

    fun stream(): Flow<HudData> = flow {
        var t = 0.0
        var dct = 80.0
        while (true) {
            t += 0.1
            val sport = forceSport
            val speed = (55 + 45 * sin(t / 5)).toInt()
            val ev = !sport && speed < 25
            val gear = (speed / 14 + 1).coerceIn(1, 7)
            val sportBoost = if (sport) 1800 + 1400 * (0.5 + 0.5 * sin(t * 0.8)) else 0.0
            val rpm = if (ev) 0 else
                (800 + speed * 22 + sportBoost + 250 * sin(t * 2)).toInt().coerceIn(700, Limits.RPM_MAX)
            val power = (25 * sin(t / 3)).toInt()
            val pedal = sin(t / 2)
            val accel = (maxOf(0.0, pedal) * 100).toInt()
            val brake = (maxOf(0.0, -pedal) * 80).toInt()
            val target = if (forceOverheat) 135.0 else 88.0
            dct += (target - dct) * 0.02
            emit(
                HudData(
                    rpm = rpm,
                    speedKmh = speed,
                    gear = gear,
                    batteryPct = (60 + 30 * sin(t / 20)).toInt(),
                    powerKw = power,
                    dctTempC = dct.toInt(),
                    coolantC = (88 + 2 * sin(t / 10)).toInt(),
                    oilC = (98 + 3 * sin(t / 15)).toInt(),
                    battTempC = (34 + 4 * sin(t / 25)).toInt(),
                    accelPct = accel,
                    brakePct = brake,
                    evMode = ev,
                    sport = sport,
                )
            )
            delay(100)
        }
    }
}
