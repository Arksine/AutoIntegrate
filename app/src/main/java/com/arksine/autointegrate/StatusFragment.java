package com.arksine.autointegrate;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.preference.SwitchPreference;
import android.util.Log;

/**
 * Created by Eric on 10/1/2016.
 */

public class StatusFragment extends PreferenceFragment {

    private static String TAG = "StatusFragment";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.status_preferences);
        PreferenceScreen root = this.getPreferenceScreen();
        SwitchPreference toggleService = (SwitchPreference) root.findPreference("status_pref_key_toggle_service");

        // Check to see if the service is on and set the toggleService preferences value accordingly
        if (isServiceRunning(MainService.class)) {
            toggleService.setChecked(true);
            Log.i(TAG, "Service is running");
        } else {
            toggleService.setChecked(false);
            Log.i(TAG, "Service is not running");
        }

        toggleService.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                if ((boolean)newValue) {
                    Context mContext = getActivity();
                    Intent startIntent = new Intent(mContext, MainService.class);
                    mContext.startService(startIntent);
                } else {
                    Context mContext = getActivity();
                    Intent stopIntent = new Intent(getString(R.string.ACTION_STOP_SERVICE));
                    mContext.sendBroadcast(stopIntent);
                }
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
