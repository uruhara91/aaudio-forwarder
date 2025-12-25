package com.aaudio.forwarder;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Kita gak butuh UI, ini cuma dummy biar APK valid.
        // Nanti QtScrcpy akan panggil service langsung via 'am start-service'
        
        // Contoh cara test manual (hardcoded):
        // Intent intent = new Intent(this, AudioForwardService.class);
        // intent.setAction("START");
        // intent.putExtra("IP", "192.168.1.5");
        // intent.putExtra("PORT", 28200);
        // startService(intent);
        
        finish(); // Langsung tutup UI-nya
    }
}
