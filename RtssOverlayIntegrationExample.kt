package com.performance.rtss

import android.os.Bundle
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/* =========================================================================================
 * 1. TRADITIONAL ANDROID ACTIVITY INTEGRATION (Zero-Permission, Non-Intrusive)
 * ========================================================================================= */

/**
 * Option A: One-line programmatic attachment directly to Activity window decor view.
 * No XML modifications required!
 */
class SampleStandardActivity : AppCompatActivity() {

    private lateinit var hudOverlay: RtssOverlayView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sample)

        // Drop the RTSS HUD over the entire screen with one line:
        hudOverlay = RtssOverlayView.attachToActivity(this)
    }
}

/**
 * Option B: Declared directly in your Activity's XML layout:
 *
 * <FrameLayout
 *     xmlns:android="http://schemas.android.com/apk/res/android"
 *     android:layout_width="match_parent"
 *     android:layout_height="match_parent">
 *
 *     <!-- Your 3D Game / App SurfaceView / RecyclerView here -->
 *     <androidx.recyclerview.widget.RecyclerView
 *         android:id="@+id/recyclerView"
 *         android:layout_width="match_parent"
 *         android:layout_height="match_parent" />
 *
 *     <!-- Draggable RTSS HUD Overlay on top -->
 *     <com.performance.rtss.RtssOverlayView
 *         android:id="@+id/rtssOverlay"
 *         android:layout_width="wrap_content"
 *         android:layout_height="wrap_content"
 *         android:layout_gravity="top|start"
 *         android:layout_margin="16dp" />
 * </FrameLayout>
 */

/* =========================================================================================
 * 2. JETPACK COMPOSE INTEGRATION
 * ========================================================================================= */

/**
 * Self-contained Composable wrapper for [RtssOverlayView].
 */
@Composable
fun RtssOverlayHud(
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { context ->
            RtssOverlayView(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                )
            }
        },
        modifier = modifier
    )
}

/**
 * Example Activity showing Jetpack Compose screen with floating RTSS HUD.
 */
class SampleComposeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        // 1. Underlying Main App Content
                        Text(
                            text = "Main App / 3D Graphics Viewport",
                            modifier = Modifier.align(Alignment.Center)
                        )

                        // 2. Draggable RTSS Telemetry HUD floating on top
                        RtssOverlayHud(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(top = 40.dp, start = 16.dp)
                        )
                    }
                }
            }
        }
    }
}
