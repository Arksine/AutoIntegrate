package com.arksine.autointegrate.preferences;

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

import com.arksine.autointegrate.activities.ButtonLearningActivity;
import com.arksine.autointegrate.interfaces.SerialHelper;
import com.arksine.autointegrate.R;
import com.arksine.autointegrate.utilities.UtilityFunctions;
import com.arksine.autointegrate.utilities.BluetoothHelper;
import com.arksine.autointegrate.utilities.UsbHelper;

import java.util.ArrayList;

/**
 * Preference Fragment containing Settings for Microcontroller Serial Connection
 */

public class MicroControllerSettings extends PreferenceFragment {

    private static String TAG = "MicroControllerSettings";

    // TODO: Add dimmer learning activity
    // TODO: add preferences to set dimmer and reverse(camera)

    SerialHelper mSerialHelper;
    private String mDeviceType;
    private boolean mSettingChanged;


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

        // Load the microcontroller_preferences from an XML resource
        addPreferencesFromResource(R.xml.microcontroller_preferences);

        mSettingChanged = false;

        PreferenceScreen root = this.getPreferenceScreen();
        PreferenceScreen editButtons = (PreferenceScreen) root.findPreference("controller_pref_key_edit_buttons");
        ListPreference selectDeviceType = (ListPreference) root.findPreference("controller_pref_key_select_device_type");
        ListPreference selectDevice = (ListPreference) root.findPreference("controller_pref_key_select_device");
        ListPreference selectBaudPref = (ListPreference) root.findPreference("controller_pref_key_select_baud");

        mDeviceType = selectDeviceType.getValue();
        toggleBaudSelection();
        populateDeviceListView();

        selectBaudPref.setSummary(selectBaudPref.getEntry());


        // Check to see if the most recently connected device is the same as the one we are using,
        // but with a different location (USB only) TODO: this needs testing!
        if(mDeviceType.equals("USB")) {
            String connectedVal = PreferenceManager.getDefaultSharedPreferences(getActivity())
                    .getString("controller_pref_key_connected_id", "");
            String currentVal = selectDevice.getValue();

            // Check to see if a valid change has been made
            if (!connectedVal.equals(currentVal) && !connectedVal.equals("")) {
                String[] connectedIds = connectedVal.split(":");
                CharSequence[] entryVals = selectDevice.getEntryValues();

                // make sure that the list contains devices
                if (!entryVals[0].equals("NO_DEVICE")) {
                    for (CharSequence cs : entryVals) {
                        String[] entryIds = cs.toString().split(":");
                        if (connectedIds[0].equals(entryIds[0]) && connectedIds[1].equals(entryIds[1])) {
                            // this is the connected device
                            selectDevice.setValue(cs.toString());
                            selectDevice.setSummary(cs);
                            Log.d(TAG, "Select device preference reset");
                            break;
                        }
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
                mSettingChanged = true;
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
                mSettingChanged = true;
                return true;
            }
        });

        selectBaudPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                preference.setSummary((String)newValue);
                mSettingChanged = true;
                return true;
            }
        });


        editButtons.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Context mContext = getActivity();
                Intent startIntent = new Intent(mContext, ButtonLearningActivity.class);
                mContext.startActivity(startIntent);

                // Since the learning activity will reconnect the MicroController with new
                // settings, so there is no need to reconnect again unless settings are changed
                mSettingChanged = false;
                return true;
            }
        });


        IntentFilter filter = new IntentFilter(getActivity().getString(R.string.ACTION_DEVICE_CHANGED));
        LocalBroadcastManager.getInstance(getActivity()).registerReceiver(deviceListReciever, filter);

    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        // Refresh the Microcontroller connection with new settings if settings have changed
        if (mSettingChanged) {
            refreshConnection();
        }

        LocalBroadcastManager.getInstance(getActivity()).unregisterReceiver(deviceListReciever);
    }

    private void refreshConnection() {
        LocalBroadcastManager localBM = LocalBroadcastManager.getInstance(getActivity());
        Intent refreshControllerIntent =
                new Intent(getString(R.string.ACTION_REFRESH_CONTROLLER_CONNECTION));
        refreshControllerIntent.putExtra("LearningMode", false);
        localBM.sendBroadcast(refreshControllerIntent);
    }


    private void toggleBaudSelection() {
        PreferenceScreen root = this.getPreferenceScreen();
        ListPreference selectBaudPref = (ListPreference) root.findPreference("controller_pref_key_select_baud");

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
                (ListPreference) root.findPreference("controller_pref_key_select_device");

        if (mDeviceType.equals("BLUETOOTH")) {
            // user selected bluetooth device
            mSerialHelper = new BluetoothHelper(getActivity());
        }
        else {
            // user selected usb device
            mSerialHelper = new UsbHelper(getActivity());

        }

        ArrayList<String> devList = mSerialHelper.enumerateDevices();

        // TODO: We need to check for the MJS cable here, and any other devices we want excluded.
        //       That way we can remove them from the device list
        // Make sure that we don't enumerate the MJS HD Radio Cable,
        // VID 0x0403 (1027), PID 0x937C (37756)
        //if ((ids[0].equals("1027") && ids[1].equals("37756"))) {
        //  Log.d(TAG, "Skipped enumerating MJS cable");
        //}

        CharSequence[] entries;
        CharSequence[] entryValues;

        if (devList == null || devList.isEmpty()) {
            Log.i(TAG, "No compatible devices found on system");
            entries = new CharSequence[1];
            entryValues = new CharSequence[1];

            entries[0]  = "No devices found";
            entryValues[0] = "NO_DEVICE";

            selectDevicePref.setSummary(entries[0]);
        }
        else {

            entries = new CharSequence[devList.size()];
            entryValues = new CharSequence[devList.size()];

            for (int i = 0; i < devList.size(); i++) {

                String[] deviceInfo = devList.get(i).split("\n");

                if (mDeviceType.equals("BLUETOOTH")) {
                    entries[i] = devList.get(i);
                    entryValues[i] = deviceInfo[1];
                } else {
                    String[] ids = deviceInfo[1].split(":");
                    String vid = Integer.toHexString(Integer.parseInt(ids[0]));
                    String pid = Integer.toHexString(Integer.parseInt(ids[1]));
                    vid = UtilityFunctions.addLeadingZeroes(vid, 4);
                    pid = UtilityFunctions.addLeadingZeroes(pid, 4);
                    entries[i] = deviceInfo[0] + "\nVID:0x" + vid + " PID:0x" + pid + "\n" + ids[2];
                    entryValues[i] = deviceInfo[1];
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