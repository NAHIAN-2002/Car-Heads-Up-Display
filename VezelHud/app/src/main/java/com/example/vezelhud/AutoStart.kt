package com.example.vezelhud

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat

/** Fires when any Bluetooth device connects; opens the HUD if it is the chosen car device. */
class AutoStartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != BluetoothDevice.ACTION_ACL_CONNECTED) return
        val settings = AppSettings(context)
        if (!settings.autoStart || settings.carAddress.isEmpty()) return
        val device = extractDevice(intent)
        if (device?.address.equals(settings.carAddress, ignoreCase = true)) {
            HudLauncher.launch(context)
        }
    }

    @Suppress("DEPRECATION")
    private fun extractDevice(intent: Intent): BluetoothDevice? =
        if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }
}

object HudLauncher {
    private const val CHANNEL = "hud_autostart"

    /**
     * Android blocks apps from opening screens from the background. So we do two things:
     * 1) if "Display over other apps" is allowed, open the HUD directly;
     * 2) always post a notification (with full-screen intent) that opens the HUD when tapped.
     */
    @SuppressLint("MissingPermission")
    fun launch(context: Context) {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)

        if (Settings.canDrawOverlays(context)) {
            runCatching { context.startActivity(intent) }
        }

        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "HUD auto-start", NotificationManager.IMPORTANCE_HIGH)
        )
        val pending = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle("Vezel HUD")
            .setContentText("Tap to open the HUD")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(pending)
            .setFullScreenIntent(pending, true)
            .setAutoCancel(true)
            .build()
        runCatching { nm.notify(1, notification) }
    }
}
