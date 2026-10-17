package com.example.vezelhud

import android.content.Context
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Writes one CSV file per session. Handy for finding PIDs and checking temperature trends. */
class CsvLogger(context: Context) {
    private val dir = File(context.getExternalFilesDir(null) ?: context.filesDir, "logs")
    private var writer: BufferedWriter? = null
    private var rows = 0

    val dirPath: String
        get() = dir.absolutePath

    fun start() {
        if (writer != null) return
        runCatching {
            dir.mkdirs()
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            writer = BufferedWriter(FileWriter(File(dir, "hud_$stamp.csv"))).also {
                it.write("time_ms,rpm,speed_kmh,gear,battery_pct,power_kw,dct_c,coolant_c,oil_c,batt_temp_c,accel_pct,brake_pct,ev,sport\n")
            }
        }
    }

    fun log(d: HudData, nowMs: Long) {
        val w = writer ?: return
        if (!d.live) return
        runCatching {
            w.write(
                "$nowMs,${d.rpm},${d.speedKmh},${d.gear},${d.batteryPct},${d.powerKw},${d.dctTempC}," +
                    "${d.coolantC},${d.oilC},${d.battTempC},${d.accelPct},${d.brakePct}," +
                    "${if (d.evMode) 1 else 0},${if (d.sport) 1 else 0}\n"
            )
            rows++
            if (rows % 20 == 0) w.flush()
        }
    }

    fun stop() {
        runCatching {
            writer?.flush()
            writer?.close()
        }
        writer = null
    }
}
