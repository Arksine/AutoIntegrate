package com.arksine.autointegrate.preferences;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.support.v4.content.LocalBroadcastManager;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.arksine.autointegrate.AutoIntegrate;
import com.arksine.autointegrate.activities.ButtonLearningActivity;
import com.arksine.autointegrate.adapters.AppListAdapter;
import com.arksine.autointegrate.dialogs.ListPreferenceEx;
import com.arksine.autointegrate.interfaces.MCUControlInterface;
import com.arksine.autointegrate.utilities.SerialHelper;
import com.arksine.autointegrate.R;
import com.arksine.autointegrate.utilities.AppItem;
import com.arksine.autointegrate.utilities.UtilityFunctions;
import com.arksine.autointegrate.utilities.BluetoothHelper;
import com.arksine.autointegrate.utilities.UsbHelper;
import com.orhanobut.dialogplus.DialogPlus;
import com.orhanobut.dialogplus.OnItemClickListener;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;

import timber.log.Timber;

/**
 * Preference Fragment containing Settings for Microcontroller Serial Connection
 */

public class MicroControllerSettings extends PreferenceFragment {


    SerialHelper mSerialHelper;
    private String mDeviceType;

    private DialogPlus mCameraAppDialog;
    private AtomicReference<MCUControlInterface> mMcuControlRef;


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

        mMcuControlRef = AutoIntegrate.getMcuInterfaceRef();

        // Load the microcontroller_preferences from an XML resource
        addPreferencesFromResource(R.xml.microcontroller_preferences);

        final SharedPreferences globalPrefs = PreferenceManager.getDefaultSharedPreferences(getActivity());

        PreferenceScreen root = this.getPreferenceScreen();
        PreferenceScreen editButtons = (PreferenceScreen) root.findPreference("controller_pref_key_edit_buttons");
        ListPreference selectDeviceType = (ListPreference) root.findPreference("controller_pref_key_select_device_type");
        ListPreference selectDevice = (ListPreference) root.findPreference("controller_pref_key_select_device");
        ListPreference selectBaudPref = (ListPreference) root.findPreference("controller_pref_key_select_baud");
        ListPreferenceEx cameraCmdPref = (ListPreferenceEx) root.findPreference("controller_pref_key_select_camera_app");

        buildAppListDialog(cameraCmdPref);

        mDeviceType = selectDeviceType.getValue();
        toggleBaudSelection();
        populateDeviceListView();

        selectBaudPref.setSummary(selectBaudPref.getEntry());


        if (cameraCmdPref.getValue().equals("2")) {
            String summary = globalPrefs
                    .getString("controller_pref_key_camera_ex_app_summary", "No Application Selected");
            cameraCmdPref.setSummary(summary);
        } else {
            cameraCmdPref.setSummary(cameraCmdPref.getEntry());
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
                preference.setSummary((String)newValue);
                MCUControlInterface controlInterface = mMcuControlRef.get();
                if (controlInterface != null) {
                    controlInterface.updateBaud(Integer.parseInt((String)newValue));
                }
                return true;
            }
        });


        editButtons.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                // Since the learning activity will reconnect the MicroController with new
                // settings, so there is no need to reconnect again when the fragment is stopped

                Context mContext = getActivity();
                Intent startIntent = new Intent(mContext, ButtonLearningActivity.class);
                mContext.startActivity(startIntent);

                return true;
            }
        });


        cameraCmdPref.setOnListItemClickListener(new ListPreferenceEx.ListItemClickListener() {
            @Override
            public void onListItemClick(Preference preference, String value) {
                if (value.equals("2")) {
                    mCameraAppDialog.show();
                }
            }
        });

        cameraCmdPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object o) {
                String value = (String)o;
                ListPreferenceEx pref = (ListPreferenceEx) preference;
                CharSequence[] entries = pref.getEntries();
                int index = pref.findIndexOfValue(value);

                switch (value) {
                    case "0":
                        pref.setSummary(entries[index]);
                        break;
                    case "1":
                        boolean camEnabled = globalPrefs.getBoolean("main_pref_key_toggle_camera", false);
                        if (camEnabled) {
                            pref.setSummary(entries[index]);
                        } else {
                            Toast.makeText(getActivity(), "Camera Integraton Not Enabled",
                                    Toast.LENGTH_SHORT).show();
                            return false;
                        }
                        break;
                    case "2":
                        // The dialog updates the reverse map, so return here
                        return true;
                }

                MCUControlInterface controlInterface = mMcuControlRef.get();
                if (controlInterface != null) {
                    controlInterface.updateReverseMap(value, "N/A");
                }

                return true;
            }
        });
    }

    @Override
    public void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter(getActivity().getString(R.string.ACTION_DEVICE_CHANGED));
        LocalBroadcastManager.getInstance(getActivity()).registerReceiver(deviceListReciever, filter);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

    }

    @Override
    public void onStop() {
        super.onStop();
        Timber.v("Activity Stopped");

        LocalBroadcastManager.getInstance(getActivity()).unregisterReceiver(deviceListReciever);
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

    private void buildAppListDialog(final ListPreferenceEx listPref) {
        final SharedPreferences globalPrefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
        // TODO: Rather than use default listholder, use custom viewholder with a filter
        //       implemented so user can type name of app
        int horizontalMargin = Math.round(getResources().getDimension(R.dimen.dialog_horizontal_margin));
        int verticalMargin = Math.round(getResources().getDimension(R.dimen.dialog_vertical_margin));
        final AppListAdapter adapter = new AppListAdapter(getActivity(),
                android.R.layout.simple_list_item_1, android.R.layout.simple_dropdown_item_1line);
        mCameraAppDialog = DialogPlus.newDialog(getActivity())
                .setAdapter(adapter)
                .setOnItemClickListener(new OnItemClickListener() {
                    @Override
                    public void onItemClick(DialogPlus dialog, Object item, View view, int position) {
                        AppItem appItem = (AppItem) item;
                        Timber.d("Camera App Set to: %s : %s",
                                appItem.getItemName(), appItem.getPackageName());
                        String summary = "Launch App: " + appItem.getItemName();
                        listPref.setSummary(summary);
                        globalPrefs.edit()
                                .putString("controller_pref_key_camera_ex_app", appItem.getPackageName())
                                .putString("controller_pref_key_camera_ex_app_summary", summary)
                                .apply();

                        // Update MCU CommandProcessor
                        MCUControlInterface controlInterface = mMcuControlRef.get();
                        if (controlInterface != null) {
                            controlInterface.updateReverseMap("2", appItem.getPackageName());
                        }
                        dialog.dismiss();
                    }
                })
                .setContentWidth(ViewGroup.LayoutParams.WRAP_CONTENT)
                .setContentHeight(ViewGroup.LayoutParams.WRAP_CONTENT)
                .setMargin(horizontalMargin, verticalMargin, horizontalMargin, verticalMargin)
                .setGravity(Gravity.CENTER)
                .create();
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

        ArrayList<String> devList = mSerialHelper.enumerateSerialDevices();

        CharSequence[] entries;
        CharSequence[] entryValues;

        if (devList == null || devList.isEmpty()) {
            Timber.i("No compatible devices found on system");
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

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        entries[i] = deviceInfo[0] + "\nVID:0x" + vid + " PID:0x" + pid + "\n" + ids[2];
                    } else {
                        entries[i] = deviceInfo[0] + "\nVID:0x" + vid + " PID:0x" + pid;
                    }
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