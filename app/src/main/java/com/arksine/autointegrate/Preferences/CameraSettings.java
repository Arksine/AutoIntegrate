package com.arksine.autointegrate.preferences;

import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.util.Log;

import com.arksine.autointegrate.R;
import com.arksine.autointegrate.activities.CameraActivity;
import com.arksine.autointegrate.utilities.DLog;
import com.arksine.autointegrate.utilities.UtilityFunctions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Manages lifecycle of the Integrated Camera Preference Fragment
 */

public class CameraSettings extends PreferenceFragment {
    private final static String TAG = "CameraSettings";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.camera_preferences);

        populateDeviceList();

        PreferenceScreen root = this.getPreferenceScreen();
        ListPreference selectCameraPref = (ListPreference) root.findPreference("camera_pref_key_select_device");

        // Camera Launcher for testing
        final PreferenceScreen launchCamera = (PreferenceScreen) root.findPreference("camera_pref_key_launch_activity");
        launchCamera.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Context context = getActivity();
                Intent launchCameraIntent = new Intent(context, CameraActivity.class);
                launchCameraIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(launchCameraIntent);

                return true;
            }
        });

        selectCameraPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object o) {
                ListPreference list = (ListPreference)preference;
                CharSequence[] entries = list.getEntries();
                int index = list.findIndexOfValue((String)o);
                preference.setSummary(entries[index]);
                return true;
            }
        });

        //TODO; add more prefs
    }

    private void populateDeviceList() {
        PreferenceScreen root = this.getPreferenceScreen();
        ListPreference deviceListPref = (ListPreference) root.findPreference("camera_pref_key_select_device");

        ArrayList<CharSequence> entries = new ArrayList<>(5);
        ArrayList<CharSequence> entryValues = new ArrayList<>(5);
        UsbManager usbManager = (UsbManager) getActivity().getSystemService(Context.USB_SERVICE);
        HashMap<String, UsbDevice> usbDeviceList = usbManager.getDeviceList();

        for(UsbDevice uDevice : usbDeviceList.values()) {

            // Check for UVC Device class values
            if (uDevice.getDeviceClass() == 239 && uDevice.getDeviceSubclass() == 2) {

                CharSequence name = "UVC Capture Device";
                CharSequence value;

                DLog.v(TAG, "UVC Camera Device found: " + name);
                DLog.v(TAG, "Device ID: " + uDevice.getDeviceId());
                DLog.v(TAG, "Device Name: " + uDevice.getDeviceName());
                DLog.v(TAG, "Vendor: ID " + uDevice.getVendorId());
                DLog.v(TAG, "Product ID: " + uDevice.getProductId());
                DLog.v(TAG, "Class: " + uDevice.getDeviceClass());
                DLog.v(TAG, "SubClass: " + uDevice.getDeviceSubclass());
                DLog.v(TAG, "Protocol: " + uDevice.getDeviceProtocol());

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    DLog.v(TAG, "Manufacturer: " + uDevice.getManufacturerName());
                    DLog.v(TAG, "Serial Number: " + uDevice.getSerialNumber());
                }


                String pid = Integer.toHexString(uDevice.getProductId());
                String vid = Integer.toHexString(uDevice.getVendorId());
                vid = UtilityFunctions.addLeadingZeroes(vid, 4);
                pid = UtilityFunctions.addLeadingZeroes(pid, 4);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    name = uDevice.getProductName() + "\nVID:0x" + vid + " PID:0x" + pid + "\n"
                            + uDevice.getSerialNumber();
                    value = uDevice.getVendorId() + ":" + uDevice.getProductId() + ":"
                            + uDevice.getSerialNumber();
                } else {
                    name = name + "\nVID:0x" + vid + " PID:0x" + pid;
                    value = uDevice.getVendorId() + ":" + uDevice.getProductId();
                }

                entries.add(name);
                entryValues.add(value);
            }
        }

        if (entries.isEmpty()) {
            Log.i(TAG, "No compatible devices found on system");
            entries.add("No device found");
            entryValues.add("NO_DEVICE");

            deviceListPref.setValue("NO_DEVICE");
        }

        deviceListPref.setEntries(entries.toArray(new CharSequence[entries.size()]));
        deviceListPref.setEntryValues(entryValues.toArray(new CharSequence[entryValues.size()]));

        // if the currently stored value isn't in the new list, reset the summary
        int index = deviceListPref.findIndexOfValue(deviceListPref.getValue());
        if (index == -1) {
            deviceListPref.setSummary("No Device Selected");
        }
        else {
            deviceListPref.setSummary(deviceListPref.getEntry());
        }
    }

}
