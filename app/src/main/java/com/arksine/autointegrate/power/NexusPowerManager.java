package com.arksine.autointegrate.power;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.BatteryManager;
import android.os.ParcelUuid;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.KeyEvent;

import com.arksine.autointegrate.R;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

import eu.chainfire.libsuperuser.Shell;

/**
 * Static class handling kernel specific power commands for Nexus 2012 devices with Kangaroo or DC
 * Kernels.
 */

public class NexusPowerManager {

    private final static String TAG = "NexusPowerManager";

    private final static String FIXED_INSTALL_SETTINGS_FILE =
            "/sys/kernel/usbhost/usbhost_fixed_install_mode";
    private final static String FAST_CHARGE_SETTINGS_FILE =
            "/sys/kernel/usbhost/usbhost_fastcharge_in_host_mode";
    private final static String SETTING_ENABLED = "1";

    private static volatile Boolean mIsRootAvailable = null;
    private static PowerManager.WakeLock screenWakeLock = null;

    private NexusPowerManager(){}

    public static void initialize(Context context) {
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        screenWakeLock = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK,
                "Screen On Wakelock");

        checkRootAvailable();
    }

    /**
     * TODO:  Need to use su (libsuperuser) to echo 1 to the following files:
     * /sys/kernel/usbhost/usbhost_fixed_install_mode               Turns on fixed install mode, a necessary prereq to fast charge
     * /sys/kernel/usbhost/usbhost_fastcharge_in_host_mode          Turns on fast charging
     *
     * It would be a good idea to implement getter functions to read a 1 or 0 to see if the above are enabled.
     *
     * The following files are changed by PowerEventMangager, but I don't believe they are implemented
     * in the DC Kernel
     * /sys/kernel/usbhost/usbhost_firm_sleep
     * /sys/kernel/usbhost/usbhost_lock_usbdisk
     *
     * Will also need a Broadcast Receiver to receive power events and react when power is attached
     * and detached.  This will be another class so I can declare it in the manifest.
     *
     * I need to start the service at boot (or atleast do it delayed), this may not
     *  be something implemented though powermanager though.
     *
     *  On power removal the main service thread should be stopped, the device should go into Airplance
     *  mode, and wakelocks need to be removed.  Anything that uses power should be disabled if possible
     *
     *  On power restoration the opposite should happen.  When fixed install mode is on this class
     *  should request and hold a wakelock.
     */

    private static void checkRootAvailable() {
        Thread checkSUthread = new Thread(new Runnable() {
            @Override
            public void run() {
                mIsRootAvailable = Shell.SU.available();
                Log.i(TAG,"Root availability status: " + mIsRootAvailable);
            }
        });
        checkSUthread.start();
    }


    public static boolean setFixedInstallMode(boolean enabled) {
        if (mIsRootAvailable == null) {
            Log.i(TAG, "Error, Power Manager not initialized");
            return false;
        }

        String num = enabled ? "1" : "0";
        final String command = "echo " + num + " > " + FIXED_INSTALL_SETTINGS_FILE;

        if (mIsRootAvailable) {
            Thread rootThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    Shell.SU.run(command);
                }
            });
            rootThread.start();

            return true;
        } else {
            Log.e(TAG, "Error setting fixed install mode, Root not available");
            return false;
        }
    }

    public static boolean setFastchargeMode(boolean enabled) {
        if (mIsRootAvailable == null) {
            Log.i(TAG, "Error, Power Manager not initialized");
            return false;
        }

        if (!isFixedInstallEnabled()) {
            Log.e(TAG, "Error setting fast charge, Usbhost not enabled");
            return false;
        }

        String num = enabled ? "1" : "0";
        final String command = "echo " + num + " > " + FAST_CHARGE_SETTINGS_FILE;

        if (mIsRootAvailable) {
            Thread rootThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    Shell.SU.run(command);
                }
            });
            rootThread.start();
            return true;
        } else {
            Log.e(TAG, "Error setting fast charge, Root not available");
            return false;
        }
    }


    public static boolean isFixedInstallEnabled() {
        String fileValue = getValueFromFile(FIXED_INSTALL_SETTINGS_FILE);
        return fileValue.equals(SETTING_ENABLED);
    }

    public static boolean isFastChargeEnabled() {
        String fileValue = getValueFromFile(FAST_CHARGE_SETTINGS_FILE);
        return fileValue.equals(SETTING_ENABLED);
    }

    public static void keepScreenOn() {
        if (screenWakeLock == null) {
            Log.i(TAG, "Error, Power Manager not initialized");
            return;
        }

        screenWakeLock.acquire();

    }

    public static void allowScreenOff() {
        if (screenWakeLock == null) {
            Log.i(TAG, "Error, Power Manager not initialized");
            return;
        }

        screenWakeLock.release();
    }

    /**
     * Makes sure that the screen stays on when charging.  This should make sure the screen
     * wakes up regardless.  It should be called whenever power preferences are toggled on.
     */
    // TODO: I probably cant do this.  Might just need to acquire a screen_dim wakelock
    public static void setScreenStayOnPref(@NonNull Context context) {

        SharedPreferences defaultPrefs = PreferenceManager.getDefaultSharedPreferences(context);

        // Store the original power preferences if it hasn't been changed
        if (defaultPrefs.getBoolean("power_pref_store_stay_on", true)) {
            defaultPrefs.edit().putBoolean("power_pref_store_stay_on", false).apply();
            int origStayOnPref;
            try {
                origStayOnPref = Settings.Global.getInt(context.getContentResolver(),
                        Settings.Global.STAY_ON_WHILE_PLUGGED_IN);
            } catch (Settings.SettingNotFoundException e) {
                origStayOnPref = 0;
            }

            defaultPrefs.edit().putInt("power_pref_stay_on_value", origStayOnPref).apply();
        }

        int newStayOnPref = BatteryManager.BATTERY_PLUGGED_AC | BatteryManager.BATTERY_PLUGGED_USB;
        Settings.Global.putInt(context.getContentResolver(),
                Settings.Global.STAY_ON_WHILE_PLUGGED_IN,
                newStayOnPref);
    }

    public static void restoreOriginalStayOnPref(@NonNull Context context) {

        SharedPreferences defaultPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        int origStayOnPref = defaultPrefs.getInt("power_pref_stay_on_value", -1);
        if (origStayOnPref == -1) {
            Log.i(TAG, "No original preference stored for " +
                    Settings.Global.STAY_ON_WHILE_PLUGGED_IN);
        } else {
            defaultPrefs.edit().putBoolean("power_pref_store_stay_on", true).apply();
            Settings.Global.putInt(context.getContentResolver(),
                    Settings.Global.STAY_ON_WHILE_PLUGGED_IN,
                    origStayOnPref);
        }

    }

    public static void goToSleep(@NonNull Context context) {

        // Suspend service thread
        Intent suspendSvcThrdIntent = new Intent(context.getString(R.string.ACTION_SUSPEND_SERVICE_THREAD));
        LocalBroadcastManager.getInstance(context).sendBroadcast(suspendSvcThrdIntent);

        // TODO: put in airplane mode, stop all music (getaudiofocus),


        if (mIsRootAvailable) {
            Timer timer = new Timer();
            TimerTask timedSleep = new TimerTask() {
                @Override
                public void run() {
                    String command = "input keyevent " + String.valueOf(KeyEvent.KEYCODE_POWER);
                    Shell.SU.run(command);
                }
            };
            //TODO: retreive time delay from settings
            timer.schedule(timedSleep, 1000);
        } else {
            int currentScreenTimeout = -1;
            try {
                currentScreenTimeout = Settings.System.getInt(context.getContentResolver(),
                        Settings.System.SCREEN_OFF_TIMEOUT);
            } catch (Settings.SettingNotFoundException e) {
                e.printStackTrace();
            }

            // Store the previous screen timeout
            if (currentScreenTimeout != -1) {
                PreferenceManager.getDefaultSharedPreferences(context).edit()
                        .putInt("power_pref_store_screen_timeout_value", currentScreenTimeout)
                        .apply();
            }

            //TODO: retreive timeout from settings
            Settings.System.putInt(context.getContentResolver(),
                    Settings.System.SCREEN_OFF_TIMEOUT, 1000);
        }

    }

    public static void wakeUp(@NonNull Context context) {
        // TODO: release airplanemode, release audiofocus, start servicethread,

    }

    private static String getValueFromFile(String filePath) {
        File settingsFile = new File(filePath);
        if (settingsFile.exists()) {
            BufferedReader reader;
            try {
                reader = new BufferedReader(new FileReader(settingsFile));
            } catch (FileNotFoundException e) {
                Log.e(TAG, "File not found: " + filePath);
                return "0";
            }

            String fileValue;
            try {
                fileValue = reader.readLine().trim();
            } catch (IOException e) {
                Log.e(TAG, "Error reading file: " + filePath);
                fileValue = "0";
            }

            try {
                reader.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

            return fileValue;

        } else {
            Log.e(TAG, "File does not exist: " + filePath);
            return "0";
        }
    }
 }
