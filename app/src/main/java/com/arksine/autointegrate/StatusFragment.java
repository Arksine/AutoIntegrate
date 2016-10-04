package com.arksine.autointegrate;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.preference.SwitchPreference;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

/**
 * Created by Eric on 10/1/2016.
 */

public class StatusFragment extends PreferenceFragment {

    private static String TAG = "StatusFragment";

    // TODO:  Need broadcastreceiver for service status, if its off status should be notified.

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.status_preferences);
        PreferenceScreen root = this.getPreferenceScreen();
        PreferenceScreen serviceStatus = (PreferenceScreen) root.findPreference("status_pref_key_service_status");
        //SwitchPreference togglePower = (SwitchPreference) root.findPreference("status_pref_key_toggle_power");
        SwitchPreference toggleArduino = (SwitchPreference) root.findPreference("status_pref_key_toggle_arduino");
        //SwitchPreference toggleCamera = (SwitchPreference) root.findPreference("status_pref_key_toggle_camera");
        //SwitchPreference toggleRadio = (SwitchPreference) root.findPreference("status_pref_key_toggle_radio");
        // Check to see if the service is on and set the toggleService preferences value accordingly

        if (isServiceRunning(MainService.class)) {
            serviceStatus.setTitle("AutoIntegrate Service is ON");
            Log.i(TAG, "Service is running");
        } else {
            serviceStatus.setTitle("AutoIntegrate Service is OFF");
            Log.i(TAG, "Service is not running");
        }

        toggleArduino.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                // wake the service
                Intent wakeIntent = new Intent(getString(R.string.ACTION_WAKE_SERVICE_THREAD));
                LocalBroadcastManager.getInstance(getActivity()).sendBroadcast(wakeIntent);
                return true;
            }
        });

    }

    private boolean isServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getActivity().getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }
}
