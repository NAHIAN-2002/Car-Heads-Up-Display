package com.example.vezelhud

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay

private val Fg = Color(0xFFEAF2F0)
private val Dim = Color(0xFF7F9490)
private val Accent = Color(0xFF2BE38A)
private val Panel = Color(0xFF1C2222)

@Composable
fun SettingsScreen(s: AppSettings, logDir: String, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var overlayOk by remember { mutableStateOf(Settings.canDrawOverlays(ctx)) }
    LaunchedEffect(Unit) {
        while (true) {
            overlayOk = Settings.canDrawOverlays(ctx)
            delay(1000)
        }
    }
    var showDevices by remember { mutableStateOf(false) }

    val btPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) showDevices = true
    }
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val locPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    fun update(block: () -> Unit) {
        block()
        s.save()
    }

    fun openDevicePicker() {
        val needsPermission = Build.VERSION.SDK_INT >= 31 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) btPermission.launch(Manifest.permission.BLUETOOTH_CONNECT) else showDevices = true
    }

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
            Text("SETTINGS", color = Fg, fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
        }

        Section("DISPLAY")
        ChoiceRow("Speed unit", listOf("km/h" to false, "mph" to true), s.useMph) { v -> update { s.useMph = v } }
        ChoiceRow("Layout", listOf("Full" to LayoutMode.FULL, "Minimal" to LayoutMode.MINIMAL), s.layout) { v ->
            update { s.layout = v }
        }
        ChoiceRow(
            "Orientation",
            listOf("Landscape" to OrientationMode.LANDSCAPE, "Portrait" to OrientationMode.PORTRAIT, "Auto" to OrientationMode.AUTO),
            s.orientation,
        ) { v -> update { s.orientation = v } }
        SwitchRow("Mirror for windshield", null, s.mirrored) { v -> update { s.mirrored = v } }
        SwitchRow("Auto brightness", "Off lets you set a fixed level, e.g. maximum for sunny days", s.autoBrightness) { v ->
            update { s.autoBrightness = v }
        }
        if (!s.autoBrightness) {
            Text("Brightness ${s.brightnessPct}%", color = Fg, fontSize = 15.sp)
            Slider(
                value = s.brightnessPct.toFloat(),
                onValueChange = { s.brightnessPct = it.toInt() },
                onValueChangeFinished = { s.save() },
                valueRange = 5f..100f,
            )
        }

        Section("ALERTS")
        SwitchRow("Warning sound", "Beeps while a critical warning is active", s.soundOn) { v -> update { s.soundOn = v } }
        StepperRow("DCT warning", s.dctWarn, "\u00B0C", 60, 150) { v -> update { s.dctWarn = v } }
        StepperRow("DCT critical", s.dctCrit, "\u00B0C", 60, 150) { v -> update { s.dctCrit = v } }
        StepperRow("Coolant warning", s.coolantWarn, "\u00B0C", 80, 130) { v -> update { s.coolantWarn = v } }
        StepperRow("Coolant critical", s.coolantCrit, "\u00B0C", 80, 130) { v -> update { s.coolantCrit = v } }
        StepperRow("Oil warning", s.oilWarn, "\u00B0C", 90, 180) { v -> update { s.oilWarn = v } }
        StepperRow("Oil critical", s.oilCrit, "\u00B0C", 90, 180) { v -> update { s.oilCrit = v } }
        StepperRow("HV battery warning", s.battWarn, "\u00B0C", 30, 70) { v -> update { s.battWarn = v } }
        StepperRow("HV battery critical", s.battCrit, "\u00B0C", 30, 70) { v -> update { s.battCrit = v } }

        Section("DATA")
        ChoiceRow("Speed source", listOf("Car" to SpeedSource.CAR, "GPS" to SpeedSource.GPS), s.speedSource) { v ->
            update { s.speedSource = v }
            val granted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
            if (v == SpeedSource.GPS && !granted) locPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        SwitchRow("Log data to file", "Saves a CSV for each session in:\n$logDir", s.logToFile) { v ->
            update { s.logToFile = v }
        }

        Section("AUTO-START")
        SwitchRow("Open HUD when the car connects", "Starts when your phone connects to the car's Bluetooth", s.autoStart) { v ->
            update { s.autoStart = v }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Car Bluetooth device", color = Fg, fontSize = 15.sp)
                Text(
                    if (s.carName.isEmpty()) "Not chosen" else "${s.carName} (${s.carAddress})",
                    color = Dim, fontSize = 12.sp,
                )
            }
            Chip("CHOOSE", false) { openDevicePicker() }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Display over other apps", color = Fg, fontSize = 15.sp)
                Text(
                    if (overlayOk) "Allowed: the HUD can open by itself" else "Not allowed: you'll get a notification to tap instead",
                    color = Dim, fontSize = 12.sp,
                )
            }
            Chip("OPEN", false) {
                ctx.startActivity(
                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))
                )
            }
        }
        if (Build.VERSION.SDK_INT >= 33) {
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Notifications", color = Fg, fontSize = 15.sp, modifier = Modifier.weight(1f))
                Chip("ALLOW", false) { notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS) }
            }
        }

        Section("DEVELOPER")
        SwitchRow("Show test buttons", "Sport, Overheat and Offline simulator buttons", s.showTestButtons) { v ->
            update { s.showTestButtons = v }
        }
        Spacer(Modifier.padding(16.dp))
    }

    if (showDevices) {
        val devices = remember { bondedDevices(ctx) }
        AlertDialog(
            onDismissRequest = { showDevices = false },
            confirmButton = { TextButton(onClick = { showDevices = false }) { Text("Cancel") } },
            title = { Text("Choose the car's Bluetooth") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    if (devices.isEmpty()) Text("No paired devices. Pair your phone with the car first.")
                    devices.forEach { (name, address) ->
                        TextButton(onClick = {
                            update {
                                s.carName = name
                                s.carAddress = address
                            }
                            showDevices = false
                        }) { Text("$name\n$address") }
                    }
                }
            },
        )
    }
}

@SuppressLint("MissingPermission")
private fun bondedDevices(ctx: Context): List<Pair<String, String>> = runCatching {
    val adapter = ctx.getSystemService(BluetoothManager::class.java)?.adapter
    adapter?.bondedDevices?.map { (it.name ?: "Unknown") to it.address }.orEmpty()
}.getOrDefault(emptyList())

@Composable
internal fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text,
        color = if (selected) Color.Black else Fg,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) Accent else Panel)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@Composable
private fun Section(title: String) {
    Text(title, color = Accent, fontSize = 13.sp, letterSpacing = 3.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 24.dp, bottom = 6.dp))
}

@Composable
private fun SwitchRow(title: String, sub: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Fg, fontSize = 15.sp)
            if (sub != null) Text(sub, color = Dim, fontSize = 12.sp)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun <T> ChoiceRow(title: String, options: List<Pair<String, T>>, selected: T, onSelect: (T) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(title, color = Fg, fontSize = 15.sp)
        Spacer(Modifier.padding(2.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (label, value) -> Chip(label, value == selected) { onSelect(value) } }
        }
    }
}

@Composable
private fun StepperRow(title: String, value: Int, unit: String, min: Int, max: Int, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Fg, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Chip("-", false) { if (value > min) onChange(value - 1) }
        Text(
            "$value$unit", color = Fg, fontSize = 16.sp, fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center, modifier = Modifier.width(72.dp),
        )
        Chip("+", false) { if (value < max) onChange(value + 1) }
    }
}
