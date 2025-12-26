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
        // 1920 bytes = 10ms latency (48kHz * 16bit * stereo)
        private const val CHUNK_SIZE = 1920 
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
            
            val data = if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra("DATA", Intent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra("DATA")
            }
            
            val port = intent.getIntExtra("PORT", 28200)

            if (resultCode != 0 && data != null) {
                startCapture(resultCode, data, port)
            } else {
                stopSelf()
            }
        } else if (intent?.action == "STOP") {
            stopCapture()
            stopSelf()
        }

        return START_STICKY
    }

    private fun startCapture(resultCode: Int, data: Intent, port: Int) {
        isRunning = true
        Thread {
            // Priority thread paling tinggi untuk audio processing
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            
            try {
                val pm = getSystemService(MediaProjectionManager::class.java)
                mediaProjection = pm.getMediaProjection(resultCode, data)
                mediaProjection?.registerCallback(object : MediaProjection.Callback() {
                    override fun onStop() = stopSelf()
                }, null)

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

                // Buffer internal OS dibuat cukup lega, tapi kita baca (read) kecil-kecil
                val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT)
                
                audioRecord = AudioRecord.Builder()
                    .setAudioFormat(format)
                    .setBufferSizeInBytes(maxOf(minBuf, CHUNK_SIZE * 8))
                    .setAudioPlaybackCaptureConfig(config)
                    .build()

                // Retry connection logic (safety race condition)
                var connected = false
                for (i in 1..5) {
                    if (connectToPC("127.0.0.1", port)) {
                        connected = true
                        break
                    }
                    Thread.sleep(100)
                }

                if (!connected) return@Thread

                audioRecord?.startRecording()
                val buffer = ByteBuffer.allocateDirect(CHUNK_SIZE)

                while (isRunning) {
                    // Blocking read: Thread pause sampai data terkumpul 10ms
                    val read = audioRecord?.read(buffer, CHUNK_SIZE) ?: -1
                    
                    if (read > 0) {
                        if (!sendAudioDirect(buffer, read)) break
                        buffer.clear()
                    } else {
                        break
                    }
                }

            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                stopCapture()
            }
        }.apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    private fun stopCapture() {
        isRunning = false
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {}
        
        mediaProjection?.stop()
        mediaProjection = null
        audioRecord = null
        closeConnection()
    }

    private fun createNotification(): Notification {
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("AAudio Streamer")
            .setContentText("Active")
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