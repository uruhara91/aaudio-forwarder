package com.aaudio.forwarder

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle

class MainActivity : Activity() {
    companion object {
        private const val REQUEST_MEDIA_PROJECTION = 1000
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Tidak perlu setContentView (Hemat resource & waktu)

        val projectionManager = getSystemService(MediaProjectionManager::class.java)
        startActivityForResult(
            projectionManager.createScreenCaptureIntent(),
            REQUEST_MEDIA_PROJECTION
        )
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

                // Start service lalu segera bunuh Activity
                startForegroundService(serviceIntent)
            }
            finish() 
        }
    }
}