package com.ayoub.gesturescroll

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult

/**
 * Wraps MediaPipe HandLandmarker and turns the palm's vertical motion into
 * discrete "swipe up" / "swipe down" events.
 *
 * Detection logic:
 *  - We track the palm-base Y (average of wrist + the 4 finger MCP joints,
 *    normalized [0..1], 0 = top of frame, 1 = bottom). Averaging several
 *    rigid points is much less jittery than a single landmark.
 *  - Every sample is pushed into a short time window (WINDOW_MS). If the
 *    total displacement between the oldest and newest sample in that
 *    window crosses SWIPE_THRESHOLD, a swipe fires.
 *  - After firing, the window is cleared and a cooldown starts so a single
 *    physical gesture doesn't fire repeatedly.
 */
class HandTracker(
    context: Context,
    private val onSwipe: (direction: Int) -> Unit,
    private val onDebug: (String) -> Unit = {}
) {
    companion object {
        private val PALM_POINTS = intArrayOf(0, 5, 9, 13, 17) // wrist + finger MCPs
        private const val SWIPE_THRESHOLD = 0.14f   // fraction of frame height
        private const val WINDOW_MS = 400L          // max span of one continuous swipe
        private const val COOLDOWN_MS = 350L         // delay before the next swipe can fire
        private const val MODEL = "hand_landmarker.task"
    }

    private data class Sample(val t: Long, val y: Float)

    private var landmarker: HandLandmarker
    private val history = ArrayDeque<Sample>()
    private var lastSwipeAt = 0L
    @Volatile private var busy = false

    init {
        val base = BaseOptions.builder()
            .setModelAssetPath(MODEL)
            .build()

        val options = HandLandmarker.HandLandmarkerOptions.builder()
            .setBaseOptions(base)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setNumHands(1)
            .setMinHandDetectionConfidence(0.5f)
            .setMinTrackingConfidence(0.5f)
            .setMinHandPresenceConfidence(0.5f)
            .setResultListener { result, _ -> handleResult(result) }
            .setErrorListener { busy = false }
            .build()

        landmarker = HandLandmarker.createFromOptions(context, options)
    }

    fun detect(bitmap: Bitmap, timestampMs: Long) {
        // Skip this frame if MediaPipe hasn't returned the previous one yet,
        // instead of stacking up detectAsync calls (which was silently
        // erroring out under setErrorListener and killing detection).
        if (busy) return
        busy = true
        val mpImage = BitmapImageBuilder(bitmap).build()
        landmarker.detectAsync(mpImage, timestampMs)
    }

    private fun handleResult(result: HandLandmarkerResult) {
        busy = false
        val now = System.currentTimeMillis()

        if (result.landmarks().isEmpty()) {
            history.clear()
            onDebug("main: non détectée")
            return
        }

        val lm = result.landmarks()[0]
        val y = PALM_POINTS.sumOf { lm[it].y().toDouble() }.toFloat() / PALM_POINTS.size

        history.addLast(Sample(now, y))
        while (history.isNotEmpty() && now - history.first().t > WINDOW_MS) {
            history.removeFirst()
        }

        val delta = y - history.first().y   // negative => moved up in frame
        val cooldownLeft = (COOLDOWN_MS - (now - lastSwipeAt)).coerceAtLeast(0)

        onDebug("main: oui  y=%.2f  Δ=%+.2f  cd=%dms".format(y, delta, cooldownLeft))

        if (cooldownLeft == 0L) {
            if (delta <= -SWIPE_THRESHOLD) {
                fire(GestureScrollService.DIRECTION_UP, now)
            } else if (delta >= SWIPE_THRESHOLD) {
                fire(GestureScrollService.DIRECTION_DOWN, now)
            }
        }
    }

    private fun fire(direction: Int, now: Long) {
        lastSwipeAt = now
        history.clear()
        onSwipe(direction)
    }

    fun close() {
        landmarker.close()
    }
}
