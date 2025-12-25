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
    private static final int BUFFER_SIZE = 2048; 

    private MediaProjection mediaProjection;
    private AudioRecord audioRecord;
    private Thread captureThread;
    private volatile boolean isRunning = false;

    // Simpan parameter start sementara
    private int pendingResultCode;
    private Intent pendingData;
    private int pendingPort;

    static {
        System.loadLibrary("aaudio_forwarder");
    }

    public native boolean initServer(int port);
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
                .setContentText("Waiting for PC connection...")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .build();

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1234, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        } else {
            startForeground(1234, notification);
        }

        if (intent != null && "START".equals(intent.getAction())) {
            pendingResultCode = intent.getIntExtra("RESULT_CODE", 0);
            pendingData = intent.getParcelableExtra("DATA");
            pendingPort = intent.getIntExtra("PORT", 28200);

            if (pendingResultCode != 0 && pendingData != null) {
                // JANGAN BLOCKING DI SINI!
                // Langsung start thread aja.
                startCaptureThread(); 
            }
        } else if (intent != null && "STOP".equals(intent.getAction())) {
            stopCapture();
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    private void startCaptureThread() {
        isRunning = true;
        captureThread = new Thread(this::captureLoop);
        captureThread.setPriority(Thread.MAX_PRIORITY);
        captureThread.start();
    }

    private void captureLoop() {
        // 1. SETUP MEDIA PROJECTION (Harus di thread yang punya Looper kalau bisa, tapi coba di sini dulu aman biasanya)
        // Kalau error "Can't create handler inside thread that has not called Looper.prepare()",
        // kita pindah setup ini ke Main Thread lagi, tapi initServer TETAP di sini.
        
        // --- SETUP AUDIO RECORD ---
        MediaProjectionManager mpm = getSystemService(MediaProjectionManager.class);
        mediaProjection = mpm.getMediaProjection(pendingResultCode, pendingData);

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

        // --- BLOCKING WAIT (INIT SERVER) ---
        // Ini yang bikin macet tadi. Sekarang aman karena di background thread.
        // Dia bakal diem di sini sampe QtScrcpy connect.
        if (!initServer(pendingPort)) {
            // Kalau gagal bind port atau error lain
            stopCapture();
            stopSelf();
            return;
        }

        // --- START RECORDING ---
        audioRecord.startRecording();
        ByteBuffer directBuffer = ByteBuffer.allocateDirect(BUFFER_SIZE);

        while (isRunning) {
            int read = audioRecord.read(directBuffer, BUFFER_SIZE);
            if (read > 0) {
                if (!sendPcmDataDirect(directBuffer, read)) {
                    break; 
                }
                directBuffer.clear();
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