package com.arksine.autointegrate.power;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.preference.PreferenceManager;

import com.arksine.autointegrate.AutoIntegrate;
import com.arksine.autointegrate.MainService;
import com.arksine.autointegrate.interfaces.ServiceControlInterface;
import com.arksine.autointegrate.utilities.DLog;
import com.arksine.autointegrate.utilities.UtilityFunctions;

/**
 * Broadcast Reciever to handle boot, power connected, and power disconnected events.  Power
 * related events are only handled if power integration is enabled
 */
public class PowerEventReceiver extends BroadcastReceiver {
    private static final String TAG = "PowerEventReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        boolean powerEnabled = PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean("main_pref_key_toggle_power", false);

        String action = intent.getAction();
        if (action.equals(Intent.ACTION_BOOT_COMPLETED)) {

            boolean startOnBoot = PreferenceManager.getDefaultSharedPreferences(context)
                    .getBoolean("main_pref_key_start_on_boot", false);
            if (startOnBoot) {
                final Context bootContext = context;
                Handler launchHandler = new Handler();
                launchHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        Intent startIntent = new Intent(bootContext, MainService.class);
                        bootContext.startService(startIntent);
                    }
                }, 5000);
            }

        } else if (powerEnabled &&
                UtilityFunctions.isServiceRunning(MainService.class, context)) {

            if (action.equals(Intent.ACTION_POWER_CONNECTED)) {
                DLog.v(TAG, "Power Reconnected, wake device");

                // Wake service thread
                ServiceControlInterface serviceControl = AutoIntegrate.getServiceControlInterface();
                if (serviceControl != null) {
                    serviceControl.wakeUpDevice();
                }


            } else if (action.equals(Intent.ACTION_POWER_DISCONNECTED)) {
                DLog.v(TAG, "Power Disconnected, attempt sleep");

                // Wake service thread
                ServiceControlInterface serviceControl = AutoIntegrate.getServiceControlInterface();
                if (serviceControl != null) {
                    serviceControl.suspendDevice();
                }
            }
        }
    }
}
