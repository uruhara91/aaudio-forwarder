package com.android.sound.helper

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast

class MainActivity : Activity() {
    companion object {
        private const val REQUEST_MEDIA_PROJECTION = 1000
        private const val REQUEST_PERMISSION_AUDIO = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 1. Cek Permission Audio DULU sebelum start screen capture
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_PERMISSION_AUDIO)
        } else {
            startProjection()
        }
    }

    // Handle hasil request permission audio
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISSION_AUDIO) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startProjection()
            } else {
                Toast.makeText(this, "Audio Permission Required!", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun startProjection() {
        try {
            val projectionManager = getSystemService(MediaProjectionManager::class.java)
            startActivityForResult(
                projectionManager.createScreenCaptureIntent(),
                REQUEST_MEDIA_PROJECTION
            )
        } catch (e: Exception) {
            e.printStackTrace()
            finish()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_MEDIA_PROJECTION) {
            if (resultCode == RESULT_OK && data != null) {
                val serviceIntent = Intent(this, AudioForwardService::class.java).apply {
                    action = "START"
                    putExtra("RESULT_CODE", resultCode)
                    putExtra("DATA", data)
                    putExtra("PORT", intent.getIntExtra("PORT", 28200))
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent)
                } else {
                    startService(serviceIntent)
                }

                // Tutup activity agar tidak mengganggu, tapi beri delay sedikit
                Handler(Looper.getMainLooper()).postDelayed({
                    moveTaskToBack(true)
                    finish()
                }, 500)
            } else {
                finish()
            }
        }
    }
}