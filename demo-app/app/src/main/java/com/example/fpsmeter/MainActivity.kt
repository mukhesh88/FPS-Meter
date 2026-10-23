package com.example.fpsmeter

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.performance.rtss.HudConfig
import com.performance.rtss.RtssOverlayService
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        HudConfig.init(this)
        enableEdgeToEdge()

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF0F1117)
                ) {
                    RtssDashboardScreen()
                }
            }
        }
    }
}

@Composable
fun RtssDashboardScreen() {
    val context = LocalContext.current

    // Overlay State
    var isOverlayActive by remember { mutableStateOf(RtssOverlayService.isRunning) }
    var hasOverlayPermission by remember { mutableStateOf(Settings.canDrawOverlays(context)) }

    // Telemetry Stat Toggles
    var showFps by remember { mutableStateOf(HudConfig.showFps) }
    var showOnePercentLow by remember { mutableStateOf(HudConfig.showOnePercentLow) }
    var showFrametime by remember { mutableStateOf(HudConfig.showFrametime) }
    var showBatteryTemp by remember { mutableStateOf(HudConfig.showBatteryTemp) }
    var showBatteryPct by remember { mutableStateOf(HudConfig.showBatteryPct) }
    var showThermalHeadroom by remember { mutableStateOf(HudConfig.showThermalHeadroom) }
    var showFrametimeGraph by remember { mutableStateOf(HudConfig.showFrametimeGraph) }

    // Appearance State (backed by HudConfig)
    var textSizeSp by remember { mutableFloatStateOf(HudConfig.textSizeSp) }
    var overlayScale by remember { mutableFloatStateOf(HudConfig.overlayScale) }
    var transparencyPct by remember { mutableIntStateOf(HudConfig.transparencyPct) }
    var touchThrough by remember { mutableStateOf(HudConfig.touchThrough) }

    fun syncAllStatesFromConfig() {
        showFps = HudConfig.showFps
        showOnePercentLow = HudConfig.showOnePercentLow
        showFrametime = HudConfig.showFrametime
        showBatteryTemp = HudConfig.showBatteryTemp
        showBatteryPct = HudConfig.showBatteryPct
        showThermalHeadroom = HudConfig.showThermalHeadroom
        showFrametimeGraph = HudConfig.showFrametimeGraph
        textSizeSp = HudConfig.textSizeSp
        overlayScale = HudConfig.overlayScale
        transparencyPct = HudConfig.transparencyPct
        touchThrough = HudConfig.touchThrough
    }

    DisposableEffect(Unit) {
        hasOverlayPermission = Settings.canDrawOverlays(context)
        isOverlayActive = RtssOverlayService.isRunning
        val configListener = HudConfig.OnConfigChangeListener {
            syncAllStatesFromConfig()
        }
        HudConfig.addListener(configListener)
        onDispose {
            HudConfig.removeListener(configListener)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "RTSS In-Game HUD",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Text(
                text = "Floating telemetry overlay over all Android games & 3D apps.",
                fontSize = 13.sp,
                color = Color(0xFF8E95A5),
                modifier = Modifier.padding(top = 4.dp, bottom = 18.dp)
            )

            // --- 1. IN-GAME OVERLAY ACTIVATION CARD ---
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isOverlayActive) Color(0xFF003D33) else Color(0xFF161922)
                ),
                border = BorderStroke(1.dp, if (isOverlayActive) Color(0xFF00BFA5) else Color(0xFF262C3D)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (!Settings.canDrawOverlays(context)) {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            )
                            context.startActivity(intent)
                        } else {
                            if (!isOverlayActive) {
                                RtssOverlayService.start(context)
                                isOverlayActive = true
                            } else {
                                RtssOverlayService.stop(context)
                                isOverlayActive = false
                            }
                        }
                    }
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "🎮 In-Game Overlay",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp
                            )
                            Text(
                                text = if (isOverlayActive) "HUD ACTIVE OVER GAMES" else "HUD STOPPED",
                                color = if (isOverlayActive) Color(0xFF00E5FF) else Color(0xFF8E95A5),
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }

                        Switch(
                            checked = isOverlayActive,
                            onCheckedChange = { checked ->
                                if (!Settings.canDrawOverlays(context)) {
                                    val intent = Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:${context.packageName}")
                                    )
                                    context.startActivity(intent)
                                } else {
                                    if (checked) {
                                        RtssOverlayService.start(context)
                                        isOverlayActive = true
                                    } else {
                                        RtssOverlayService.stop(context)
                                        isOverlayActive = false
                                    }
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFF00E5FF),
                                checkedTrackColor = Color(0xFF004D40)
                            )
                        )
                    }

                    if (!hasOverlayPermission) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}")
                                )
                                context.startActivity(intent)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Grant 'Display Over Other Apps' Permission", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Text(
                            text = "Permission granted. Once enabled, switch to any game (Genshin, PUBG, COD) and drag the HUD anywhere.",
                            fontSize = 12.sp,
                            color = Color(0xFF80CBC4),
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }

                    HorizontalDivider(color = Color(0xFF222838), modifier = Modifier.padding(vertical = 12.dp))

                    // Touch-Through (Click-Through Game Controls) Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "🎯 Touch-Through (Click Through HUD)",
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = if (touchThrough) "All touches pass directly through to games. Unlock via notification."
                                else "HUD intercepts touch to move or resize.",
                                color = if (touchThrough) Color(0xFF39FF14) else Color(0xFF8E95A5),
                                fontSize = 11.sp,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }

                        Switch(
                            checked = touchThrough,
                            onCheckedChange = {
                                touchThrough = it
                                HudConfig.touchThrough = it
                                HudConfig.save(context)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFF39FF14),
                                checkedTrackColor = Color(0xFF1B5E20)
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // --- 2. QUICK HUD PRESETS ---
            Text(
                text = "⚡ Quick Presets",
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            ) {
                items(HudConfig.HudPreset.values()) { preset ->
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF161922)),
                        border = BorderStroke(1.dp, Color(0xFF262C3D)),
                        modifier = Modifier
                            .width(165.dp)
                            .clickable {
                                HudConfig.applyPreset(context, preset)
                                syncAllStatesFromConfig()
                            }
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "${preset.icon} ${preset.title}",
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = preset.description,
                                color = Color(0xFF8E95A5),
                                fontSize = 11.sp,
                                minLines = 2,
                                maxLines = 2
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // --- 3. FREE-FORM HUD DESIGNER & SIZING CANVAS ---
            Text(
                text = "📐 Free-Form HUD Sizing & Canvas",
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF161922)),
                border = BorderStroke(1.dp, Color(0xFF262C3D)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Interactive Free-Form Preview",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Drag the bottom-right corner ◢ to resize scale freely, or use sliders below.",
                        color = Color(0xFF8E95A5),
                        fontSize = 11.sp,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )

                    // Interactive Free-Form Mockup Box
                    val alphaFloat = (transparencyPct / 100f).coerceIn(0.2f, 1.0f)
                    val previewScale = overlayScale.coerceIn(0.65f, 1.75f)

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                            .background(Color(0xFF0A0D14), RoundedCornerShape(12.dp))
                            .border(1.dp, Color(0xFF1F2433), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        // Scaled Preview Card
                        Box(
                            modifier = Modifier
                                .background(
                                    Color(0xFF05070B).copy(alpha = alphaFloat),
                                    RoundedCornerShape(8.dp)
                                )
                                .border(1.dp, Color(0xFF00E5FF).copy(alpha = alphaFloat * 0.5f), RoundedCornerShape(8.dp))
                                .padding(horizontal = (12 * previewScale).dp, vertical = (8 * previewScale).dp)
                        ) {
                            Column {
                                if (showFps || showOnePercentLow || showFrametime) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        var textL1 = ""
                                        if (showFps) textL1 += "FPS: 120.0 "
                                        if (showOnePercentLow) textL1 += "(1% Low: 89.5) "
                                        if (showFrametime) textL1 += "| 8.3 ms"
                                        Text(
                                            text = textL1.trim(),
                                            color = Color(0xFF00E5FF),
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = (textSizeSp * previewScale * 0.85f).sp
                                        )
                                    }
                                }

                                if (showBatteryTemp || showBatteryPct || showThermalHeadroom) {
                                    Spacer(modifier = Modifier.height((2 * previewScale).dp))
                                    var textL2 = ""
                                    if (showBatteryTemp) textL2 += "TEMP: 37.5°C "
                                    if (showBatteryPct) textL2 += "| BAT: 75% "
                                    if (showThermalHeadroom) textL2 += "| HR: 80%"
                                    Text(
                                        text = textL2.trim(),
                                        color = Color(0xFF39FF14),
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = (textSizeSp * previewScale * 0.80f).sp
                                    )
                                }

                                if (showFrametimeGraph) {
                                    Spacer(modifier = Modifier.height((4 * previewScale).dp))
                                    Box(
                                        modifier = Modifier
                                            .width((140 * previewScale).dp)
                                            .height((22 * previewScale).dp)
                                            .background(Color(0xFF0A0E14), RoundedCornerShape(4.dp))
                                            .border(1.dp, Color(0xFF263238), RoundedCornerShape(4.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "〰 8.3ms VSYNC GRAPH 〰",
                                            color = Color(0xFF00E5FF),
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = (7 * previewScale).sp
                                        )
                                    }
                                }
                            }
                        }

                        // Corner Resize Handle for Free-Form scaling
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(6.dp)
                                .size(32.dp)
                                .background(Color(0xFF161922), RoundedCornerShape(8.dp))
                                .border(1.dp, Color(0xFF00E5FF), RoundedCornerShape(8.dp))
                                .pointerInput(Unit) {
                                    detectDragGestures { change, dragAmount ->
                                        change.consume()
                                        val delta = (dragAmount.x + dragAmount.y) / 2f
                                        val newScale = (overlayScale + (delta / 120f)).coerceIn(0.65f, 1.75f)
                                        val rounded = (newScale * 100f).roundToInt() / 100f
                                        if (rounded != overlayScale) {
                                            overlayScale = rounded
                                            HudConfig.overlayScale = rounded
                                            HudConfig.save(context)
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("◢", color = Color(0xFF00E5FF), fontSize = 14.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Text Size Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Text Size", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text("Font scale for telemetry labels", color = Color(0xFF8E95A5), fontSize = 11.sp)
                        }
                        Text(
                            text = "${textSizeSp.roundToInt()} sp",
                            color = Color(0xFF00E5FF),
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                    Slider(
                        value = textSizeSp,
                        onValueChange = {
                            textSizeSp = it
                            HudConfig.textSizeSp = it
                            HudConfig.save(context)
                        },
                        valueRange = 10f..20f,
                        steps = 9,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF00E5FF),
                            activeTrackColor = Color(0xFF00E5FF),
                            inactiveTrackColor = Color(0xFF222838)
                        ),
                        modifier = Modifier.padding(vertical = 4.dp)
                    )

                    HorizontalDivider(color = Color(0xFF222838), modifier = Modifier.padding(vertical = 8.dp))

                    // Overlay Scale Slider (Proportional Scaling)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Overlay Scale", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text("Proportional scale (text, graph, padding)", color = Color(0xFF8E95A5), fontSize = 11.sp)
                        }
                        Text(
                            text = "${(overlayScale * 100).roundToInt()}%",
                            color = Color(0xFF00E5FF),
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                    Slider(
                        value = overlayScale,
                        onValueChange = {
                            val rounded = (it * 100f).roundToInt() / 100f
                            overlayScale = rounded
                            HudConfig.overlayScale = rounded
                            HudConfig.save(context)
                        },
                        valueRange = 0.65f..1.75f,
                        steps = 21,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF00E5FF),
                            activeTrackColor = Color(0xFF00E5FF),
                            inactiveTrackColor = Color(0xFF222838)
                        ),
                        modifier = Modifier.padding(vertical = 4.dp)
                    )

                    HorizontalDivider(color = Color(0xFF222838), modifier = Modifier.padding(vertical = 8.dp))

                    // Transparency / Opacity Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Background Opacity", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text("Backdrop transparency (${100 - transparencyPct}% translucent)", color = Color(0xFF8E95A5), fontSize = 11.sp)
                        }
                        Text(
                            text = "$transparencyPct%",
                            color = Color(0xFF00E5FF),
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                    Slider(
                        value = transparencyPct.toFloat(),
                        onValueChange = {
                            val rounded = it.roundToInt()
                            transparencyPct = rounded
                            HudConfig.transparencyPct = rounded
                            HudConfig.save(context)
                        },
                        valueRange = 20f..100f,
                        steps = 15,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF00E5FF),
                            activeTrackColor = Color(0xFF00E5FF),
                            inactiveTrackColor = Color(0xFF222838)
                        ),
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // --- 4. TELEMETRY STAT TOGGLES (ON / OFF EACH STAT) ---
            Text(
                text = "Telemetry Customization (On/Off)",
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF161922)),
                border = BorderStroke(1.dp, Color(0xFF262C3D)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    StatToggleRow(
                        title = "Instant / Average FPS",
                        subtitle = "Real-time presentation FPS (e.g. 120.0)",
                        isChecked = showFps,
                        onCheckedChange = {
                            showFps = it
                            HudConfig.showFps = it
                            HudConfig.save(context)
                        }
                    )
                    HorizontalDivider(color = Color(0xFF222838), modifier = Modifier.padding(vertical = 4.dp))

                    StatToggleRow(
                        title = "1% Low FPS",
                        subtitle = "Harmonic mean of worst 1% frames in 2.5s",
                        isChecked = showOnePercentLow,
                        onCheckedChange = {
                            showOnePercentLow = it
                            HudConfig.showOnePercentLow = it
                            HudConfig.save(context)
                        }
                    )
                    HorizontalDivider(color = Color(0xFF222838), modifier = Modifier.padding(vertical = 4.dp))

                    StatToggleRow(
                        title = "Exact Frametime (ms)",
                        subtitle = "VSYNC delta duration (e.g. 8.3 ms)",
                        isChecked = showFrametime,
                        onCheckedChange = {
                            showFrametime = it
                            HudConfig.showFrametime = it
                            HudConfig.save(context)
                        }
                    )
                    HorizontalDivider(color = Color(0xFF222838), modifier = Modifier.padding(vertical = 4.dp))

                    StatToggleRow(
                        title = "Battery Temperature",
                        subtitle = "Device thermals in °Celsius",
                        isChecked = showBatteryTemp,
                        onCheckedChange = {
                            showBatteryTemp = it
                            HudConfig.showBatteryTemp = it
                            HudConfig.save(context)
                        }
                    )
                    HorizontalDivider(color = Color(0xFF222838), modifier = Modifier.padding(vertical = 4.dp))

                    StatToggleRow(
                        title = "Battery Percentage",
                        subtitle = "Current battery charge level",
                        isChecked = showBatteryPct,
                        onCheckedChange = {
                            showBatteryPct = it
                            HudConfig.showBatteryPct = it
                            HudConfig.save(context)
                        }
                    )
                    HorizontalDivider(color = Color(0xFF222838), modifier = Modifier.padding(vertical = 4.dp))

                    StatToggleRow(
                        title = "Thermal Headroom Warning",
                        subtitle = "PowerManager throttle forecasting",
                        isChecked = showThermalHeadroom,
                        onCheckedChange = {
                            showThermalHeadroom = it
                            HudConfig.showThermalHeadroom = it
                            HudConfig.save(context)
                        }
                    )
                    HorizontalDivider(color = Color(0xFF222838), modifier = Modifier.padding(vertical = 4.dp))

                    StatToggleRow(
                        title = "Frametime Line Graph",
                        subtitle = "60-frame scrolling spike graph (GPU lines)",
                        isChecked = showFrametimeGraph,
                        onCheckedChange = {
                            showFrametimeGraph = it
                            HudConfig.showFrametimeGraph = it
                            HudConfig.save(context)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun StatToggleRow(
    title: String,
    subtitle: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = Color(0xFF8E95A5), fontSize = 11.sp)
        }
        Switch(
            checked = isChecked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFF00E5FF),
                checkedTrackColor = Color(0xFF004D40)
            )
        )
    }
}
