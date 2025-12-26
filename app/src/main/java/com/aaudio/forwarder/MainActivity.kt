package com.aaudio.forwarder

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    companion object {
        private const val REQUEST_MEDIA_PROJECTION = 1000
    }

    private lateinit var projectionManager: MediaProjectionManager
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Minimal dark UI
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            gravity = Gravity.CENTER
            setPadding(32, 32, 32, 32)
        }

        statusText = TextView(this).apply {
            text = "Initializing..."
            setTextColor(Color.WHITE)
            textSize = 16f
            gravity = Gravity.CENTER
        }
        
        layout.addView(statusText)
        setContentView(layout)

        projectionManager = getSystemService(MediaProjectionManager::class.java)
        startActivityForResult(
            projectionManager.createScreenCaptureIntent(),
            REQUEST_MEDIA_PROJECTION
        )
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == REQUEST_MEDIA_PROJECTION) {
            if (resultCode == RESULT_OK && data != null) {
                val serviceIntent = Intent(this, AudioForwardService::class.java).apply {
                    action = "START"
                    putExtra("RESULT_CODE", resultCode)
                    putExtra("DATA", data)
                    
                    // Get port from intent extras if available
                    intent?.getIntExtra("PORT", 28200)?.let {
                        putExtra("PORT", it)
                    }
                }

                startForegroundService(serviceIntent)
                
                statusText.apply {
                    text = "✓ SERVICE RUNNING\n\nMinimizing..."
                    setTextColor(Color.GREEN)
                }
                
                // Auto minimize after 1 second
                window.decorView.postDelayed({
                    moveTaskToBack(true)
                }, 1000)
            } else {
                statusText.apply {
                    text = "✗ PERMISSION DENIED\n\nRestart app to try again"
                    setTextColor(Color.RED)
                }
            }
        }
    }
}