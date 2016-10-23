package com.arksine.autointegrate.activities;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.os.Handler;
import android.provider.Settings;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.Window;
import android.view.WindowManager;

/**
 * Transparent Activity to change system brightness.  This is required to make sure that
 * brightness changes are reflected when doing so from the service, as a service has no window
 */
public class BrightnessChangeActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);


        Intent brightnessIntent = getIntent();
        int brightness = brightnessIntent.getIntExtra("Brightness", 100);
        ContentResolver cResolver = getContentResolver();
        Window window = getWindow();

        if (brightness <= 0) {
            brightness = 1;
        } else if (brightness > 255) {
            brightness = 255;
        }

        Settings.System.putInt(cResolver, Settings.System.SCREEN_BRIGHTNESS, brightness);

        WindowManager.LayoutParams lp = window.getAttributes();
        lp.screenBrightness = (float) brightness / 255;
        window.setAttributes(lp);
    }

    @Override
    protected void onResume() {
        super.onResume();

        Handler finishHandler = new Handler();
        finishHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                BrightnessChangeActivity.this.finish();
            }
        }, 200);

    }
}
