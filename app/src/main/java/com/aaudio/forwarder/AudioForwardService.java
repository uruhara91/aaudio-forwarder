package com.aaudio.forwarder;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

public class AudioForwardService extends Service {
    private static final String TAG = "AudioForwardService";

    // --- LOAD LIBRARY C++ ---
    static {
        try {
            System.loadLibrary("aaudio_forwarder");
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "Failed to load native library: " + e.getMessage());
        }
    }

    // --- DEFINISI FUNGSI JNI (Jembatan ke C++) ---
    public native boolean startForwarding(String ip, int port, int sampleRate);
    public native void stopForwarding();
    public native String getStatus();

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;

        String action = intent.getAction();
        if ("START".equals(action)) {
            String ip = intent.getStringExtra("IP");
            int port = intent.getIntExtra("PORT", 28200);
            int rate = intent.getIntExtra("RATE", 48000);
            
            Log.i(TAG, "Requesting start: " + ip + ":" + port);
            
            // Panggil C++
            boolean success = startForwarding(ip, port, rate);
            if (!success) {
                Log.e(TAG, "Failed to start native forwarding");
                stopSelf();
            }
        } else if ("STOP".equals(action)) {
            // Panggil C++
            stopForwarding();
            stopSelf();
        }

        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    
    @Override
    public void onDestroy() {
        stopForwarding();
        super.onDestroy();
    }
}