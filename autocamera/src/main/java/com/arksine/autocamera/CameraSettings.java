package com.arksine.autocamera;

import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;

import java.util.ArrayList;
import java.util.HashMap;

import timber.log.Timber;

/**
 * Manages lifecycle of the Integrated Camera Preference Fragment
 */

public class CameraSettings extends PreferenceFragment {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Timber.v("Camera Settings Launched");

        addPreferencesFromResource(R.xml.camera_preferences);

        populateDeviceList();

        PreferenceScreen root = this.getPreferenceScreen();
        ListPreference selectCameraPref = (ListPreference) root.findPreference("camera_pref_key_select_device");


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

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                Timber.d("Manufacturer: %s", uDevice.getManufacturerName());
                Timber.d("Serial Number: %s", uDevice.getSerialNumber());
            }

            // Check for UVC Device class values
            if (uDevice.getDeviceClass() == 239 && uDevice.getDeviceSubclass() == 2) {

                CharSequence name = "UVC Capture Device";
                CharSequence value;

                Timber.d("UVC Camera Device found:");
                Timber.d("Device ID: %d", uDevice.getDeviceId());
                Timber.d("Device Name: %s", uDevice.getDeviceName());
                Timber.d("Vendor ID: %#06x", uDevice.getVendorId());
                Timber.d("Product ID: %#06x", uDevice.getProductId());
                Timber.d("Class: %#06x", uDevice.getDeviceClass());
                Timber.d("SubClass: %#06x", uDevice.getDeviceSubclass());
                Timber.d("Protocol: %#06x", uDevice.getDeviceProtocol());

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    Timber.d("Manufacturer: %s", uDevice.getManufacturerName());
                    Timber.d("Serial Number: %s", uDevice.getSerialNumber());
                }


                String pid = String.format("%#06x", uDevice.getProductId());
                String vid = String.format("%#06x", uDevice.getVendorId());

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    name = String.format("%s\nVID:%s PID:%s\n%s", uDevice.getProductName(),
                            vid, pid, uDevice.getSerialNumber());
                    value = String.format("%s:%s:%s", uDevice.getVendorId(), uDevice.getProductId(),
                            uDevice.getSerialNumber());
                } else {
                    name = String.format("%s\nVID:%s PID:%s", name, vid, pid);
                    value = String.format("%s:%s", uDevice.getVendorId(), uDevice.getProductId());
                }

                entries.add(name);
                entryValues.add(value);
            }
        }

        if (entries.isEmpty()) {
            Timber.i("No compatible devices found on system");
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
