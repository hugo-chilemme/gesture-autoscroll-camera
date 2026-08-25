package com.ayoub.gesturescroll

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import java.util.concurrent.Executors

/**
 * Foreground service that:
 *  1. Shows a small draggable floating overlay (toggle + status).
 *  2. Runs the front camera through ImageAnalysis while the user is in ANY app.
 *  3. Feeds frames to HandTracker, which triggers scrolls via GestureScrollService.
 */
class OverlayService : Service(), LifecycleOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: View
    private lateinit var statusText: TextView

    private var cameraProvider: ProcessCameraProvider? = null
    private val analysisExecutor = Executors.newSingleThreadExecutor()
    private var handTracker: HandTracker? = null
    private var active = true
    private var frameTs = 0L

    companion object {
        private const val CHANNEL_ID = "gesture_scroll"
        private const val NOTIF_ID = 42
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        startForeground(NOTIF_ID, buildNotification())
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        addOverlay()
        handTracker = HandTracker(this) { direction -> onSwipe(direction) }
        startCamera()
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
    }

    private fun onSwipe(direction: Int) {
        val svc = GestureScrollService.instance
        if (svc == null) {
            updateStatus("⚠ Active l'accessibilité")
            return
        }
        if (!active) return
        svc.performScroll(direction)
        updateStatus(if (direction == GestureScrollService.DIRECTION_UP) "▲ scroll" else "▼ scroll")
    }

    private fun addOverlay() {
        overlayView = LayoutInflater.from(this).inflate(R.layout.overlay, null)
        statusText = overlayView.findViewById(R.id.status)
        val toggle = overlayView.findViewById<ImageView>(R.id.toggle)

        toggle.setOnClickListener {
            active = !active
            toggle.setImageResource(
                if (active) R.drawable.ic_active else R.drawable.ic_paused
            )
            updateStatus(if (active) "actif" else "pause")
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 24
            y = 240
        }

        makeDraggable(overlayView, params)
        windowManager.addView(overlayView, params)
    }

    private fun makeDraggable(view: View, params: WindowManager.LayoutParams) {
        var initialX = 0
        var initialY = 0
        var touchX = 0f
        var touchY = 0f
        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x; initialY = params.y
                    touchX = event.rawX; touchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - touchX).toInt()
                    params.y = initialY + (event.rawY - touchY).toInt()
                    windowManager.updateViewLayout(view, params)
                    true
                }
                else -> false
            }
        }
    }

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            cameraProvider = future.get()

            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            analysis.setAnalyzer(analysisExecutor) { proxy -> analyze(proxy) }

            try {
                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    analysis
                )
            } catch (e: Exception) {
                updateStatus("cam err")
            }
        }, mainExecutor)
    }

    private fun analyze(proxy: ImageProxy) {
        try {
            val bitmap = proxy.toBitmapCompat()
            if (bitmap != null) {
                handTracker?.detect(bitmap, frameTs++)
            }
        } finally {
            proxy.close()
        }
    }

    private fun ImageProxy.toBitmapCompat(): Bitmap? {
        val bmp = this.toBitmap() ?: return null
        // Rotate to upright based on the frame's rotation metadata
        val rot = imageInfo.rotationDegrees
        if (rot == 0) return bmp
        val m = Matrix().apply { postRotate(rot.toFloat()) }
        return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
    }

    private fun updateStatus(text: String) {
        overlayView.post { statusText.text = text }
    }

    private fun buildNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Gesture Scroll",
                NotificationManager.IMPORTANCE_LOW
            )
            (getSystemService(NotificationManager::class.java)).createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Gesture Scroll actif")
            .setContentText("Contrôle mains-libres par caméra")
            .setSmallIcon(R.drawable.ic_active)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        cameraProvider?.unbindAll()
        analysisExecutor.shutdown()
        handTracker?.close()
        if (::overlayView.isInitialized) {
            windowManager.removeView(overlayView)
        }
        super.onDestroy()
    }
}
