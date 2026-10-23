package com.performance.rtss

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager

/**
 * Non-root, permissionless thermal and battery telemetry monitor.
 *
 * Capabilities:
 * - Reads battery temperature via sticky broadcast [Intent.ACTION_BATTERY_CHANGED]
 *   and [BatteryManager.EXTRA_TEMPERATURE] (converted to Celsius: raw / 10.0f).
 * - Queries [PowerManager.getThermalHeadroom] (API 30+) to forecast thermal throttling headroom.
 *   (0.0 = cool, 1.0 = throttling threshold, > 1.0 = severe throttling).
 * - Listens to [PowerManager.OnThermalStatusChangedListener] (API 29+) for hardware thermal states.
 * - Zero permissions required (no Shizuku, root, or ADB).
 */
class ThermalMonitor(private val context: Context) {

    /**
     * Primitive-only listener callback for battery and thermal telemetry.
     */
    fun interface ThermalListener {
        fun onThermalMetrics(
            batteryTempCelsius: Float,
            batteryPct: Int,
            thermalHeadroom: Float, // 0.0 .. 1.0+ (>=1.0 is throttling), Float.NaN if unsupported
            thermalStatus: Int,      // PowerManager.THERMAL_STATUS_*
            isThrottling: Boolean
        )
    }

    var listener: ThermalListener? = null

    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private var isRunning = false
    private var lastBatteryTemp = 0.0f
    private var lastBatteryPct = 0
    private var lastThermalStatus = 0 // PowerManager.THERMAL_STATUS_NONE
    private var lastHeadroom = Float.NaN

    // BroadcastReceiver for ongoing battery status changes
    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            if (intent == null || intent.action != Intent.ACTION_BATTERY_CHANGED) return
            extractBatteryMetrics(intent)
            dispatchUpdate()
        }
    }

    // Thermal status listener for Android 10+ (API 29)
    private val thermalStatusListener: Any? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            PowerManager.OnThermalStatusChangedListener { status ->
                lastThermalStatus = status
                dispatchUpdate()
            }
        } else null
    }

    // Periodic poll for thermal headroom (API 30+) and fallback refresh
    private val pollRunnable = object : Runnable {
        override fun run() {
            if (!isRunning) return
            pollThermalHeadroom()
            dispatchUpdate()
            mainHandler.postDelayed(this, POLL_INTERVAL_MS)
        }
    }

    companion object {
        private const val POLL_INTERVAL_MS = 1000L // 1Hz telemetry polling
        private const val FORECAST_SECONDS = 5     // Lookahead horizon for thermal throttling
    }

    /**
     * Start monitoring battery thermals and PowerManager headroom.
     */
    fun start() {
        if (isRunning) return
        isRunning = true

        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)

        // 1. Synchronously read initial state from sticky broadcast
        val stickyIntent = registerReceiverCompat(context, null, filter)
        if (stickyIntent != null) {
            extractBatteryMetrics(stickyIntent)
        }

        // 2. Register live receiver for battery change events
        registerReceiverCompat(context, batteryReceiver, filter)

        // 3. Register thermal status listener (API 29+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && powerManager != null) {
            val statusListener = thermalStatusListener as? PowerManager.OnThermalStatusChangedListener
            if (statusListener != null) {
                try {
                    powerManager.addThermalStatusListener(context.mainExecutor, statusListener)
                    lastThermalStatus = powerManager.currentThermalStatus
                } catch (_: Exception) {}
            }
        }

        // 4. Initial poll & start 1Hz poll loop
        pollThermalHeadroom()
        mainHandler.postDelayed(pollRunnable, POLL_INTERVAL_MS)
        dispatchUpdate()
    }

    /**
     * Stop telemetry monitoring and unregister all listeners.
     */
    fun stop() {
        if (!isRunning) return
        isRunning = false

        mainHandler.removeCallbacks(pollRunnable)

        try {
            context.unregisterReceiver(batteryReceiver)
        } catch (_: Exception) {}

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && powerManager != null) {
            val statusListener = thermalStatusListener as? PowerManager.OnThermalStatusChangedListener
            if (statusListener != null) {
                try {
                    powerManager.removeThermalStatusListener(statusListener)
                } catch (_: Exception) {}
            }
        }
    }

    private fun extractBatteryMetrics(intent: Intent) {
        val rawTemp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
        lastBatteryTemp = rawTemp / 10.0f

        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        lastBatteryPct = if (level >= 0 && scale > 0) {
            (level * 100) / scale
        } else {
            0
        }
    }

    private fun pollThermalHeadroom() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && powerManager != null) {
            try {
                // Returns 0.0 .. 1.0 (headroom to throttling), or > 1.0 if throttling has started.
                lastHeadroom = powerManager.getThermalHeadroom(FORECAST_SECONDS)
            } catch (_: Exception) {
                lastHeadroom = Float.NaN
            }
        }
    }

    private fun dispatchUpdate() {
        val severeThermalStatus = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            lastThermalStatus >= PowerManager.THERMAL_STATUS_SEVERE
        } else {
            false
        }

        val isThrottling = (!lastHeadroom.isNaN() && lastHeadroom >= 1.0f) || severeThermalStatus

        listener?.onThermalMetrics(
            batteryTempCelsius = lastBatteryTemp,
            batteryPct = lastBatteryPct,
            thermalHeadroom = lastHeadroom,
            thermalStatus = lastThermalStatus,
            isThrottling = isThrottling
        )
    }

    /**
     * Compatibility helper to register receivers with proper flags on Android 13+ (API 33).
     */
    private fun registerReceiverCompat(
        context: Context,
        receiver: BroadcastReceiver?,
        filter: IntentFilter
    ): Intent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
    }
}
