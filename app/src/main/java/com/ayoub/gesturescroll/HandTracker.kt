package com.ayoub.gesturescroll

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult

/**
 * Wraps MediaPipe HandLandmarker and turns the wrist's vertical motion into
 * discrete "swipe up" / "swipe down" events.
 *
 * Detection logic:
 *  - We track landmark index 0 (the wrist) normalized Y over time.
 *  - Y is normalized [0..1], 0 = top of frame, 1 = bottom.
 *  - When the wrist travels more than SWIPE_THRESHOLD in Y within the
 *    velocity window, we emit a swipe and then enter a cooldown so a single
 *    physical gesture doesn't fire repeatedly.
 */
class HandTracker(
    context: Context,
    private val onSwipe: (direction: Int) -> Unit
) {
    companion object {
        private const val WRIST = 0
        private const val SWIPE_THRESHOLD = 0.18f   // fraction of frame height
        private const val COOLDOWN_MS = 900L
        private const val MODEL = "hand_landmarker.task"
    }

    private var landmarker: HandLandmarker
    private var lastY: Float? = null
    private var anchorY: Float? = null
    private var lastSwipeAt = 0L

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
            .setErrorListener { /* swallow, keep stream alive */ }
            .build()

        landmarker = HandLandmarker.createFromOptions(context, options)
    }

    fun detect(bitmap: Bitmap, timestampMs: Long) {
        val mpImage = BitmapImageBuilder(bitmap).build()
        landmarker.detectAsync(mpImage, timestampMs)
    }

    private fun handleResult(result: HandLandmarkerResult) {
        if (result.landmarks().isEmpty()) {
            // Hand left the frame -> reset gesture anchor
            anchorY = null
            lastY = null
            return
        }

        val wristY = result.landmarks()[0][WRIST].y()

        if (anchorY == null) {
            anchorY = wristY
            lastY = wristY
            return
        }

        val start = anchorY ?: wristY
        val delta = wristY - start   // negative => moved up in frame

        val now = System.currentTimeMillis()
        val cooledDown = now - lastSwipeAt > COOLDOWN_MS

        if (cooledDown) {
            if (delta <= -SWIPE_THRESHOLD) {
                lastSwipeAt = now
                anchorY = wristY
                onSwipe(GestureScrollService.DIRECTION_UP)
            } else if (delta >= SWIPE_THRESHOLD) {
                lastSwipeAt = now
                anchorY = wristY
                onSwipe(GestureScrollService.DIRECTION_DOWN)
            }
        }

        // Slowly drift the anchor so slow hand repositioning doesn't count
        val ly = lastY ?: wristY
        if (kotlin.math.abs(wristY - ly) < 0.02f) {
            anchorY = wristY
        }
        lastY = wristY
    }

    fun close() {
        landmarker.close()
    }
}
