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
import android.os.Process // Penting untuk prioritas CPU
import android.util.Log
import java.nio.ByteBuffer

class AudioForwardService : Service() {
    companion object {
        private const val TAG = "AAudioFwd"
        private const val CHANNEL_ID = "AAudioChannel"
        private const val SAMPLE_RATE = 48000
        
        // --- OPTIMASI 1: CHUNK SIZE ---
        // 4096 bytes = ~21ms latency (Default lama)
        // 1920 bytes = 10ms latency (Aman & Cepat)
        // 960 bytes  = 5ms latency (Sangat Agresif, butuh CPU stabil)
        private const val SEND_CHUNK_SIZE = 1920 
        
        // Buffer internal AudioRecord tetap agak besar untuk safety
        private const val INTERNAL_BUFFER_SIZE = 4096 * 4 
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
        
        // (Snippet notifikasi startForeground ada di sini...)
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("AAudio Forwarder")
            .setContentText("Recording")
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

    private fun startCapture(resultCode: Int, data: Intent, port: Int) {
        isRunning = true
        captureThread = Thread {

            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            
            try {
                captureLoop(resultCode, data, port)
            } catch (e: Exception) {
                Log.e(TAG, "Capture error: ${e.message}")
            } finally {
                stopCapture()
                stopSelf()
            }
        }.apply {
            // Prioritas JVM (Java) juga dimaksimalkan
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    private fun captureLoop(resultCode: Int, data: Intent, port: Int) {
        val projectionManager = getSystemService(MediaProjectionManager::class.java)
        mediaProjection = projectionManager.getMediaProjection(resultCode, data)

        val captureConfig = AudioPlaybackCaptureConfiguration.Builder(mediaProjection!!)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
            .build()

        // Meminta mode Low Latency ke HAL (Hardware Abstraction Layer)
        val audioFormat = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(SAMPLE_RATE)
            .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
            .build()

        audioRecord = AudioRecord.Builder()
            .setAudioFormat(audioFormat)
            .setAudioPlaybackCaptureConfig(captureConfig)
            .setBufferSizeInBytes(INTERNAL_BUFFER_SIZE)
            .build()

        Log.i(TAG, "Connecting to PC:$port...")
        
        // Retry logic singkat
        var connected = false
        for (i in 1..5) {
            if (connectToPC("127.0.0.1", port)) {
                connected = true
                break
            }
            Thread.sleep(200) 
        }

        if (!connected) return

        audioRecord!!.startRecording()
        
        // Alokasi buffer sesuai CHUNK SIZE kecil (1920 bytes)
        val buffer = ByteBuffer.allocateDirect(SEND_CHUNK_SIZE) 

        while (isRunning) {
            // Kita baca dalam potongan KECIL (10ms) agar segera dikirim
            // Blocking read: Thread akan pause sampai 1920 bytes terkumpul (tepat 10ms)
            val read = audioRecord!!.read(buffer, SEND_CHUNK_SIZE)
            
            if (read > 0) {
                // Kirim secepat kilat
                if (!sendAudioDirect(buffer, read)) {
                    break
                }
                buffer.clear()
            } else if (read < 0) {
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
            } catch (e: Exception) {}
        }
        audioRecord = null
        mediaProjection?.stop()
        mediaProjection = null
        closeConnection()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Audio Forwarding", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
}