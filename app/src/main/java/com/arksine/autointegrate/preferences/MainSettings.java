package com.arksine.autointegrate.preferences;

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

import com.arksine.autointegrate.AutoIntegrate;
import com.arksine.autointegrate.MainService;
import com.arksine.autointegrate.R;
import com.arksine.autointegrate.interfaces.ServiceControlInterface;
import com.arksine.autointegrate.utilities.DLog;
import com.arksine.autointegrate.utilities.UtilityFunctions;

/**
 * Handles Main Settings Fragment
 */

public class MainSettings extends PreferenceFragment {

    private static String TAG = "MainSettings";

    private final BroadcastReceiver serverStatusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(context.getString(R.string.ACTION_SERVICE_STATUS_CHANGED))) {
                String status = intent.getStringExtra("service_status");
                updateServiceStatus(status);
            }
        }
    };

    private final Preference.OnPreferenceChangeListener mServiceToggleListener
            = new Preference.OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference preference, Object o) {
            if ((boolean)o) {
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
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.main_preferences);
        PreferenceScreen root = this.getPreferenceScreen();
        SwitchPreference toggleService = (SwitchPreference) root.findPreference("main_pref_key_toggle_service");
        SwitchPreference togglePower = (SwitchPreference) root.findPreference("main_pref_key_toggle_power");
        SwitchPreference toggleMCU = (SwitchPreference) root.findPreference("main_pref_key_toggle_controller");
        //SwitchPreference toggleCamera = (SwitchPreference) root.findPreference("main_pref_key_toggle_camera");
        SwitchPreference toggleRadio = (SwitchPreference) root.findPreference("main_pref_key_toggle_radio");
        // Check to see if the service is on and set the toggleService preferences value accordingly

        if (UtilityFunctions.isServiceRunning(MainService.class, getActivity())) {
            toggleService.setChecked(true);
            DLog.v(TAG, "Service is running");
        } else {
            toggleService.setChecked(false);
            DLog.v(TAG, "Service is not running");
        }

        toggleService.setOnPreferenceChangeListener(mServiceToggleListener);

        toggleMCU.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                // Refresh the Micro Controller connection
                ServiceControlInterface serviceControl = AutoIntegrate.getServiceControlInterface();
                if (serviceControl != null) {
                    serviceControl.refreshMcuConnection(false, null);
                }
                return true;
            }
        });

        toggleRadio.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object o) {
                // Refresh the Radio connection
                ServiceControlInterface serviceControl = AutoIntegrate.getServiceControlInterface();
                if (serviceControl != null) {
                    serviceControl.refreshRadioConnection();
                }
                return true;
            }
        });


    }

    @Override
    public void onResume() {
        super.onResume();

        IntentFilter filter = new IntentFilter(getActivity().getString(R.string.ACTION_SERVICE_STATUS_CHANGED));
        LocalBroadcastManager.getInstance(getActivity()).registerReceiver(serverStatusReceiver, filter);
    }

    @Override
    public void onPause() {
        super.onPause();

        LocalBroadcastManager.getInstance(getActivity()).unregisterReceiver(serverStatusReceiver);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    private void updateServiceStatus(String status) {
        PreferenceScreen root = this.getPreferenceScreen();
        SwitchPreference toggleService = (SwitchPreference) root.findPreference("main_pref_key_toggle_service");

        // Calling "setChecked" fires the onChangeListener.  The result of this is that
        // if the service is turned on or shut off by some event outside of the app, the
        // result would be updated here and the onChange listener would repeat the action.  The
        // workaround it to set the listener to null, call setChecked, then set the listener back.
        switch (status) {
            case "On":
                if (!toggleService.isChecked()) {
                    toggleService.setOnPreferenceChangeListener(null);
                    toggleService.setChecked(true);
                    toggleService.setOnPreferenceChangeListener(mServiceToggleListener);
                    DLog.v(TAG, "Service is running");
                }
                break;
            case "Off":
                if (toggleService.isChecked()) {
                    toggleService.setOnPreferenceChangeListener(null);
                    toggleService.setChecked(false);
                    toggleService.setOnPreferenceChangeListener(mServiceToggleListener);
                    DLog.v(TAG, "Service is not running");
                }
                break;
            case "Suspended":
                break;
            default:
                //
                break;
        }
    }

}
