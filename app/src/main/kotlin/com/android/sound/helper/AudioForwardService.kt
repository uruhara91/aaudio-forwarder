package com.android.sound.helper

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
import android.os.PowerManager
import android.os.Process
import android.util.Log
import java.nio.ByteBuffer
import kotlin.math.max

class AudioForwardService : Service() {
    companion object {
        private const val TAG = "AudioForwardService"
        private const val CHANNEL_ID = "SoundServiceChannel"
        private const val NOTIFICATION_ID = 1337
        private const val SAMPLE_RATE = 48000
        private const val CHUNK_SIZE = 1920 
    }

    private var mediaProjection: MediaProjection? = null
    private var audioRecord: AudioRecord? = null
    @Volatile private var isRunning = false
    private var wakeLock: PowerManager.WakeLock? = null

    // JNI
    private external fun connectToPC(host: String, port: Int): Boolean
    private external fun sendAudioDirect(buffer: ByteBuffer, size: Int): Boolean
    private external fun closeConnection()

    init {
        try {
            System.loadLibrary("sound_service")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load native library: ${e.message}")
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        
        // Wakelock untuk mencegah HP tidur saat streaming
        val powerManager = getSystemService(PowerManager::class.java)
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SoundService::Lock")
        wakeLock?.acquire(12*60*60*1000L) // 12 hours safety limit
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 1. Promote Foreground SEGERA sebelum logic lain berjalan
        val notification = createNotification()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        if (intent?.action == "START" && !isRunning) {
            val resultCode = intent.getIntExtra("RESULT_CODE", 0)
            val port = intent.getIntExtra("PORT", 28200)
            
            val data = if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra("DATA", Intent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra("DATA")
            }

            if (resultCode != 0 && data != null) {
                startCapture(resultCode, data, port)
            } else {
                Log.e(TAG, "Invalid Intent Data")
                stopSelf()
            }
        } else if (intent?.action == "com.android.sound.helper.STOP") {
            stopCapture()
            stopSelf()
        }

        return START_NOT_STICKY
    }

    private fun startCapture(resultCode: Int, data: Intent, port: Int) {
        isRunning = true
        Thread {
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            
            try {
                // Connection Retry Logic (Wait for adb reverse to kick in)
                var connected = false
                for (i in 0 until 15) { 
                    if (connectToPC("127.0.0.1", port)) {
                        connected = true
                        Log.d(TAG, "Connected to PC on port $port")
                        break
                    }
                    Thread.sleep(300)
                }

                if (!connected) {
                    Log.e(TAG, "Failed to connect to PC. Is ADB Reverse enabled?")
                    stopSelf()
                    return@Thread
                }

                val pm = getSystemService(MediaProjectionManager::class.java)
                mediaProjection = pm.getMediaProjection(resultCode, data)
                
                // Config Audio Capture
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

                val minInternalBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT)
                
                // SecurityException sering terjadi di sini jika permission belum granted
                audioRecord = AudioRecord.Builder()
                    .setAudioFormat(format)
                    .setBufferSizeInBytes(max(minInternalBuf, CHUNK_SIZE * 4))
                    .setAudioPlaybackCaptureConfig(config)
                    .build()

                audioRecord?.startRecording()
                
                // DIRECT BUFFER
                val buffer = ByteBuffer.allocateDirect(CHUNK_SIZE)

                while (isRunning) {
                    val readBytes = audioRecord?.read(buffer, CHUNK_SIZE) ?: -1
                    
                    if (readBytes > 0) {
                        if (!sendAudioDirect(buffer, readBytes)) {
                            Log.e(TAG, "Socket send failed, stopping")
                            break
                        }
                        buffer.clear()
                    } else {
                        if (readBytes == AudioRecord.ERROR_INVALID_OPERATION || readBytes == AudioRecord.ERROR_BAD_VALUE) {
                            Log.e(TAG, "AudioRecord Error: $readBytes")
                            Thread.sleep(100) // prevent spin loop
                        }
                    }
                }

            } catch (e: SecurityException) {
                Log.e(TAG, "Permission denied during audio setup", e)
            } catch (e: Exception) {
                Log.e(TAG, "Crash in capture loop", e)
            } finally {
                stopCapture()
            }
        }.start()
    }

    private fun stopCapture() {
        isRunning = false
        try {
            if (audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                audioRecord?.stop()
            }
            audioRecord?.release()
        } catch (e: Exception) {}
        
        try {
            mediaProjection?.stop()
        } catch (e: Exception) {}
        
        closeConnection()
        
        mediaProjection = null
        audioRecord = null
    }

    private fun createNotification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        
        return builder
            .setContentTitle("Sound Service")
            .setContentText("Recording...")
            .setSmallIcon(android.R.drawable.ic_media_play) // Pastikan ada icon
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
        try {
            wakeLock?.release()
        } catch (e: Exception) {}
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}