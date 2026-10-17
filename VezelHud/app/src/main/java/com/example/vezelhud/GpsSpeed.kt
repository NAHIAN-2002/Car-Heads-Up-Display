package com.example.vezelhud

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import androidx.core.content.ContextCompat
import kotlin.math.roundToInt

/** Speed from the phone's GPS. Used as an alternative or fallback to the car's speed. */
class GpsSpeed(private val context: Context) : LocationListener {
    private val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    @Volatile
    private var kmh: Int? = null

    @Volatile
    private var lastFixMs = 0L
    private var running = false

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun start() {
        if (running || !hasPermission()) return
        runCatching {
            manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, this)
            running = true
        }
    }

    fun stop() {
        if (running) {
            manager.removeUpdates(this)
            running = false
        }
    }

    /** Latest GPS speed in km/h, or null when there is no recent fix. */
    fun current(nowMs: Long): Int? = if (nowMs - lastFixMs < 3000) kmh else null

    override fun onLocationChanged(location: Location) {
        if (location.hasSpeed()) {
            kmh = (location.speed * 3.6f).roundToInt()
            lastFixMs = System.currentTimeMillis()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}
}
