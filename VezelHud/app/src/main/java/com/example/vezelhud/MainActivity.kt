package com.example.vezelhud

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep the screen on and go fullscreen (hide status/navigation bars)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        setContent { HudApp() }
    }
}

@Composable
fun HudApp() {
    val sim = remember { Simulator() }
    val flow = remember { sim.stream() }
    val data by flow.collectAsState(initial = HudData())

    var mirrored by remember { mutableStateOf(false) }
    var overheat by remember { mutableStateOf(false) }
    var sport by remember { mutableStateOf(false) }
    sim.forceOverheat = overheat
    sim.forceSport = sport

    Box(Modifier.fillMaxSize()) {
        HudScreen(data, mirrored)
        // Temporary test controls (simulator only)
        Row(Modifier.align(Alignment.BottomCenter).padding(4.dp)) {
            TextButton(onClick = { mirrored = !mirrored }) {
                Text("Mirror", color = Color(0xFF7F9490), fontSize = 12.sp)
            }
            TextButton(onClick = { sport = !sport }) {
                Text("Sport", color = Color(0xFF7F9490), fontSize = 12.sp)
            }
            TextButton(onClick = { overheat = !overheat }) {
                Text("Overheat", color = Color(0xFF7F9490), fontSize = 12.sp)
            }
        }
    }
}
