package com.arksine.autointegrate.preferences;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.preference.SwitchPreference;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.arksine.autointegrate.MainService;
import com.arksine.autointegrate.R;

/**
 * Created by Eric on 10/1/2016.
 */

public class StatusFragment extends PreferenceFragment {

    private static String TAG = "StatusFragment";

    private final BroadcastReceiver serverStatusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(context.getString(R.string.ACTION_SERVICE_STATUS_CHANGED))) {
                updateServiceStatus();
            }
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.status_preferences);
        PreferenceScreen root = this.getPreferenceScreen();
        SwitchPreference toggleService = (SwitchPreference) root.findPreference("status_pref_key_toggle_service");
        //SwitchPreference togglePower = (SwitchPreference) root.findPreference("status_pref_key_toggle_power");
        SwitchPreference toggleMC = (SwitchPreference) root.findPreference("status_pref_key_toggle_controller");
        //SwitchPreference toggleCamera = (SwitchPreference) root.findPreference("status_pref_key_toggle_camera");
        //SwitchPreference toggleRadio = (SwitchPreference) root.findPreference("status_pref_key_toggle_radio");
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

        toggleMC.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                // Refresh the Micro Controller connection
                Intent refreshIntent = new Intent(getString(R.string.ACTION_REFRESH_CONTROLLER_CONNECTION));
                refreshIntent.putExtra("LearningMode", false);
                LocalBroadcastManager.getInstance(getActivity()).sendBroadcast(refreshIntent);
                return true;
            }
        });

        IntentFilter filter = new IntentFilter(getActivity().getString(R.string.ACTION_SERVICE_STATUS_CHANGED));
        LocalBroadcastManager.getInstance(getActivity()).registerReceiver(serverStatusReceiver, filter);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        LocalBroadcastManager.getInstance(getActivity()).unregisterReceiver(serverStatusReceiver);
    }

    private void updateServiceStatus() {
        PreferenceScreen root = this.getPreferenceScreen();
        SwitchPreference toggleService = (SwitchPreference) root.findPreference("status_pref_key_toggle_service");

        // TODO: does calling "setChecked" in turn call the onPreferenceChangedListener?  If so,
        //       that is not behavior that we want.  Also, check to see if we are suspended.
        if (isServiceRunning(MainService.class)) {
            if (!toggleService.isChecked()) {
                toggleService.setChecked(true);
                Log.i(TAG, "Service is running");
            }
        } else {
            if (toggleService.isChecked()) {
                toggleService.setChecked(false);
                Log.i(TAG, "Service is not running");
            }
        }
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
