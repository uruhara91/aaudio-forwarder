package com.aaudio.forwarder;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Bundle;

public class MainActivity extends Activity {

    private static final int PERMISSION_REQUEST_CODE = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Cek izin Microphone
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            // Kalau belum punya izin, MINTA DULU
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, PERMISSION_REQUEST_CODE);
        } else {
            // Kalau sudah punya, langsung tutup (tugas selesai)
            finish();
        }
    }

    // Callback saat user klik Allow/Deny
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == PERMISSION_REQUEST_CODE) {
            // Apapun hasilnya, kita tutup activity-nya.
            // Kalau user 'Deny', service nanti bakal gagal start, tapi app gak crash.
            finish();
        }
    }
}