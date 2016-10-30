package com.arksine.autointegrate.power;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.KeyEvent;

import com.arksine.autointegrate.R;
import com.arksine.autointegrate.utilities.UtilityFunctions;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import eu.chainfire.libsuperuser.Shell;

//TODO: This class doesn't need to be full of static functions.  It can be instantiated in the
//      Service thread.  The Power Event receiver would just broadcast the wake and suspend
//      event rather than call functions in this class directly
//      The only functions that need to be static are the Nexus Specific
//      Functions that set and get values written to files.  It would require a broadcast reciever
//      to know when the main power manager setting is toggled

/**
 * Static class handling kernel specific power commands for Nexus 2012 devices with Kangaroo or DC
 * Kernels.
 */

public class IntegratedPowerManager {

    interface SystemFunctions {
        void turnOffScreen();
        void toggleAirplaneMode(boolean status);
    }

    private final static String TAG = "IntegratedPowerManager";

    private final static String USB_HOST_STATUS_FILE =
            "/sys/kernel/usbhost/usbhost_hostmode";
    private final static String FIXED_INSTALL_SETTINGS_FILE =
            "/sys/kernel/usbhost/usbhost_fixed_install_mode";
    private final static String FAST_CHARGE_SETTINGS_FILE =
            "/sys/kernel/usbhost/usbhost_fastcharge_in_host_mode";
    private final static String SETTING_ENABLED = "1";

    private final static String DEVICE_POWER_PERMISSION =
            "android.permission.DEVICE_POWER";
    private final static String WRITE_SECURE_SETTINGS_PERMISSION =
            "android.permission.WRITE_SECURE_SETTINGS";

    private Context mContext;
    private SystemFunctions mSystemFunctions;
    private PowerManager.WakeLock mScreenWakeLock = null;

    private static volatile Boolean mIsRootAvailable = null;




    public IntegratedPowerManager(Context context){
        mContext = context;

        PowerManager pm = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
        mScreenWakeLock = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK
                        | PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "Screen On Wakelock");
        mScreenWakeLock.acquire();
        Log.i(TAG, "Wakelock Aquired");
        checkRootAvailable();

    }


    public void destroy() {
        if (mScreenWakeLock != null && mScreenWakeLock.isHeld()) {
            mScreenWakeLock.release();
            Log.i(TAG, "Wakelock Released");
        }

        mScreenWakeLock = null;
        restoreScreenTimeout();
        mIsRootAvailable = null;
    }

    private static boolean isRootInitialized() {
        return (mIsRootAvailable != null);
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

    private void checkRootAvailable() {
        Thread checkSUthread = new Thread(new Runnable() {
            @Override
            public void run() {
                mIsRootAvailable = Shell.SU.available();
                Log.i(TAG,"Root availability status: " + mIsRootAvailable);

                if (UtilityFunctions.hasPermission(mContext, DEVICE_POWER_PERMISSION) &&
                    UtilityFunctions.hasPermission(mContext, WRITE_SECURE_SETTINGS_PERMISSION)) {
                    Log.i(TAG, "Device has signature level permissions");
                    // Signature level system functions
                    mSystemFunctions = new SystemFunctions() {
                        @Override
                        public void turnOffScreen() {
                            Timer timer = new Timer();
                            TimerTask timedSleep = new TimerTask() {
                                @Override
                                public void run() {
                                    iPowerManagerSleep();
                                }
                            };
                            //TODO: retreive time delay from settings
                            timer.schedule(timedSleep, 2000);
                        }

                        @Override
                        public void toggleAirplaneMode(boolean status) {
                            int value = status ? 1 : 0;
                            Settings.Global.putInt(mContext.getContentResolver(),
                                    Settings.Global.AIRPLANE_MODE_ON, value);

                            Intent airplaneModeIntent = new Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED);
                            airplaneModeIntent.putExtra("state", status);
                            mContext.sendBroadcast(airplaneModeIntent);
                        }
                    };

                } else if (mIsRootAvailable) {
                    // Root system functions
                    mSystemFunctions = new SystemFunctions() {
                        @Override
                        public void turnOffScreen() {
                            Timer timer = new Timer();
                            TimerTask timedSleep = new TimerTask() {
                                @Override
                                public void run() {
                                    String command = "input keyevent " + String.valueOf(KeyEvent.KEYCODE_POWER);
                                    Shell.SU.run(command);
                                }
                            };
                            //TODO: retreive time delay from settings
                            timer.schedule(timedSleep, 2000);
                        }

                        @Override
                        public void toggleAirplaneMode(boolean status) {
                            String value = status ? "1" : "0";
                            String[] commands = new String[2];
                            commands[0] = ("settings put global airplane_mode_on " + value + "\n");
                            commands[1] = ("am broadcast -a android.intent.action.AIRPLANE_MODE --ez state "
                                    + String.valueOf(status));

                            List<String> output = Shell.SU.run(commands);
                            for (String o: output) {
                                Log.i(TAG, o + "\n");
                            }
                        }
                    };
                } else {
                    /**
                     * Neither Signature level permissions nor root available.  Screen timeout will
                     * be set to minimum so the screen times out after the wakelock is released.
                     * Initial screen timeout will be stored and returned to its original state
                     * when service is shut down.
                     */

                    setScreenTimeout();

                    mSystemFunctions = new SystemFunctions() {
                        @Override
                        public void turnOffScreen() {}

                        @Override
                        public void toggleAirplaneMode(boolean status) {}
                    };
                }
            }
        });
        checkSUthread.start();
    }


    public static boolean setFixedInstallMode(boolean enabled) {
        if (!isRootInitialized()) {
            Log.i(TAG, "Error, SU not initialized");
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
        if (!isRootInitialized()) {
            Log.i(TAG, "Error, SU not initialized");
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

    public static boolean isUsbHostEnabled() {
        String fileValue = getValueFromFile(USB_HOST_STATUS_FILE);
        return fileValue.equals(SETTING_ENABLED);
    }


    public static boolean isFixedInstallEnabled() {
        String fileValue = getValueFromFile(FIXED_INSTALL_SETTINGS_FILE);
        return fileValue.equals(SETTING_ENABLED);
    }

    public static boolean isFastChargeEnabled() {
        String fileValue = getValueFromFile(FAST_CHARGE_SETTINGS_FILE);
        return fileValue.equals(SETTING_ENABLED);
    }

    // This is blocking, do not call from UI thread
    public void goToSleep() {

        // release wakelocked keeping screen on
        if (mScreenWakeLock.isHeld()) {
            mScreenWakeLock.release();
            Log.i(TAG, "Wakelock Released");
        }



        AudioFocusManager.requestAudioFocus(mContext);

        mSystemFunctions.toggleAirplaneMode(true);
        mSystemFunctions.turnOffScreen();

    }

    // This is blocking, do not call from UI thread
    public void wakeUp() {

        // release wakelocked keeping screen on
        if (!mScreenWakeLock.isHeld()) {
            mScreenWakeLock.acquire();
            Log.i(TAG, "Wakelock Aquired");
        }

        AudioFocusManager.releaseAudioFocus(mContext);


        mSystemFunctions.toggleAirplaneMode(false);


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

    private void setScreenTimeout() {
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(mContext);

        int currentScreenTimeout = -1;
        try {
            currentScreenTimeout = Settings.System.getInt(mContext.getContentResolver(),
                    Settings.System.SCREEN_OFF_TIMEOUT);
        } catch (Settings.SettingNotFoundException e) {
            e.printStackTrace();
        }

        // Only store the timeout if it hasn't been previously stored.  This keeps us from
        // overwriting our currently stored timeout if the service is closed without restoring it
        boolean storeTimeout = sharedPrefs.getBoolean("power_pref_store_screen_timeout_flag", false);

        // Store the previous screen timeout
        if (currentScreenTimeout != -1 && !storeTimeout) {
            sharedPrefs.edit()
                    .putInt("power_pref_store_screen_timeout_value", currentScreenTimeout)
                    .putBoolean("power_pref_store_screen_timeout_flag", true)
                    .apply();

            Log.i(TAG, "Current Screen Timeout: " + currentScreenTimeout);
        }

        //TODO: retreive timeout from settings...This doesn't work great, takes about
        //      10 seconds to timeout (atleast on api 23).  Maybe there is another method I could
        //      try that doesn't require root?
        Settings.System.putInt(mContext.getContentResolver(),
                Settings.System.SCREEN_OFF_TIMEOUT, 1000);
    }

    private void restoreScreenTimeout() {
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(mContext);

        boolean storeTimeout = sharedPrefs.getBoolean("power_pref_store_screen_timeout_flag", false);

        // Only restore if the previous timeout has been stored
        if (storeTimeout) {
            sharedPrefs.edit()
                    .putBoolean("power_pref_store_screen_timeout_flag", false)
                    .apply();

            int timeout =  sharedPrefs.getInt("power_pref_store_screen_timeout_value", 15000);
            Settings.System.putInt(mContext.getContentResolver(),
                    Settings.System.SCREEN_OFF_TIMEOUT, timeout);

            Log.i(TAG, "Screen timeout restored");
        }
    }

    /**
     * Use reflection to retreive IPowerManager service so we can access goToSleep function
     */
    private void iPowerManagerSleep() {
        try {
            Object iPowerManger;
            Class<?> ServiceManager = Class.forName("android.os.ServiceManager");
            Class<?> Stub = Class.forName("android.os.IPowerManager$Stub");

            Method getService = ServiceManager.getDeclaredMethod("getService", String.class);
            Method asInterface = Stub.getDeclaredMethod("asInterface", IBinder.class);
            getService.setAccessible(true);
            asInterface.setAccessible(true);
            IBinder iBinder = (IBinder) getService.invoke(null, Context.POWER_SERVICE);
            iPowerManger = asInterface.invoke(null, iBinder);

            // Kitkat version of goToSleep only has 2 parameters, Lollipop and above have 3
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                Method goToSleep = iPowerManger.getClass().getMethod("goToSleep", long.class, int.class, int.class);
                goToSleep.setAccessible(true);
                // TODO: is this time correct
                goToSleep.invoke(iPowerManger, SystemClock.uptimeMillis(), 0, 0);
            } else {
                Method goToSleep = iPowerManger.getClass().getMethod("goToSleep", long.class, int.class);
                goToSleep.setAccessible(true);
                // TODO: is this time correct
                goToSleep.invoke(iPowerManger, SystemClock.uptimeMillis(), 0);
            }



            Log.i(TAG, "Successfully put device to sleep using reflection");
        } catch (Exception e) {
            Log.i(TAG, "Unable to put device to sleep using reflection");
            e.printStackTrace();
        }

    }

 }
