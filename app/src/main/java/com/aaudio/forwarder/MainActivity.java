package com.aaudio.forwarder;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    private static final int REQUEST_MEDIA_PROJECTION = 1000;
    private MediaProjectionManager projectionManager;
    private TextView statusText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Setup UI Gelap (Programmatic biar ringan)
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(Color.BLACK); // <--- DARK MODE
        layout.setGravity(Gravity.CENTER);
        layout.setPadding(32, 32, 32, 32);

        statusText = new TextView(this);
        statusText.setText("Initializing...");
        statusText.setTextColor(Color.WHITE); // <--- TEKS PUTIH
        statusText.setTextSize(16f);
        statusText.setGravity(Gravity.CENTER);
        
        layout.addView(statusText);
        setContentView(layout);

        projectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        startActivityForResult(projectionManager.createScreenCaptureIntent(), REQUEST_MEDIA_PROJECTION);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_MEDIA_PROJECTION) {
            if (resultCode == Activity.RESULT_OK) {
                Intent serviceIntent = new Intent(this, AudioForwardService.class);
                serviceIntent.setAction("START");
                serviceIntent.putExtra("RESULT_CODE", resultCode);
                serviceIntent.putExtra("DATA", data);
                
                Intent originalIntent = getIntent();
                if (originalIntent != null && originalIntent.hasExtra("PORT")) {
                    serviceIntent.putExtra("PORT", originalIntent.getIntExtra("PORT", 28200));
                }

                startForegroundService(serviceIntent);
                
                statusText.setText("SERVICE RUNNING.\n(Black Screen Mode)\nCheck PC for Audio.");
                moveTaskToBack(true);
            } else {
                statusText.setText("PERMISSION DENIED.\nRestart app to try again.");
                statusText.setTextColor(Color.RED);
            }
        }
    }
}