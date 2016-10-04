package com.arksine.autointegrate;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import java.util.ArrayList;

/**
 * Created by Eric on 9/30/2016.
 */

public class ArduinoSettings extends PreferenceFragment {

    private static String TAG = "ArduinoSettings";
    // TODO: need to implement an intent filter rather than go through with enumerating and requesting
    //       permission on each device.  Just too many pitfalls.

    // TODO: Use localbroadcast to restart service in button learning mode
    // TODO: Add button learning activity
    // TODO: Add dimmer learning activity
    // TODO: add preferences to set dimmer and reverse(camera)


    // TODO:  Bluetooth Broadcast Receiver was leaked.  Best for it to move somewhere in the service
    //        So it is instantiated only once.

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
        PreferenceScreen editButtons = (PreferenceScreen) root.findPreference("arduino_pref_key_edit_buttons");
        ListPreference selectDeviceType = (ListPreference) root.findPreference("arduino_pref_key_select_device_type");
        ListPreference selectDevice = (ListPreference) root.findPreference("arduino_pref_key_select_device");

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


        editButtons.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Context mContext = getActivity();
                // TODO: Edit the intent below to launch Add/Edit Buttons activity
                //Intent startIntent = new Intent(mContext, .class);
                //mContext.startService(startIntent);
                return true;
            }
        });


        IntentFilter filter = new IntentFilter(ACTION_DEVICE_CHANGED);
        LocalBroadcastManager.getInstance(getActivity()).registerReceiver(deviceListReciever, filter);

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mSerialHelper.disconnect(); // disconnect to clean up registered broadcast receivers
        LocalBroadcastManager localBM = LocalBroadcastManager.getInstance(getActivity());
        Intent refreshService = new Intent(getString(R.string.ACTION_REFRESH_SERVICE_THREAD));
        localBM.sendBroadcast(refreshService);
        localBM.unregisterReceiver(deviceListReciever);
    }

    private void populateDeviceListView() {

        // TODO: Filter out MJS cable from this list

        PreferenceScreen root = this.getPreferenceScreen();
        ListPreference selectDevicePref =
                (ListPreference) root.findPreference("arduino_pref_key_select_device");

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

                if (mDeviceType.equals("BLUETOOTH")) {
                    entries[i] = mAdapterList.get(i);
                } else {
                    // TODO: something isn't working right here
                    String[] ids = deviceInfo[1].split(":");
                    String vid = Integer.toHexString(Integer.parseInt(ids[0]));
                    String pid = Integer.toHexString(Integer.parseInt(ids[1]));
                    entries[i] = deviceInfo[0] + "\nVID:0x" + vid + " PID:0x" + pid + "\n" + ids[2];
                }

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