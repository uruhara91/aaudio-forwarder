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
import android.os.Process
import java.nio.ByteBuffer

class AudioForwardService : Service() {
    companion object {
        private const val CHANNEL_ID = "AAudioChannel"
        private const val NOTIFICATION_ID = 1337
        private const val SAMPLE_RATE = 48000
        // Untuk FPS, kita kirim paket kecil lebih sering.
        // 480 frame per 10ms.
        private const val CHUNK_SIZE = 1920 // 48000 * 2ch * 2byte * 0.010s
    }

    private var mediaProjection: MediaProjection? = null
    private var audioRecord: AudioRecord? = null
    @Volatile private var isRunning = false

    // JNI Declarations
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
        val notification = createNotification()
        
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        if (intent?.action == "START" && !isRunning) {
            val resultCode = intent.getIntExtra("RESULT_CODE", 0)
            
            // Handle Parcelable deprecation
            val data = if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra("DATA", Intent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra("DATA")
            }
            
            // Broadcast receiver intent from ADB usually sends String extra for port, check safely
            // But since we use --ei in adb command, it is int.
            val port = intent.getIntExtra("PORT", 28200)

            if (resultCode != 0 && data != null) {
                startCapture(resultCode, data, port)
            } else {
                stopSelf()
            }
        } else if (intent?.action == "com.aaudio.forwarder.STOP") {
            // Support stop via broadcast
            stopCapture()
            stopSelf()
        }

        return START_NOT_STICKY
    }

    private fun startCapture(resultCode: Int, data: Intent, port: Int) {
        isRunning = true
        Thread {
            // RT Priority is crucial for audio glitch-free streaming
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            
            try {
                // 1. Connection Logic (Retry hard)
                // HP Connect ke Localhost (yang di-forward ke PC via ADB Reverse)
                var connected = false
                for (i in 1..20) { // Coba selama 2 detik
                    if (connectToPC("127.0.0.1", port)) {
                        connected = true
                        break
                    }
                    Thread.sleep(100)
                }

                if (!connected) {
                    // Gagal connect, matikan service agar user tau
                    stopSelf()
                    return@Thread
                }

                val pm = getSystemService(MediaProjectionManager::class.java)
                mediaProjection = pm.getMediaProjection(resultCode, data)
                
                val config = AudioPlaybackCaptureConfiguration.Builder(mediaProjection!!)
                    .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                    .addMatchingUsage(AudioAttributes.USAGE_GAME)
                    .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                    .build()

                val format = AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
                    .build()

                // Buffer OS minimal x2 biar aman
                val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT)
                
                audioRecord = AudioRecord.Builder()
                    .setAudioFormat(format)
                    .setBufferSizeInBytes(maxOf(minBuf, CHUNK_SIZE * 4))
                    .setAudioPlaybackCaptureConfig(config)
                    .build()

                audioRecord?.startRecording()
                
                // DIRECT BUFFER: Zero Copy dari Java side ke JNI
                val buffer = ByteBuffer.allocateDirect(CHUNK_SIZE)

                while (isRunning) {
                    // Blocking read. Akan pause thread sampai 1920 bytes (10ms) terkumpul.
                    val read = audioRecord?.read(buffer, CHUNK_SIZE) ?: -1
                    
                    if (read > 0) {
                        // Kirim ke C++ JNI
                        if (!sendAudioDirect(buffer, read)) {
                            // Connection lost
                            break
                        }
                        buffer.clear()
                    } else if (read < 0) {
                        // Error reading audio
                        break
                    }
                }

            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                stopCapture()
            }
        }.start()
    }

    private fun stopCapture() {
        isRunning = false
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {}
        
        try {
            mediaProjection?.stop()
        } catch (e: Exception) {}
        
        mediaProjection = null
        audioRecord = null
        closeConnection()
    }

    private fun createNotification(): Notification {
        // Notification channel setup... (sama seperti sebelumnya)
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("AAudio Forwarder")
            .setContentText("Streaming Audio to PC...")
            .setSmallIcon(android.R.drawable.stat_sys_headset)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Audio Service", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }
    
    override fun onDestroy() {
        stopCapture()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}