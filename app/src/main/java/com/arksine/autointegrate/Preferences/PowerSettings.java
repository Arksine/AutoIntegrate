package com.arksine.autointegrate.preferences;

import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.preference.SwitchPreference;

import com.arksine.autointegrate.R;
import com.arksine.autointegrate.power.IntegratedPowerManager;

/**
 * Controls settings that allow the service to react to power events, as well as special kernel
 * settings enabling USB Host Charging and Fixed Install Mode
 */

public class PowerSettings extends PreferenceFragment {

    private static String TAG = "PowerSettings";

    CheckBoxPreference mApMode;
    SwitchPreference mFixedInstallMode;
    SwitchPreference mFastChargeMode;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.power_preferences);

        PreferenceScreen root = this.getPreferenceScreen();
        mApMode = (CheckBoxPreference) root.findPreference("power_pref_key_use_airplane_mode");
        mFixedInstallMode = (SwitchPreference) root.findPreference("power_pref_key_fixed_install");
        mFastChargeMode = (SwitchPreference) root.findPreference("power_pref_key_fast_charging");

        mFixedInstallMode.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object o) {
                if ((boolean)o) {
                    return IntegratedPowerManager.setFixedInstallMode(true);
                } else {
                    return IntegratedPowerManager.setFixedInstallMode(false);
                }
            }
        });

        mFastChargeMode.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object o) {
                if ((boolean)o) {
                    return IntegratedPowerManager.setFastchargeMode(true);
                } else {
                    return IntegratedPowerManager.setFastchargeMode(false);
                }
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();

        // TODO: If the service hasn't been started, these will return null.  Can I do something to
        //       work around it?
        boolean rootAvailable = IntegratedPowerManager.checkRootAvailable();
        boolean signaturePermissionsAvailable = IntegratedPowerManager.hasSignaturePermission(getActivity());
        mApMode.setEnabled(rootAvailable || signaturePermissionsAvailable);

        mFixedInstallMode.setEnabled(rootAvailable);
        mFastChargeMode.setEnabled(rootAvailable);
    }
}
