package com.performance.rtss

import android.content.Context
import android.content.SharedPreferences

/**
 * Manages user customization options for each HUD stat and appearance:
 * - Stats: FPS, 1% Low, Frametime, Temperature, Battery, Thermal Headroom, Line Graph
 * - Appearance: Text Size (sp), Overlay Scale, Background Opacity / Transparency
 * - Touch Interaction: Touch-Through (click-through to game controls)
 * - Presets: Esports, Full Telemetry, Battery/Heat, Stutter Hunter, Balanced
 */
object HudConfig {

    private const val PREFS_NAME = "rtss_hud_preferences"

    // Stat Toggles
    private const val KEY_SHOW_FPS = "key_show_fps"
    private const val KEY_SHOW_1_PERCENT_LOW = "key_show_1_percent_low"
    private const val KEY_SHOW_FRAMETIME = "key_show_frametime"
    private const val KEY_SHOW_BATTERY_TEMP = "key_show_battery_temp"
    private const val KEY_SHOW_BATTERY_PCT = "key_show_battery_pct"
    private const val KEY_SHOW_THERMAL_HEADROOM = "key_show_thermal_headroom"
    private const val KEY_SHOW_FRAMETIME_GRAPH = "key_show_frametime_graph"

    // Appearance Settings
    private const val KEY_TEXT_SIZE_SP = "key_text_size_sp"
    private const val KEY_OVERLAY_SCALE = "key_overlay_scale"
    private const val KEY_TRANSPARENCY_PCT = "key_transparency_pct"

    // Touch-Through & Free-form
    private const val KEY_TOUCH_THROUGH = "key_touch_through"

    var showFps: Boolean = true
    var showOnePercentLow: Boolean = true
    var showFrametime: Boolean = true
    var showBatteryTemp: Boolean = true
    var showBatteryPct: Boolean = true
    var showThermalHeadroom: Boolean = true
    var showFrametimeGraph: Boolean = true

    var textSizeSp: Float = 13.0f // 10.0f to 20.0f
    var overlayScale: Float = 1.0f // 0.65f to 1.75f (free-form scaling)
    var transparencyPct: Int = 80 // 20% to 100% opacity (alpha)
    var touchThrough: Boolean = false // When true, passes all touches through to the game

    enum class HudPreset(
        val id: String,
        val title: String,
        val description: String,
        val icon: String
    ) {
        BALANCED(
            "balanced",
            "Balanced",
            "FPS, 1% Low, Thermals & Frametime Graph",
            "🎯"
        ),
        ESPORTS(
            "esports",
            "Esports Pro",
            "Minimal FPS & 1% Low, high transparency, touch-through",
            "⚡"
        ),
        FULL_TELEMETRY(
            "full_telemetry",
            "Full Telemetry",
            "All stats, thermals, headroom & scrolling graph",
            "📊"
        ),
        BATTERY_THERMAL(
            "battery_thermal",
            "Battery & Heat",
            "Monitor heat, battery drain & throttling",
            "🔋"
        ),
        STUTTER_HUNTER(
            "stutter_hunter",
            "Stutter Hunter",
            "High-contrast frametime spikes & frame pacing",
            "📈"
        )
    }

    fun interface OnConfigChangeListener {
        fun onConfigChanged()
    }

    private val listeners = ArrayList<OnConfigChangeListener>()

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        showFps = prefs.getBoolean(KEY_SHOW_FPS, true)
        showOnePercentLow = prefs.getBoolean(KEY_SHOW_1_PERCENT_LOW, true)
        showFrametime = prefs.getBoolean(KEY_SHOW_FRAMETIME, true)
        showBatteryTemp = prefs.getBoolean(KEY_SHOW_BATTERY_TEMP, true)
        showBatteryPct = prefs.getBoolean(KEY_SHOW_BATTERY_PCT, true)
        showThermalHeadroom = prefs.getBoolean(KEY_SHOW_THERMAL_HEADROOM, true)
        showFrametimeGraph = prefs.getBoolean(KEY_SHOW_FRAMETIME_GRAPH, true)

        textSizeSp = prefs.getFloat(KEY_TEXT_SIZE_SP, 13.0f)
        overlayScale = prefs.getFloat(KEY_OVERLAY_SCALE, 1.0f)
        transparencyPct = prefs.getInt(KEY_TRANSPARENCY_PCT, 80)
        touchThrough = prefs.getBoolean(KEY_TOUCH_THROUGH, false)
    }

    fun save(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putBoolean(KEY_SHOW_FPS, showFps)
            putBoolean(KEY_SHOW_1_PERCENT_LOW, showOnePercentLow)
            putBoolean(KEY_SHOW_FRAMETIME, showFrametime)
            putBoolean(KEY_SHOW_BATTERY_TEMP, showBatteryTemp)
            putBoolean(KEY_SHOW_BATTERY_PCT, showBatteryPct)
            putBoolean(KEY_SHOW_THERMAL_HEADROOM, showThermalHeadroom)
            putBoolean(KEY_SHOW_FRAMETIME_GRAPH, showFrametimeGraph)
            putFloat(KEY_TEXT_SIZE_SP, textSizeSp)
            putFloat(KEY_OVERLAY_SCALE, overlayScale)
            putInt(KEY_TRANSPARENCY_PCT, transparencyPct)
            putBoolean(KEY_TOUCH_THROUGH, touchThrough)
            apply()
        }
        notifyListeners()
    }

    fun applyPreset(context: Context, preset: HudPreset) {
        when (preset) {
            HudPreset.BALANCED -> {
                showFps = true
                showOnePercentLow = true
                showFrametime = false
                showBatteryTemp = true
                showBatteryPct = true
                showThermalHeadroom = true
                showFrametimeGraph = true
                textSizeSp = 13.0f
                overlayScale = 1.0f
                transparencyPct = 80
                touchThrough = false
            }
            HudPreset.ESPORTS -> {
                showFps = true
                showOnePercentLow = true
                showFrametime = false
                showBatteryTemp = false
                showBatteryPct = false
                showThermalHeadroom = false
                showFrametimeGraph = false
                textSizeSp = 11.0f
                overlayScale = 0.85f
                transparencyPct = 85
                touchThrough = true
            }
            HudPreset.FULL_TELEMETRY -> {
                showFps = true
                showOnePercentLow = true
                showFrametime = true
                showBatteryTemp = true
                showBatteryPct = true
                showThermalHeadroom = true
                showFrametimeGraph = true
                textSizeSp = 13.0f
                overlayScale = 1.0f
                transparencyPct = 80
                touchThrough = false
            }
            HudPreset.BATTERY_THERMAL -> {
                showFps = true
                showOnePercentLow = false
                showFrametime = false
                showBatteryTemp = true
                showBatteryPct = true
                showThermalHeadroom = true
                showFrametimeGraph = false
                textSizeSp = 12.0f
                overlayScale = 0.95f
                transparencyPct = 75
                touchThrough = false
            }
            HudPreset.STUTTER_HUNTER -> {
                showFps = true
                showOnePercentLow = true
                showFrametime = true
                showBatteryTemp = false
                showBatteryPct = false
                showThermalHeadroom = false
                showFrametimeGraph = true
                textSizeSp = 13.0f
                overlayScale = 1.1f
                transparencyPct = 90
                touchThrough = false
            }
        }
        save(context)
    }

    fun addListener(listener: OnConfigChangeListener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
        }
    }

    fun removeListener(listener: OnConfigChangeListener) {
        listeners.remove(listener)
    }

    fun notifyListeners() {
        for (i in 0 until listeners.size) {
            listeners[i].onConfigChanged()
        }
    }
}
