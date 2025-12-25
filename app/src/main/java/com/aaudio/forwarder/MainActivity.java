package com.aaudio.forwarder;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.TextView; // Tambah ini

public class MainActivity extends Activity {

    private static final int PERMISSION_REQUEST_CODE = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Kasih tampilan dikit biar gak dikira virus
        TextView tv = new TextView(this);
        tv.setText("AAudio Forwarder Ready.\nWaiting for permission...");
        setContentView(tv);

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, PERMISSION_REQUEST_CODE);
        } else {
            // Jangan langsung finish, kasih user liat kalau ini udah ready
            tv.setText("Permission GRANTED.\nService is ready to start via USB.");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == PERMISSION_REQUEST_CODE) {
             TextView tv = new TextView(this);
             setContentView(tv);
             if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                 tv.setText("Permission GRANTED.\nYou can minimize this app now.");
             } else {
                 tv.setText("Permission DENIED.\nApp cannot work without Microphone access.");
             }
        }
    }
}