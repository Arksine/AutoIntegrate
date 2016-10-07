package com.arksine.autointegrate.Preferences;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.arksine.autointegrate.Interfaces.SerialHelper;
import com.arksine.autointegrate.R;
import com.arksine.autointegrate.Utilities.BluetoothHelper;
import com.arksine.autointegrate.Utilities.UsbHelper;

import java.util.ArrayList;

/**
 * Preference Fragment containing Settings for Arduino Serial Connection
 */

public class ArduinoSettings extends PreferenceFragment {

    private static String TAG = "ArduinoSettings";

    // TODO: Use localbroadcast to restart service in button learning mode
    // TODO: Add button learning activity
    // TODO: Add dimmer learning activity
    // TODO: add preferences to set dimmer and reverse(camera)

    SerialHelper mSerialHelper;
    private String mDeviceType;


    private final BroadcastReceiver deviceListReciever = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(context.getString(R.string.ACTION_DEVICE_CHANGED))) {
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
        ListPreference selectBaudPref = (ListPreference) root.findPreference("arduino_pref_key_select_baud");

        mDeviceType = selectDeviceType.getValue();
        toggleBaudSelection();
        populateDeviceListView();

        selectBaudPref.setSummary(selectBaudPref.getEntry());


        // Check to see if the most recently connected device is the same as the one we are using,
        // but with a different location (USB only) TODO: this needs testing!
        if(mDeviceType.equals("USB")) {
            String connectedVal = PreferenceManager.getDefaultSharedPreferences(getActivity())
                    .getString("arduino_pref_key_connected_id", "");
            String currentVal = selectDevice.getValue();

            // Check to see if a valid change has been made
            if (!connectedVal.equals(currentVal)
                    && !connectedVal.equals("") &&  !currentVal.equals("NO_DEVICE")) {
                String[] connectedIds = connectedVal.split(":");
                CharSequence[] entryVals = selectDevice.getEntryValues();
                for(CharSequence cs : entryVals) {
                    String[] entryIds = cs.toString().split(":");
                    if (connectedIds[0].equals(entryIds[0]) && connectedIds[1].equals(entryIds[1])){
                        // this is the connected device
                        selectDevice.setValue(cs.toString());
                        selectDevice.setSummary(cs);
                        Log.d(TAG, "Select device preference reset");
                        break;
                    }
                }
            }
        }

        selectDeviceType.setSummary(selectDeviceType.getEntry());

        selectDeviceType.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                ListPreference list = (ListPreference)preference;
                CharSequence[] entries = list.getEntries();
                int index = list.findIndexOfValue((String)newValue);
                preference.setSummary(entries[index]);
                mDeviceType = (String)newValue;
                toggleBaudSelection();
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

        selectBaudPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                preference.setSummary(Integer.toString((Integer)newValue));
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


        IntentFilter filter = new IntentFilter(getActivity().getString(R.string.ACTION_DEVICE_CHANGED));
        LocalBroadcastManager.getInstance(getActivity()).registerReceiver(deviceListReciever, filter);

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        LocalBroadcastManager localBM = LocalBroadcastManager.getInstance(getActivity());
        Intent refreshArduinoIntent = new Intent(getString(R.string.ACTION_REFRESH_ARDUINO_CONNECTION));
        localBM.sendBroadcast(refreshArduinoIntent);
        localBM.unregisterReceiver(deviceListReciever);
    }

    private void toggleBaudSelection() {
        PreferenceScreen root = this.getPreferenceScreen();
        ListPreference selectBaudPref = (ListPreference) root.findPreference("arduino_pref_key_select_baud");

        // Bluetooth detects the attached device baud automatically, we need to set the baud for usb
        if (mDeviceType.equals("USB")) {
            selectBaudPref.setEnabled(true);
        } else {
            selectBaudPref.setEnabled(false);
        }
    }


    private void populateDeviceListView() {

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
                    entryValues[i] = deviceInfo[1];
                } else {
                    String[] ids = deviceInfo[1].split(":");

                    // Make sure that we don't enumerate the MJS HD Radio Cable,
                    // VID 0x0403 (1027), PID 0x937C (37756)
                    if (!(ids[0].equals("1027") && ids[1].equals("37756"))) {
                        String vid = Integer.toHexString(Integer.parseInt(ids[0]));
                        String pid = Integer.toHexString(Integer.parseInt(ids[1]));
                        entries[i] = ids[0] + "\nVID:0x" + vid + " PID:0x" + pid + "\n" + ids[2];
                        entryValues[i] = deviceInfo[1];
                    } else {
                        Log.d(TAG, "Skipped enumerating MJS cable");
                    }
                }
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