package com.arksine.autointegrate;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;

import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.MenuItem;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatPreferenceActivity {

    private static String TAG = "MainActivity";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // TODO: Start service if not started?
        // TODO: Add navigation drawer
        // TODO: Add other screens (status, hdradio, power)

        // TODO: Show status screen first, not ArduinoSettings
        /*getFragmentManager().beginTransaction()
                .add(android.R.id.content, new ArduinoSettings())
                .commit();*/
    }

    /**
     * Called to determine if the activity should run in multi-pane mode.
     * The default implementation returns true if the screen is large
     * enough.
     */
    @Override
    public boolean onIsMultiPane() {
        /*boolean preferMultiPane = getResources().getBoolean(
                com.android.internal.R.bool.preferences_prefer_dual_pane);
        return preferMultiPane;*/
        // TODO: check for landscape
        return true;
    }

    /**
     * Populate the activity with the top-level headers.
     */
    @Override
    public void onBuildHeaders(List<Header> target) {
        loadHeadersFromResource(R.xml.preference_headers, target);
    }

    @Override
    protected boolean isValidFragment(String fragmentName) {
        // TODO: need to check all fragments
        return ArduinoSettings.class.getName().equals(fragmentName);
    }


    public static class ArduinoSettings extends PreferenceFragment {

        // TODO: Use localbroadcast to restart service in button learning mode
        // TODO: Add button learning activity
        // TODO: Add dimmer learning activity
        // TODO: add preferences to set dimmer and reverse(camera)

        public static final String ACTION_DEVICE_CHANGED = "com.arksine.autointegrate.ACTION_DEVICE_CHANGED";

        SerialHelper mSerialHelper;
        private String mDeviceType;

        private final BroadcastReceiver deviceListReciever = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (action.equals(ACTION_DEVICE_CHANGED)) {
                    populateDeviceListView();
                }
            }
        };

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            // Load the arduino_preferences from an XML resource
            addPreferencesFromResource(R.xml.arduino_preferences);

            PreferenceScreen root = this.getPreferenceScreen();
            PreferenceScreen startService = (PreferenceScreen) root.findPreference("pref_key_start_service");
            PreferenceScreen stopService = (PreferenceScreen) root.findPreference("pref_key_stop_service");
            ListPreference selectDeviceType = (ListPreference) root.findPreference("pref_key_select_device_type");
            ListPreference selectDevice = (ListPreference) root.findPreference("pref_key_select_device");

            mDeviceType = selectDeviceType.getValue();
            populateDeviceListView();

            selectDeviceType.setSummary(selectDeviceType.getEntry());

            selectDeviceType.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    ListPreference list = (ListPreference)preference;
                    CharSequence[] entries = list.getEntries();
                    int index = list.findIndexOfValue((String)newValue);
                    preference.setSummary(entries[index]);

                    mDeviceType = (String)newValue;
                    populateDeviceListView();

                    return true;
                }
            });
            selectDevice.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    ListPreference list = (ListPreference)preference;
                    CharSequence[] entries = list.getEntries();
                    int index = list.findIndexOfValue((String)newValue);
                    preference.setSummary(entries[index]);
                    return true;
                }
            });


            startService.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    Context mContext = getActivity();
                    Intent startIntent = new Intent(mContext, MainService.class);
                    mContext.startService(startIntent);
                    return true;
                }
            });

            stopService.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    Context mContext = getActivity();
                    Intent stopIntent = new Intent(getString(R.string.ACTION_STOP_SERVICE));
                    mContext.sendBroadcast(stopIntent);
                    return true;
                }
            });



            IntentFilter filter = new IntentFilter(ACTION_DEVICE_CHANGED);
            LocalBroadcastManager.getInstance(getActivity()).registerReceiver(deviceListReciever, filter);
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            LocalBroadcastManager.getInstance(getActivity()).unregisterReceiver(deviceListReciever);
        }


        private void populateDeviceListView() {

            PreferenceScreen root = this.getPreferenceScreen();
            ListPreference selectDevicePref =
                    (ListPreference) root.findPreference("pref_key_select_device");

            if (mDeviceType.equals("BLUETOOTH")) {
                // user selected bluetooth device
                mSerialHelper = new BluetoothHelper(getActivity());
            }
            else {
                // user selected usb device
                mSerialHelper = new UsbHelper(getActivity());

            }

            ArrayList<String> mAdapterList = mSerialHelper.enumerateDevices();

            CharSequence[] entries;
            CharSequence[] entryValues;

            if (mAdapterList == null || mAdapterList.isEmpty()) {
                Log.i(TAG, "No compatible bluetooth devices found on system");
                entries = new CharSequence[1];
                entryValues = new CharSequence[1];

                entries[0]  = "No devices found";
                entryValues[0] = "NO_DEVICE";

                selectDevicePref.setSummary(entries[0]);
            }
            else {

                entries = new CharSequence[mAdapterList.size()];
                entryValues = new CharSequence[mAdapterList.size()];

                for (int i = 0; i < mAdapterList.size(); i++) {

                    String[] deviceInfo = mAdapterList.get(i).split("\n");

                    entries[i] = mAdapterList.get(i);
                    entryValues[i] = deviceInfo[1];

                }
            }

            selectDevicePref.setEntries(entries);
            selectDevicePref.setEntryValues(entryValues);

            // if the currently stored value isn't in the new list, reset the summary
            int index = selectDevicePref.findIndexOfValue(selectDevicePref.getValue());
            if (index == -1) {
                selectDevicePref.setSummary("");
            }
            else {
                selectDevicePref.setSummary(selectDevicePref.getEntry());
            }
        }

    }
}
