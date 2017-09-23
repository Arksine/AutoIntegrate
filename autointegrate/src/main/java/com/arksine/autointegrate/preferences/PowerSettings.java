package com.arksine.autointegrate.preferences;

import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.preference.SwitchPreference;

import com.arksine.autointegrate.R;
import com.arksine.autointegrate.power.IntegratedPowerManager;
import com.arksine.autointegrate.utilities.RootManager;
import com.arksine.autointegrate.utilities.UtilityFunctions;

/**
 * Controls settings that allow the service to react to power events, as well as special kernel
 * settings enabling USB Host Charging and Fixed Install Mode
 */

public class PowerSettings extends PreferenceFragment {

    private CheckBoxPreference mApMode;
    private SwitchPreference mFixedInstallMode;
    private SwitchPreference mFastChargeMode;

    private SwitchPreference.OnPreferenceChangeListener mFixedInstallListener
            = new Preference.OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference preference, Object o) {
            if ((boolean)o) {
                return IntegratedPowerManager.setFixedInstallMode(true);
            } else {
                return IntegratedPowerManager.setFixedInstallMode(false);
            }
        }
    };

    private SwitchPreference.OnPreferenceChangeListener mFastChargeListener
            = new Preference.OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference preference, Object o) {
            if ((boolean)o) {
                return IntegratedPowerManager.setFastchargeMode(true);
            } else {
                return IntegratedPowerManager.setFastchargeMode(false);
            }
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.power_preferences);

        PreferenceScreen root = this.getPreferenceScreen();
        mApMode = (CheckBoxPreference) root.findPreference("power_pref_key_use_airplane_mode");
        mFixedInstallMode = (SwitchPreference) root.findPreference("power_pref_key_fixed_install");
        mFastChargeMode = (SwitchPreference) root.findPreference("power_pref_key_fast_charging");

        mFixedInstallMode.setOnPreferenceChangeListener(mFixedInstallListener);
        mFastChargeMode.setOnPreferenceChangeListener(mFastChargeListener);
    }

    @Override
    public void onResume() {
        super.onResume();

        boolean rootAvailable = RootManager.isRootAvailable();
        if (RootManager.isInitialized()) {
            RootManager.RootCallback callback = new RootManager.RootCallback() {
                @Override
                public void OnRootInitialized(boolean rootStatus) {
                    initialize(rootStatus);
                }
            };
            RootManager.checkRootWithCallback(callback);
        } else {
            initialize(RootManager.isRootAvailable());
        }
    }

    private void initialize(boolean rootAvailable) {
        boolean signaturePermissionsAvailable = UtilityFunctions.hasSignaturePermission(getActivity());
        boolean kernelSettingsAvailable = IntegratedPowerManager.hasKernelUsbSettings();
        mApMode.setEnabled(rootAvailable || signaturePermissionsAvailable);

        // Initialize switch prefs to reflect current kernel settings
        if (rootAvailable && kernelSettingsAvailable) {
            mFixedInstallMode.setEnabled(true);
            mFixedInstallMode.setOnPreferenceChangeListener(null);
            mFixedInstallMode.setChecked(IntegratedPowerManager.isFixedInstallEnabled());
            mFixedInstallMode.setOnPreferenceChangeListener(mFixedInstallListener);

            mFastChargeMode.setEnabled(true);
            mFastChargeMode.setOnPreferenceChangeListener(null);
            mFastChargeMode.setChecked(IntegratedPowerManager.isFastChargeEnabled());
            mFastChargeMode.setOnPreferenceChangeListener(mFastChargeListener);
        } else {
            mFixedInstallMode.setEnabled(false);
            mFastChargeMode.setEnabled(false);
        }
    }
}
