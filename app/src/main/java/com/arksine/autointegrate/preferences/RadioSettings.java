package com.arksine.autointegrate.preferences;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;

import com.arksine.autointegrate.AutoIntegrate;
import com.arksine.autointegrate.R;
import com.arksine.autointegrate.activities.RadioActivity;
import com.arksine.autointegrate.interfaces.ServiceControlInterface;

/**
 * Manages HD Radio Preferences
 */

public class RadioSettings extends PreferenceFragment {
    private static String TAG = "RadioSettings";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.radio_preferences);

        PreferenceScreen root = this.getPreferenceScreen();
        PreferenceScreen launchRadio = (PreferenceScreen) root.findPreference("radio_pref_key_launch_activity");
        ListPreference driverPref = (ListPreference) root.findPreference("radio_pref_key_select_driver");

        launchRadio.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Context context = getActivity();
                Intent launchRadioIntent = new Intent(context, RadioActivity.class);
                launchRadioIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(launchRadioIntent);
                return true;
            }
        });

        driverPref.setSummary(driverPref.getEntry());

        driverPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object o) {
                ListPreference pref = (ListPreference) preference;
                CharSequence[] entries = pref.getEntries();
                int index = pref.findIndexOfValue((String)o);
                preference.setSummary(entries[index]);

                ServiceControlInterface serviceControl = AutoIntegrate.getServiceControlInterface();
                if (serviceControl != null) {
                    serviceControl.refreshRadioConnection();
                }
                return true;
            }
        });

        // TODO: Implement other settings
    }
}
