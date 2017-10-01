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
import com.arksine.autointegrate.utilities.AdbManager;
import com.arksine.autointegrate.utilities.RootManager;
import com.arksine.autointegrate.utilities.UtilityFunctions;

import timber.log.Timber;

/**
 * Handles Main Settings Fragment
 */

public class MainSettings extends PreferenceFragment {

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

    private final Preference.OnPreferenceChangeListener mAdbToggleListener
            = new Preference.OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference preference, Object o) {
            boolean enabled = (boolean)o;

            AdbManager manager = AdbManager.getInstance(getActivity());
            manager.toggleAdb(enabled, getActivity());

            return true;
        }
    };

    private final Preference.OnPreferenceChangeListener mAdbWirelessListener
            = new Preference.OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference preference, Object o) {
            boolean enabled = (boolean)o;

            AdbManager manager = AdbManager.getInstance(getActivity());
            manager.toggleWirelessAdb(enabled);

            return true;
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.main_preferences);
        PreferenceScreen root = this.getPreferenceScreen();
        SwitchPreference togglePower = (SwitchPreference) root.findPreference("main_pref_key_toggle_power");
        SwitchPreference toggleMCU = (SwitchPreference) root.findPreference("main_pref_key_toggle_controller");
        SwitchPreference toggleRadio = (SwitchPreference) root.findPreference("main_pref_key_toggle_radio");

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

        PreferenceScreen root = this.getPreferenceScreen();
        SwitchPreference toggleService = (SwitchPreference) root
                .findPreference("main_pref_key_toggle_service");
        final SwitchPreference toggleAdb = (SwitchPreference) root
                .findPreference("main_pref_key_toggle_adb");
        final SwitchPreference toggleAdbWireless = (SwitchPreference) root
                .findPreference("main_pref_key_toggle_adb_wireless");

        // Check to see if the service is on and set the toggleService preferences value accordingly
        toggleService.setOnPreferenceChangeListener(null);
        if (UtilityFunctions.isServiceRunning(MainService.class, getActivity())) {
            toggleService.setChecked(true);
            Timber.v("Service is running");
        } else {
            toggleService.setChecked(false);
            Timber.v("Service is not running");
        }
        toggleService.setOnPreferenceChangeListener(mServiceToggleListener);

        RootManager.RootCallback initCb = new RootManager.RootCallback() {
            @Override
            public void OnRootInitialized(final boolean rootStatus) {
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        updateDebuggingOptions(rootStatus);
                    }
                });

            }
        };
        RootManager.checkRootWithCallback(initCb);

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

    private void updateDebuggingOptions(boolean enabled) {

        PreferenceScreen root = this.getPreferenceScreen();
        final SwitchPreference toggleAdb = (SwitchPreference) root
                .findPreference("main_pref_key_toggle_adb");
        final SwitchPreference toggleAdbWireless = (SwitchPreference) root
                .findPreference("main_pref_key_toggle_adb_wireless");

        toggleAdb.setEnabled(enabled);
        toggleAdbWireless.setEnabled(enabled);
        if (enabled) {
            toggleAdb.setOnPreferenceChangeListener(null);
            toggleAdbWireless.setOnPreferenceChangeListener(null);

            AdbManager adbManager = AdbManager.getInstance(getActivity());
            toggleAdb.setChecked(adbManager.isAdbEnabled(getActivity()));
            toggleAdbWireless.setChecked(adbManager.isWirelessAdbEnabled());


            toggleAdb.setOnPreferenceChangeListener(mAdbToggleListener);
            toggleAdbWireless.setOnPreferenceChangeListener(mAdbWirelessListener);
        } else {
            Timber.i("Neither Root nor Signature permissions available, cannot change" +
                    "adb settings");
        }
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
                    Timber.v("Service is running");
                }
                break;
            case "Off":
                if (toggleService.isChecked()) {
                    toggleService.setOnPreferenceChangeListener(null);
                    toggleService.setChecked(false);
                    toggleService.setOnPreferenceChangeListener(mServiceToggleListener);
                    Timber.v("Service is not running");
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
