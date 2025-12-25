package com.aaudio.forwarder;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.TextView;

public class MainActivity extends Activity {

    private static final int PERMISSION_REQUEST_CODE = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        TextView tv = new TextView(this);
        tv.setText("AAudio Forwarder\nChecking permissions...");
        setContentView(tv);

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, PERMISSION_REQUEST_CODE);
        } else {
            // Izin sudah ada? Langsung minimize ke background!
            startServiceAndMinimize();
        }
    }

    private void startServiceAndMinimize() {
        TextView tv = new TextView(this);
        tv.setText("Permission OK.\nApp is running in background.");
        setContentView(tv);
        
        // Pindah ke background (masih ada di Recent Apps)
        moveTaskToBack(true);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == PERMISSION_REQUEST_CODE) {
             if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                 startServiceAndMinimize();
             } else {
                 TextView tv = new TextView(this);
                 tv.setText("Permission DENIED.\nCannot stream audio.");
                 setContentView(tv);
             }
        }
    }
}