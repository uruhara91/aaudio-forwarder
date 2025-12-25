package com.aaudio.forwarder;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

public class AudioForwardService extends Service {
    private static final String TAG = "AudioForwardService";
    private static final String CHANNEL_ID = "AAudioChannel";

    static {
        try {
            System.loadLibrary("aaudio_forwarder");
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "FATAL: Failed to load native library: " + e.getMessage());
        }
    }

    public native boolean startForwarding(String ip, int port, int sampleRate);
    public native void stopForwarding();
    public native String getStatus();

    @Override
    public void onCreate() {
        super.onCreate();
        // Wajib bikin Notifikasi Channel buat Android 8+
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // --- INI KUNCINYA: Langsung jadi Foreground Service dengan Notifikasi ---
        Notification notification = new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("AAudio Forwarder")
                .setContentText("Streaming audio to PC via USB...")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .build();

        // ID 1234 bebas, yang penting gak 0.
        startForeground(1234, notification);
        // ------------------------------------------------------------------------

        if (intent == null) return START_NOT_STICKY;

        String action = intent.getAction();
        if ("START".equals(action)) {
            String ip = intent.getStringExtra("IP");
            int port = intent.getIntExtra("PORT", 28200);
            int rate = intent.getIntExtra("RATE", 48000);
            
            // Default IP local loopback (karena lewat adb reverse)
            if (ip == null) ip = "127.0.0.1";

            Log.i(TAG, "Requesting start: " + ip + ":" + port);
            
            boolean success = startForwarding(ip, port, rate);
            if (!success) {
                Log.e(TAG, "Failed to start native forwarding (C++ returned false)");
                stopSelf();
            }
        } else if ("STOP".equals(action)) {
            Log.i(TAG, "Stopping service...");
            stopForwarding();
            stopForeground(true);
            stopSelf();
        }

        return START_NOT_STICKY;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "AAudio Service Channel",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
    
    @Override
    public void onDestroy() {
        stopForwarding();
        super.onDestroy();
    }
}