package com.aaudio.forwarder;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioPlaybackCaptureConfiguration;
import android.media.AudioRecord;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import java.nio.ByteBuffer;

public class AudioForwardService extends Service {
    private static final String CHANNEL_ID = "AAudioChannel";
    private static final int SAMPLE_RATE = 48000;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_STEREO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    
    // Buffer Size: 4096 bytes (Low latency chunk)
    // Jangan terlalu kecil biar gak kebanyakan syscall, jangan terlalu besar biar gak lag.
    private static final int BUFFER_SIZE = 4096; 

    private MediaProjection mediaProjection;
    private AudioRecord audioRecord;
    private Thread captureThread;
    private volatile boolean isRunning = false;

    static {
        System.loadLibrary("aaudio_forwarder");
    }

    public native boolean initServer(int port);
    // Kita kirim ByteBuffer langsung, bukan byte array!
    public native boolean sendPcmDataDirect(ByteBuffer buffer, int size); 
    public native void closeServer();

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification notification = new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("Audio Forwarder")
                .setContentText("Streaming (Dark Mode)...")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .build();

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1234, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        } else {
            startForeground(1234, notification);
        }

        if (intent != null && "START".equals(intent.getAction())) {
            int resultCode = intent.getIntExtra("RESULT_CODE", 0);
            Intent data = intent.getParcelableExtra("DATA");
            int port = intent.getIntExtra("PORT", 28200);
            if (resultCode != 0 && data != null) startCapture(resultCode, data, port);
        } else if (intent != null && "STOP".equals(intent.getAction())) {
            stopCapture();
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    private void startCapture(int resultCode, Intent data, int port) {
        MediaProjectionManager mpm = getSystemService(MediaProjectionManager.class);
        mediaProjection = mpm.getMediaProjection(resultCode, data);

        AudioPlaybackCaptureConfiguration config = new AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                .build();

        AudioFormat format = new AudioFormat.Builder()
                .setEncoding(AUDIO_FORMAT)
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(CHANNEL_CONFIG)
                .build();

        audioRecord = new AudioRecord.Builder()
                .setAudioFormat(format)
                .setAudioPlaybackCaptureConfig(config)
                .build();

        if (!initServer(port)) {
            stopSelf();
            return;
        }

        isRunning = true;
        captureThread = new Thread(this::captureLoop);
        captureThread.setPriority(Thread.MAX_PRIORITY); // Java Thread Priority max
        captureThread.start();
    }

    private void captureLoop() {
        audioRecord.startRecording();
        
        // ZERO COPY MAGIC: Allocate Direct Buffer
        // Memori ini ada di native heap, bukan Java heap.
        ByteBuffer directBuffer = ByteBuffer.allocateDirect(BUFFER_SIZE);

        while (isRunning) {
            // AudioRecord nulis langsung ke native memory address
            int read = audioRecord.read(directBuffer, BUFFER_SIZE);
            
            if (read > 0) {
                // Kirim ByteBuffer object ke JNI (C++ tinggal ambil pointer address-nya)
                // Tidak ada array copy di sini.
                if (!sendPcmDataDirect(directBuffer, read)) {
                    break;
                }
                directBuffer.clear(); // Reset posisi pointer buffer
            }
        }
        stopCapture();
        stopSelf();
    }

    private void stopCapture() {
        isRunning = false;
        if (audioRecord != null) { try { audioRecord.stop(); audioRecord.release(); } catch (Exception e) {} }
        if (mediaProjection != null) mediaProjection.stop();
        closeServer();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID, "Audio Service", NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(serviceChannel);
        }
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}