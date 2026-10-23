package com.performance.rtss

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.example.fpsmeter.MainActivity

/**
 * Foreground Service that hosts the RTSS HUD directly in Android's WindowManager.
 *
 * Characteristics:
 * - Floats on top of ANY game (Call of Duty, PUBG, Genshin, etc.) or app.
 * - Draggable anywhere across the screen.
 * - Supports Touch-Through mode: passes all touches through to game controls (FLAG_NOT_TOUCHABLE).
 * - Displays real-time hardware thermals, battery, throttling headroom, and VSYNC frametime.
 */
class RtssOverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: RtssOverlayView? = null
    private lateinit var layoutParams: WindowManager.LayoutParams

    private val configListener = HudConfig.OnConfigChangeListener {
        updateTouchThroughState()
        val nm = getSystemService(NotificationManager::class.java)
        nm?.notify(NOTIFICATION_ID, buildNotification())
    }

    companion object {
        const val ACTION_START = "ACTION_START_OVERLAY"
        const val ACTION_STOP = "ACTION_STOP_OVERLAY"
        const val ACTION_TOGGLE_TOUCH_THROUGH = "ACTION_TOGGLE_TOUCH_THROUGH"
        private const val CHANNEL_ID = "rtss_overlay_channel"
        private const val NOTIFICATION_ID = 1001

        var isRunning = false
            private set

        @JvmStatic
        fun start(context: Context) {
            val intent = Intent(context, RtssOverlayService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        @JvmStatic
        fun stop(context: Context) {
            val intent = Intent(context, RtssOverlayService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        HudConfig.addListener(configListener)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopOverlay()
            stopSelf()
            return START_NOT_STICKY
        }

        if (intent?.action == ACTION_TOGGLE_TOUCH_THROUGH) {
            HudConfig.touchThrough = !HudConfig.touchThrough
            HudConfig.save(this)
            updateTouchThroughState()
            val nm = getSystemService(NotificationManager::class.java)
            nm?.notify(NOTIFICATION_ID, buildNotification())
            return START_STICKY
        }

        startForeground(NOTIFICATION_ID, buildNotification())
        showOverlay()
        isRunning = true

        return START_STICKY
    }

    private fun showOverlay() {
        if (overlayView != null) return

        val wm = windowManager ?: return

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        var initialFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

        if (HudConfig.touchThrough) {
            initialFlags = initialFlags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }

        val wmParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            initialFlags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 40
            y = 120
        }
        this.layoutParams = wmParams

        val overlay = RtssOverlayView(this).apply {
            // Enable floating window drag and close handler
            attachToWindowManager(wm, wmParams) {
                stopOverlay()
                stopSelf()
            }
        }

        try {
            wm.addView(overlay, wmParams)
            overlayView = overlay
        } catch (_: Exception) {}
    }

    private fun updateTouchThroughState() {
        val wm = windowManager ?: return
        val view = overlayView ?: return
        if (!::layoutParams.isInitialized) return

        val currentFlags = layoutParams.flags
        val newFlags = if (HudConfig.touchThrough) {
            currentFlags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        } else {
            currentFlags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        }

        if (newFlags != currentFlags) {
            layoutParams.flags = newFlags
            try {
                wm.updateViewLayout(view, layoutParams)
            } catch (_: Exception) {}
        }
    }

    private fun stopOverlay() {
        if (overlayView != null) {
            try {
                windowManager?.removeView(overlayView)
            } catch (_: Exception) {}
            overlayView = null
        }
        isRunning = false
    }

    override fun onDestroy() {
        super.onDestroy()
        HudConfig.removeListener(configListener)
        stopOverlay()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "RTSS Game Telemetry HUD",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows real-time in-game FPS and thermal telemetry HUD"
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java)
        val pOpenApp = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, RtssOverlayService::class.java).apply {
            action = ACTION_STOP
        }
        val pStop = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val toggleTouchIntent = Intent(this, RtssOverlayService::class.java).apply {
            action = ACTION_TOGGLE_TOUCH_THROUGH
        }
        val pToggleTouch = PendingIntent.getService(
            this, 2, toggleTouchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val touchActionTitle = if (HudConfig.touchThrough) "🔓 Unlock Drag" else "🔒 Lock (Touch Through)"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("RTSS In-Game Telemetry Active")
            .setContentText(
                if (HudConfig.touchThrough) "Touch-Through ON: All touches pass through to game controls"
                else "Draggable: Drag HUD anywhere on screen"
            )
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setContentIntent(pOpenApp)
            .addAction(android.R.drawable.ic_menu_manage, touchActionTitle, pToggleTouch)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop HUD", pStop)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
