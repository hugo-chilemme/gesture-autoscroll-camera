package com.ayoub.gesturescroll

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.util.DisplayMetrics
import android.view.accessibility.AccessibilityEvent

/**
 * Generic hands-free scrolling service.
 *
 * This service does NOT target any specific app. It simply dispatches a
 * vertical swipe gesture onto whatever app is currently in the foreground,
 * driven by hand movements detected through the camera. This is a legitimate
 * accessibility use case (hands-free navigation for users with limited
 * mobility or occupied hands).
 */
class GestureScrollService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: GestureScrollService? = null

        const val DIRECTION_UP = 0
        const val DIRECTION_DOWN = 1
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) { /* no-op */ }
    override fun onInterrupt() { /* no-op */ }

    /**
     * Perform a scroll on the current foreground app.
     * @param direction DIRECTION_UP scrolls content upward (finger swipes up),
     *                  DIRECTION_DOWN scrolls content downward.
     */
    fun performScroll(direction: Int) {
        val metrics: DisplayMetrics = resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels

        val x = width / 2f
        val startY: Float
        val endY: Float

        // Swipe covers ~55% of the screen height for a natural page scroll.
        if (direction == DIRECTION_UP) {
            // Finger moves bottom -> top => content scrolls up (next item)
            startY = height * 0.75f
            endY = height * 0.20f
        } else {
            startY = height * 0.25f
            endY = height * 0.80f
        }

        val path = Path().apply {
            moveTo(x, startY)
            lineTo(x, endY)
        }

        val stroke = GestureDescription.StrokeDescription(path, 0L, 120L)
        val gesture = GestureDescription.Builder()
            .addStroke(stroke)
            .build()

        dispatchGesture(gesture, null, null)
    }
}
