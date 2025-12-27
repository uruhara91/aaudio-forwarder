package com.android.sound.helper

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper

class MainActivity : Activity() {
    companion object {
        private const val REQUEST_MEDIA_PROJECTION = 1000
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent)
                } else {
                    startService(serviceIntent)
                }

                Handler(Looper.getMainLooper()).postDelayed({
                    finish()
                }, 500)
            } else {
                finish()
            }
        }
    }
}