package com.example.vezelhud

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class LayoutMode { FULL, MINIMAL }
enum class OrientationMode { LANDSCAPE, PORTRAIT, AUTO }
enum class SpeedSource { CAR, GPS }

/** All user settings. Change a value, then call save(). */
class AppSettings(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("hud_settings", Context.MODE_PRIVATE)

    var useMph by mutableStateOf(prefs.getBoolean("mph", false))
    var mirrored by mutableStateOf(prefs.getBoolean("mirrored", false))
    var layout by mutableStateOf(enumPref("layout", LayoutMode.FULL))
    var orientation by mutableStateOf(enumPref("orientation", OrientationMode.LANDSCAPE))
    var speedSource by mutableStateOf(enumPref("speedSource", SpeedSource.CAR))
    var autoBrightness by mutableStateOf(prefs.getBoolean("autoBrightness", true))
    var brightnessPct by mutableStateOf(prefs.getInt("brightnessPct", 100))
    var soundOn by mutableStateOf(prefs.getBoolean("sound", true))
    var logToFile by mutableStateOf(prefs.getBoolean("log", false))
    var showTestButtons by mutableStateOf(prefs.getBoolean("test", true))
    var autoStart by mutableStateOf(prefs.getBoolean("autoStart", false))
    var carAddress by mutableStateOf(prefs.getString("carAddress", "") ?: "")
    var carName by mutableStateOf(prefs.getString("carName", "") ?: "")

    var dctWarn by mutableStateOf(prefs.getInt("dctWarn", 100))
    var dctCrit by mutableStateOf(prefs.getInt("dctCrit", 120))
    var coolantWarn by mutableStateOf(prefs.getInt("coolantWarn", 105))
    var coolantCrit by mutableStateOf(prefs.getInt("coolantCrit", 112))
    var oilWarn by mutableStateOf(prefs.getInt("oilWarn", 130))
    var oilCrit by mutableStateOf(prefs.getInt("oilCrit", 150))
    var battWarn by mutableStateOf(prefs.getInt("battWarn", 45))
    var battCrit by mutableStateOf(prefs.getInt("battCrit", 55))

    private inline fun <reified T : Enum<T>> enumPref(key: String, default: T): T =
        runCatching { enumValueOf<T>(prefs.getString(key, default.name) ?: default.name) }
            .getOrDefault(default)

    fun save() {
        prefs.edit()
            .putBoolean("mph", useMph)
            .putBoolean("mirrored", mirrored)
            .putString("layout", layout.name)
            .putString("orientation", orientation.name)
            .putString("speedSource", speedSource.name)
            .putBoolean("autoBrightness", autoBrightness)
            .putInt("brightnessPct", brightnessPct)
            .putBoolean("sound", soundOn)
            .putBoolean("log", logToFile)
            .putBoolean("test", showTestButtons)
            .putBoolean("autoStart", autoStart)
            .putString("carAddress", carAddress)
            .putString("carName", carName)
            .putInt("dctWarn", dctWarn)
            .putInt("dctCrit", dctCrit)
            .putInt("coolantWarn", coolantWarn)
            .putInt("coolantCrit", coolantCrit)
            .putInt("oilWarn", oilWarn)
            .putInt("oilCrit", oilCrit)
            .putInt("battWarn", battWarn)
            .putInt("battCrit", battCrit)
            .apply()
        applyLimits()
    }

    fun applyLimits() {
        Limits.DCT_WARN = dctWarn
        Limits.DCT_CRIT = dctCrit
        Limits.COOLANT_WARN = coolantWarn
        Limits.COOLANT_CRIT = coolantCrit
        Limits.OIL_WARN = oilWarn
        Limits.OIL_CRIT = oilCrit
        Limits.BATT_TEMP_WARN = battWarn
        Limits.BATT_TEMP_CRIT = battCrit
    }

    init {
        applyLimits()
    }
}
