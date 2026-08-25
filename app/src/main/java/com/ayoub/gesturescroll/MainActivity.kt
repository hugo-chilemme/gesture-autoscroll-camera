package com.ayoub.gesturescroll

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var statusCamera: TextView
    private lateinit var statusOverlay: TextView
    private lateinit var statusA11y: TextView

    private val cameraPermLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            refreshStatus()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusCamera = findViewById(R.id.statusCamera)
        statusOverlay = findViewById(R.id.statusOverlay)
        statusA11y = findViewById(R.id.statusA11y)

        findViewById<Button>(R.id.btnCamera).setOnClickListener {
            cameraPermLauncher.launch(Manifest.permission.CAMERA)
        }
        findViewById<Button>(R.id.btnOverlay).setOnClickListener {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        }
        findViewById<Button>(R.id.btnA11y).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        findViewById<Button>(R.id.btnStart).setOnClickListener { startService() }
        findViewById<Button>(R.id.btnStop).setOnClickListener {
            stopService(Intent(this, OverlayService::class.java))
            Toast.makeText(this, "Service arrêté", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun hasCamera() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    private fun hasOverlay() = Settings.canDrawOverlays(this)

    private fun hasA11y(): Boolean {
        val enabled = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.contains("${packageName}/.GestureScrollService")
    }

    private fun refreshStatus() {
        statusCamera.text = if (hasCamera()) "✓ Caméra" else "✗ Caméra"
        statusOverlay.text = if (hasOverlay()) "✓ Overlay" else "✗ Overlay"
        statusA11y.text = if (hasA11y()) "✓ Accessibilité" else "✗ Accessibilité"
    }

    private fun startService() {
        if (!hasCamera() || !hasOverlay() || !hasA11y()) {
            Toast.makeText(this, "Accorde les 3 permissions d'abord", Toast.LENGTH_LONG).show()
            return
        }
        val intent = Intent(this, OverlayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        Toast.makeText(this, "Service lancé 🤙", Toast.LENGTH_SHORT).show()
    }
}
