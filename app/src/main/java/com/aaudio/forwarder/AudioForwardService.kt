package com.aaudio.forwarder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import java.nio.ByteBuffer

class AudioForwardService : Service() {
    companion object {
        private const val TAG = "AAudioFwd"
        private const val CHANNEL_ID = "AAudioChannel"
        private const val SAMPLE_RATE = 48000
        private const val BUFFER_SIZE = 4096
    }

    private var mediaProjection: MediaProjection? = null
    private var audioRecord: AudioRecord? = null
    private var captureThread: Thread? = null
    @Volatile private var isRunning = false

    // Native methods
    private external fun connectToPC(host: String, port: Int): Boolean
    private external fun sendAudioDirect(buffer: ByteBuffer, size: Int): Boolean
    private external fun closeConnection()

    init {
        System.loadLibrary("aaudio_forwarder")
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("AAudio Forwarder")
            .setContentText("Streaming audio...")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .build()

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1234, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(1234, notification)
        }

        intent?.let {
            when (it.action) {
                "START" -> {
                    val resultCode = it.getIntExtra("RESULT_CODE", 0)
                    // FIX DEPRECATION: Cara aman ambil Parcelable
                    val data = if (Build.VERSION.SDK_INT >= 33) {
                        it.getParcelableExtra("DATA", Intent::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        it.getParcelableExtra("DATA")
                    }
                    val port = it.getIntExtra("PORT", 28200)
                    
                    if (resultCode != 0 && data != null) {
                        startCapture(resultCode, data, port)
                    }
                }
                "STOP" -> {
                    stopCapture()
                    stopSelf()
                }
            }
        }
        return START_NOT_STICKY
    }

    // ... (Sisa kode ke bawah SAMA PERSIS, tidak perlu diubah) ...
    private fun startCapture(resultCode: Int, data: Intent, port: Int) {
        isRunning = true
        captureThread = Thread {
            try {
                captureLoop(resultCode, data, port)
            } catch (e: Exception) {
                Log.e(TAG, "Capture error: ${e.message}")
            } finally {
                stopCapture()
                stopSelf()
            }
        }.apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    private fun captureLoop(resultCode: Int, data: Intent, port: Int) {
        val projectionManager = getSystemService(MediaProjectionManager::class.java)
        mediaProjection = projectionManager.getMediaProjection(resultCode, data)

        // Konfigurasi Audio Record (Sama)
        val captureConfig = AudioPlaybackCaptureConfiguration.Builder(mediaProjection!!)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
            .build()

        val audioFormat = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(SAMPLE_RATE)
            .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
            .build()

        audioRecord = AudioRecord.Builder()
            .setAudioFormat(audioFormat)
            .setAudioPlaybackCaptureConfig(captureConfig)
            // Buffer Capture agak besar gpp biar ga overflow, 
            // latensi ditentukan seberapa cepat kita baca & kirim
            .setBufferSizeInBytes(BUFFER_SIZE * 4) 
            .build()

        Log.i(TAG, "Connecting to PC:$port...")
        
        // --- RETRY LOGIC (PENTING) ---
        var connected = false
        for (i in 1..5) {
            // "127.0.0.1" di HP via adb reverse akan nembak ke PC
            if (connectToPC("127.0.0.1", port)) {
                connected = true
                break
            }
            Log.w(TAG, "Connection attempt $i failed. Retrying...")
            Thread.sleep(500) // Tunggu setengah detik
        }

        if (!connected) {
            Log.e(TAG, "Failed to connect to PC after retries.")
            return
        }
        
        Log.i(TAG, "Connected to PC successfully!")

        audioRecord!!.startRecording()
        
        // Direct Buffer (Zero Copy JNI)
        val buffer = ByteBuffer.allocateDirect(BUFFER_SIZE) 

        while (isRunning) {
            // Optimization: Blocking read, no sleep.
            val read = audioRecord!!.read(buffer, BUFFER_SIZE)
            if (read > 0) {
                // Kirim langsung via JNI
                if (!sendAudioDirect(buffer, read)) {
                    Log.w(TAG, "Send failed (Socket closed?), disconnecting...")
                    break
                }
                buffer.clear()
            } else if (read < 0) {
                Log.e(TAG, "AudioRecord read error: $read")
                break
            }
        }
    }

    private fun stopCapture() {
        isRunning = false
        audioRecord?.apply {
            try {
                stop()
                release()
            } catch (e: Exception) {
                Log.e(TAG, "Stop error: ${e.message}")
            }
        }
        audioRecord = null
        mediaProjection?.stop()
        mediaProjection = null
        closeConnection()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Audio Forwarding",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}