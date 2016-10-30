package com.arksine.autointegrate.power;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.arksine.autointegrate.MainService;
import com.arksine.autointegrate.R;
import com.arksine.autointegrate.ServiceThread;
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
                .getBoolean("status_pref_key_toggle_power", false);

        String action = intent.getAction();
        if (action.equals(Intent.ACTION_BOOT_COMPLETED)) {
            // TODO; start service on a delay

        } else if (powerEnabled &&
                UtilityFunctions.isServiceRunning(MainService.class, context)) {

            if (action.equals(Intent.ACTION_POWER_CONNECTED)) {
                // TODO: wake device up and start service thread
                Log.i(TAG, "Power Reconnected, wake device");

                // Wake service thread
                Intent wakeSvcThrdIntent = new Intent(context.getString(R.string.ACTION_WAKE_DEVICE));
                wakeSvcThrdIntent.setClass(context, ServiceThread.class);
                LocalBroadcastManager.getInstance(context).sendBroadcast(wakeSvcThrdIntent);



            } else if (action.equals(Intent.ACTION_POWER_DISCONNECTED)) {
                // TODO: stop service thread, put device to sleep
                Log.i(TAG, "Power Disconnected, attempt sleep");

                // Wake service thread
                Intent suspendSvcThrdIntent = new Intent(context.getString(R.string.ACTION_SUSPEND_DEVICE));
                suspendSvcThrdIntent.setClass(context, ServiceThread.class);
                LocalBroadcastManager.getInstance(context).sendBroadcast(suspendSvcThrdIntent);

            }

        }
    }
}
