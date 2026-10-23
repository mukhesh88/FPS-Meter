package com.performance.rtss

import android.view.Choreographer
import java.util.Arrays

/**
 * Zero-allocation Choreographer loop that tracks VSYNC presentation intervals.
 *
 * Performance Characteristics:
 * - Allocates ZERO objects inside [doFrame] to eliminate GC pressure.
 * - Primitive circular ring buffers for timestamps and deltas.
 * - Computes Instantaneous FPS, 500ms Smoothed Rolling Average FPS,
 *   Exact Frametime (ms), and 1% Low FPS (from slowest 1% of frames in the last 2.5s).
 */
class FpsTracker private constructor(
    private val choreographer: Choreographer
) : Choreographer.FrameCallback {

    constructor() : this(Choreographer.getInstance())

    /**
     * Primitive-only listener callback to guarantee zero heap allocations.
     */
    fun interface FrameMetricsListener {
        fun onFrame(
            instantFps: Float,
            avgFps: Float,
            onePercentLow: Float,
            frameTimeMs: Float
        )
    }

    var listener: FrameMetricsListener? = null

    companion object {
        // Buffer capacity: 1024 frames = ~8.5s at 120Hz, ~17s at 60Hz, ~4.2s at 240Hz
        private const val BUFFER_SIZE = 1024
        private const val BUFFER_MASK = BUFFER_SIZE - 1

        private const val SLIDING_WINDOW_NANOS = 500_000_000L       // 500ms sliding average
        private const val ONE_PERCENT_WINDOW_NANOS = 2_500_000_000L // 2.5s window for 1% low
        private const val ONE_PERCENT_RECALC_INTERVAL = 10          // Recalculate 1% low every 10 frames
        private const val MAX_VALID_DELTA_NANOS = 1_000_000_000L    // 1.0s clamp to ignore app pauses/freezes
    }

    // Pre-allocated primitive ring buffers
    private val frameTimestamps = LongArray(BUFFER_SIZE)
    private val frameDeltasNanos = LongArray(BUFFER_SIZE)
    private val frameDeltasMs = FloatArray(BUFFER_SIZE)

    // Pre-allocated scratch buffer for 1% low calculation
    private val scratchFrametimes = FloatArray(BUFFER_SIZE)

    private var headIndex = 0
    private var totalRecordedFrames = 0
    private var lastFrameTimeNanos = 0L
    private var isRunning = false
    private var frameCounter = 0
    private var cachedOnePercentLow = 0.0f

    /**
     * Start the VSYNC frame callback loop.
     */
    fun start() {
        if (isRunning) return
        isRunning = true
        lastFrameTimeNanos = 0L
        headIndex = 0
        totalRecordedFrames = 0
        frameCounter = 0
        cachedOnePercentLow = 0.0f
        choreographer.postFrameCallback(this)
    }

    /**
     * Stop the VSYNC frame callback loop and release choreographer callback.
     */
    fun stop() {
        if (!isRunning) return
        isRunning = false
        choreographer.removeFrameCallback(this)
        lastFrameTimeNanos = 0L
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!isRunning) return

        // Immediately schedule callback for next VSYNC pulse
        choreographer.postFrameCallback(this)

        if (lastFrameTimeNanos == 0L) {
            lastFrameTimeNanos = frameTimeNanos
            return
        }

        val deltaNanos = frameTimeNanos - lastFrameTimeNanos
        lastFrameTimeNanos = frameTimeNanos

        // Ignore negative deltas or large discontinuities caused by app pause / sleep
        if (deltaNanos <= 0L || deltaNanos > MAX_VALID_DELTA_NANOS) {
            return
        }

        val frameTimeMs = deltaNanos / 1_000_000.0f
        val instantFps = 1_000_000_000.0f / deltaNanos

        // Record metrics in primitive ring buffers
        val writeIdx = headIndex
        frameTimestamps[writeIdx] = frameTimeNanos
        frameDeltasNanos[writeIdx] = deltaNanos
        frameDeltasMs[writeIdx] = frameTimeMs

        headIndex = (headIndex + 1) and BUFFER_MASK
        if (totalRecordedFrames < BUFFER_SIZE) {
            totalRecordedFrames++
        }

        // --- 1. Smoothed Rolling Average FPS (500ms sliding window) ---
        var sumNanos = 0L
        var count500ms = 0
        var readIdx = writeIdx
        val cutoff500ms = frameTimeNanos - SLIDING_WINDOW_NANOS

        while (count500ms < totalRecordedFrames) {
            val ts = frameTimestamps[readIdx]
            if (ts < cutoff500ms) break
            sumNanos += frameDeltasNanos[readIdx]
            count500ms++
            readIdx = (readIdx - 1 + BUFFER_SIZE) and BUFFER_MASK
        }

        val avgFps = if (sumNanos > 0L && count500ms > 0) {
            (count500ms.toFloat() * 1_000_000_000.0f) / sumNanos
        } else {
            instantFps
        }

        // --- 2. 1% Low FPS (slowest 1% of frames in last 2.5s) ---
        frameCounter++
        if (frameCounter >= ONE_PERCENT_RECALC_INTERVAL || cachedOnePercentLow == 0.0f) {
            frameCounter = 0
            cachedOnePercentLow = computeOnePercentLow(frameTimeNanos, writeIdx, avgFps)
        }

        // Notify listener via non-boxing primitive signature
        listener?.onFrame(
            instantFps = instantFps,
            avgFps = avgFps,
            onePercentLow = cachedOnePercentLow,
            frameTimeMs = frameTimeMs
        )
    }

    /**
     * Computes the 1% Low FPS using the harmonic mean of the slowest 1% of frames
     * within the past 2.5 seconds.
     *
     * Uses [Arrays.sort] on primitive float array (in-place Dual-Pivot Quicksort, 0 allocations).
     */
    private fun computeOnePercentLow(
        currentFrameNanos: Long,
        currentWriteIndex: Int,
        fallbackFps: Float
    ): Float {
        var countWindow = 0
        var readIdx = currentWriteIndex
        val cutoffWindow = currentFrameNanos - ONE_PERCENT_WINDOW_NANOS

        // Collect all frames within the 2.5s historical window into scratch array
        while (countWindow < totalRecordedFrames) {
            val ts = frameTimestamps[readIdx]
            if (ts < cutoffWindow) break
            scratchFrametimes[countWindow] = frameDeltasMs[readIdx]
            countWindow++
            readIdx = (readIdx - 1 + BUFFER_SIZE) and BUFFER_MASK
        }

        // Need statistically meaningful sample size
        if (countWindow < 10) {
            return fallbackFps
        }

        // Primitive in-place sort: O(N log N) with Dual-Pivot Quicksort (ZERO heap allocation)
        Arrays.sort(scratchFrametimes, 0, countWindow)

        // Slowest 1% frames are the largest frametimes (tail of ascending array)
        val k = (countWindow * 0.01f).toInt().coerceAtLeast(1)
        val startIdx = countWindow - k

        var worstFrametimeSumMs = 0.0f
        var i = startIdx
        while (i < countWindow) {
            worstFrametimeSumMs += scratchFrametimes[i]
            i++
        }

        return if (worstFrametimeSumMs > 0.0f) {
            (k * 1000.0f) / worstFrametimeSumMs
        } else {
            fallbackFps
        }
    }
}
