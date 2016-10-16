package com.arksine.autointegrate.activities;

import android.content.Context;
import android.content.Intent;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;
import android.support.v4.content.LocalBroadcastManager;

import com.arksine.autointegrate.R;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ButtonLearningActivityTest {

    @Test
    public void testButtonLearning() throws Exception {
        final Context appContext = InstrumentationRegistry.getTargetContext();

        Thread testThread = new Thread(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < 15; i++) {
                    int random = (int) Math.abs(Math.round(Math.random())) % 10000;
                    Intent intent = new Intent(appContext.getString(R.string.ACTION_CONTROLLER_LEARN_DATA));
                    intent.putExtra("Command", "click");
                    intent.putExtra("Data", String.valueOf(random));
                    LocalBroadcastManager.getInstance(appContext).sendBroadcast(intent);

                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException e) {}
                }
            }
        });

        testThread.start();

    }
}