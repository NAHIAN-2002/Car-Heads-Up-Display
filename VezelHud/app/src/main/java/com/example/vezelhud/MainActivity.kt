package com.example.vezelhud

import android.app.NotificationManager
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.delay

enum class Screen { HUD, TRIP, SETTINGS }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep the screen on, and show over the lock screen (useful when auto-started)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        // Fullscreen: hide status and navigation bars
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        // The auto-start notification is no longer needed once the HUD is open
        getSystemService(NotificationManager::class.java).cancel(1)

        setContent { HudApp(this) }
    }
}

@Composable
fun HudApp(activity: ComponentActivity) {
    val appContext = activity.applicationContext
    val settings = remember { AppSettings(appContext) }
    val sim = remember { Simulator() }
    val trip = remember { TripTracker() }
    val logger = remember { CsvLogger(appContext) }
    val gps = remember { GpsSpeed(appContext) }
    val sound = remember { AlertSound() }

    var data by remember { mutableStateOf(HudData(live = false)) }
    var screen by remember { mutableStateOf(Screen.HUD) }
    var lastTap by remember { mutableStateOf(0L) }
    var showControls by remember { mutableStateOf(false) }

    // Simulator test switches
    var overheat by remember { mutableStateOf(false) }
    var sport by remember { mutableStateOf(false) }
    var offline by remember { mutableStateOf(false) }
    sim.forceOverheat = overheat
    sim.forceSport = sport
    sim.forceOffline = offline

    // One place that receives data: later this is the OBD2 source instead of the simulator
    LaunchedEffect(Unit) {
        sim.stream().collect { raw ->
            val now = System.currentTimeMillis()
            val d = if (settings.speedSource == SpeedSource.GPS) {
                gps.current(now)?.let { raw.copy(speedKmh = it) } ?: raw
            } else raw
            data = d
            trip.update(d, now)
            logger.log(d, now)
        }
    }

    LaunchedEffect(settings.logToFile) {
        if (settings.logToFile) logger.start() else logger.stop()
    }
    LaunchedEffect(settings.speedSource) {
        if (settings.speedSource == SpeedSource.GPS) {
            while (!gps.hasPermission()) delay(500)
            gps.start()
        } else gps.stop()
    }
    DisposableEffect(Unit) {
        onDispose {
            logger.stop()
            gps.stop()
            sound.release()
        }
    }
    LaunchedEffect(settings.orientation) {
        activity.requestedOrientation = when (settings.orientation) {
            OrientationMode.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            OrientationMode.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            OrientationMode.AUTO -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
    LaunchedEffect(settings.autoBrightness, settings.brightnessPct) {
        val lp = activity.window.attributes
        lp.screenBrightness = if (settings.autoBrightness) {
            WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        } else {
            (settings.brightnessPct / 100f).coerceIn(0.02f, 1f)
        }
        activity.window.attributes = lp
    }

    // Warning beep while a critical alert is active
    val alert = if (data.live) activeAlert(data) else null
    LaunchedEffect(alert, settings.soundOn) {
        if (alert != null && settings.soundOn) {
            while (true) {
                sound.beep()
                delay(1500)
            }
        }
    }

    // Tap the HUD to show the control bar for 5 seconds
    LaunchedEffect(lastTap) {
        if (lastTap != 0L) {
            showControls = true
            delay(5000)
            showControls = false
        }
    }
    BackHandler(enabled = screen != Screen.HUD) { screen = Screen.HUD }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when (screen) {
            Screen.HUD -> Box(
                Modifier
                    .fillMaxSize()
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        lastTap = System.currentTimeMillis()
                    },
            ) {
                HudScreen(data, settings.mirrored, settings.layout, settings.useMph)
                if (showControls) {
                    val items = buildList<Pair<String, () -> Unit>> {
                        add("TRIP" to { screen = Screen.TRIP })
                        add("SETTINGS" to { screen = Screen.SETTINGS })
                        add("MIRROR" to {
                            settings.mirrored = !settings.mirrored
                            settings.save()
                        })
                        if (settings.showTestButtons) {
                            add((if (sport) "SPORT: ON" else "SPORT") to { sport = !sport })
                            add((if (overheat) "OVERHEAT: ON" else "OVERHEAT") to { overheat = !overheat })
                            add((if (offline) "OFFLINE: ON" else "OFFLINE") to { offline = !offline })
                        }
                    }
                    ControlsBar(
                        items = items,
                        onAny = { lastTap = System.currentTimeMillis() },
                        modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp),
                    )
                }
            }
            Screen.TRIP -> TripScreen(trip, settings.useMph) { screen = Screen.HUD }
            Screen.SETTINGS -> SettingsScreen(settings, logger.dirPath) { screen = Screen.HUD }
        }
    }
}

@Composable
private fun ControlsBar(
    items: List<Pair<String, () -> Unit>>,
    onAny: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .horizontalScroll(rememberScrollState())
            .background(Color(0xE6101414), RoundedCornerShape(14.dp))
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items.forEach { (label, action) ->
            Text(
                label,
                color = Color(0xFFEAF2F0),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable {
                        action()
                        onAny()
                    }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            )
        }
    }
}
