package com.arksine.autointegrate.preferences;

import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.preference.SwitchPreference;

import com.arksine.autointegrate.R;
import com.arksine.autointegrate.power.IntegratedPowerManager;
import com.arksine.autointegrate.utilities.UtilityFunctions;

/**
 * Controls settings that allow the service to react to power events, as well as special kernel
 * settings enabling USB Host Charging and Fixed Install Mode
 */

public class PowerSettings extends PreferenceFragment {

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

        Boolean rootAvailable = UtilityFunctions.isRootAvailable();
        if (rootAvailable == null) {
            UtilityFunctions.RootCallback callback = new UtilityFunctions.RootCallback() {
                @Override
                public void OnRootInitialized(boolean rootStatus) {
                    initialize(rootStatus);
                }
            };
            UtilityFunctions.initRoot(callback);
        } else {
            initialize(rootAvailable);
        }

    }

    private void initialize(boolean rootAvailable) {
        boolean signaturePermissionsAvailable = UtilityFunctions.hasSignaturePermission(getActivity());
        mApMode.setEnabled(rootAvailable || signaturePermissionsAvailable);

        mFixedInstallMode.setEnabled(rootAvailable);
        mFastChargeMode.setEnabled(rootAvailable);
    }
}
