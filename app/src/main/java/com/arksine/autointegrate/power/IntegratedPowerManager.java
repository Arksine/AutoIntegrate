package com.arksine.autointegrate.power;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.Process;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.view.KeyEvent;

import com.arksine.autointegrate.utilities.AdbManager;
import com.arksine.autointegrate.utilities.RootManager;
import com.arksine.autointegrate.utilities.UtilityFunctions;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;

import eu.chainfire.libsuperuser.Shell;
import timber.log.Timber;


/**
 * Handles Power management functionality (sleep, wake, etc).  Static functions read/write to
 * sysfs Kernel storage to enable Kernel specific functionality.
 */

public class IntegratedPowerManager {

    interface SystemFunctions {
        void turnOffScreen();
        void toggleAirplaneMode(boolean status);
    }

    private final static String USB_HOST_STATUS_FILE =
            "/sys/kernel/usbhost/usbhost_hostmode";
    private final static String FIXED_INSTALL_SETTINGS_FILE =
            "/sys/kernel/usbhost/usbhost_fixed_install_mode";
    private final static String FAST_CHARGE_SETTINGS_FILE =
            "/sys/kernel/usbhost/usbhost_fastcharge_in_host_mode";
    private final static String SETTING_ENABLED = "1";

    private Context mContext;
    private SharedPreferences mDefaultPrefs;
    private SystemFunctions mSystemFunctions = null;
    private PowerManager.WakeLock mScreenWakeLock = null;
    private Handler mPowerHandler = null;

    @SuppressWarnings("deprecation")
    public IntegratedPowerManager(Context context, boolean rootStatus){
        mContext = context;

        PowerManager pm = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
        mScreenWakeLock = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK
                        | PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "Screen On Wakelock");
        mScreenWakeLock.acquire();
        Timber.v("Wakelock Aquired");

        HandlerThread hThread = new HandlerThread("PowerHandlerThread");
        hThread.setPriority(Process.THREAD_PRIORITY_BACKGROUND);
        hThread.start();
        mPowerHandler = new Handler(hThread.getLooper());

        mDefaultPrefs = PreferenceManager.getDefaultSharedPreferences(mContext);

        initPrivilegedAccess(rootStatus);

        if (rootStatus) {
            checkKernelSettings();
        }
    }


    public void destroy() {
        if (mScreenWakeLock != null && mScreenWakeLock.isHeld()) {
            mScreenWakeLock.release();
            Timber.v("Wakelock Released");
        }

        mScreenWakeLock = null;
        restoreScreenTimeout();
    }

    private void initPrivilegedAccess(boolean rootStatus) {
        boolean hasSignaturePermission = UtilityFunctions.hasSignaturePermission(mContext);

        if (hasSignaturePermission) {
            Timber.i("Autointegrate has signature level permissions");
            // Signature level system functions
            mSystemFunctions = new SystemFunctions() {
                @Override
                public void turnOffScreen() {
                    Runnable timedSleep = new Runnable() {
                        @Override
                        public void run() {
                            iPowerManagerSleep();
                        }
                    };

                    long delay = mDefaultPrefs.getLong("power_pref_key_screen_timeout", 1000);
                    mPowerHandler.postDelayed(timedSleep, delay);
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

        } else if (rootStatus) {
            // Root system functions
            mSystemFunctions = new SystemFunctions() {
                @Override
                public void turnOffScreen() {
                    Runnable timedSleep = new Runnable() {
                        @Override
                        public void run() {
                            String command = "input keyevent " + String.valueOf(KeyEvent.KEYCODE_POWER);
                            RootManager.runCommand(command);
                        }
                    };

                    long delay = mDefaultPrefs.getLong("power_pref_key_screen_timeout", 1000);
                    mPowerHandler.postDelayed(timedSleep, delay);
                }

                @Override
                public void toggleAirplaneMode(boolean status) {
                    String value = status ? "1" : "0";
                    String[] commands = new String[2];
                    commands[0] = String.format(Locale.US,
                            "settings put global airplane_mode_on %s\n", value);
                    commands[1] = String.format(Locale.US,
                            "am broadcast -a android.intent.action.AIRPLANE_MODE --ez state %b",
                            status);

                    RootManager.SuFinishedCallback outCb = new RootManager.SuFinishedCallback() {
                        @Override
                        public void onSuComplete(List<String> output) {
                            for (String o: output) {
                                Timber.d("%s \n", o);
                            }
                        }
                    };
                    RootManager.runCommand(commands, outCb);

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

    /**
     * Check current kernel settings, update them if necessary
     */
    private void checkKernelSettings() {
        final boolean fixedInstall = mDefaultPrefs.getBoolean("power_pref_key_fast_charging" , false);
        final boolean fastCharge = mDefaultPrefs.getBoolean("power_pref_key_fixed_install", false);

        Thread checkKernelStatusThread = new Thread(new Runnable() {
            @Override
            public void run() {
                if (fixedInstall && !isFixedInstallEnabled()) {
                    Timber.d("Fixed install active on startup");
                    setFixedInstallMode(true);
                }

                if (fastCharge && !isFastChargeEnabled()) {
                    Timber.d("Fast charge active on startup");
                    setFastchargeMode(true);
                }
            }
        });
        checkKernelStatusThread.start();
    }

    // TODO: add black screen with countdown similar to Power event manager
    // This is blocking, do not call from UI thread
    public void goToSleep() {
        // TODO: attempt to toggle usbhost off
        if (mSystemFunctions == null) {
            Timber.w("Power System Functions not set.");
            return;
        }

        // release wakelocked keeping screen on
        if (mScreenWakeLock.isHeld()) {
            mScreenWakeLock.release();
            Timber.v("Wakelock Released");
        }

        long audioDelay = mDefaultPrefs.getLong("power_pref_key_audio_focus_timeout", 0);
        Runnable gainAudioFocus = new Runnable() {
            @Override
            public void run() {
                AudioFocusManager.requestAudioFocus(mContext);
            }
        };
        mPowerHandler.postDelayed(gainAudioFocus, audioDelay);


        if (mDefaultPrefs.getBoolean("power_pref_key_use_airplane_mode", false)) {
            mSystemFunctions.toggleAirplaneMode(true);
        }

        mSystemFunctions.turnOffScreen();

    }

    // This is blocking, do not call from UI thread
    public void wakeUp() {
        if (mSystemFunctions == null) {
            Timber.w("Power System Functions not set.");
            return;
        }

        // release wakelocked keeping screen on
        if (!mScreenWakeLock.isHeld()) {
            mScreenWakeLock.acquire();
            Timber.v("Wakelock Aquired");
        }

        AudioFocusManager.releaseAudioFocus(mContext);


        if (mDefaultPrefs.getBoolean("power_pref_key_use_airplane_mode", false)) {
            mSystemFunctions.toggleAirplaneMode(false);
        }

        // TODO: if wireless Adb is enabled, restart it?  Not sure if necessary yet
        /*AdbManager adbManager = AdbManager.getInstance(mContext);
        if (adbManager.isAdbEnabled(mContext)) {
            adbManager.restartAdb();
        }*/
    }



    private void setScreenTimeout() {

        int currentScreenTimeout = -1;
        try {
            currentScreenTimeout = Settings.System.getInt(mContext.getContentResolver(),
                    Settings.System.SCREEN_OFF_TIMEOUT);
        } catch (Settings.SettingNotFoundException e) {
            Timber.w(e);
        }

        // Only store the timeout if it hasn't been previously stored.  This keeps us from
        // overwriting our currently stored timeout if the service is closed without restoring it
        boolean storeTimeout = mDefaultPrefs.getBoolean("power_pref_store_screen_timeout_flag", false);

        // Store the previous screen timeout
        if (currentScreenTimeout != -1 && !storeTimeout) {
            mDefaultPrefs.edit()
                    .putInt("power_pref_store_screen_timeout_value", currentScreenTimeout)
                    .putBoolean("power_pref_store_screen_timeout_flag", true)
                    .apply();

            Timber.v("Current Screen Timeout: %d", currentScreenTimeout);
        }

        // Set Timeout to a minimal number.  The effect varies, because the timeout only occurs
        // after the system has decided that the device has no activity.
        Settings.System.putInt(mContext.getContentResolver(),
                Settings.System.SCREEN_OFF_TIMEOUT, 1000);
    }

    private void restoreScreenTimeout() {

        boolean storeTimeout = mDefaultPrefs.getBoolean("power_pref_store_screen_timeout_flag", false);

        // Only restore if the previous timeout has been stored
        if (storeTimeout) {
            mDefaultPrefs.edit()
                    .putBoolean("power_pref_store_screen_timeout_flag", false)
                    .apply();

            int timeout =  mDefaultPrefs.getInt("power_pref_store_screen_timeout_value", 15000);
            Settings.System.putInt(mContext.getContentResolver(),
                    Settings.System.SCREEN_OFF_TIMEOUT, timeout);

            Timber.v("Screen sleep timeout restored");
        }
    }

    /**
     * Use reflection to retreive IPowerManager service so we can access goToSleep function
     */
    private void iPowerManagerSleep() {
        try {
            Object iPowerManager;
            Class<?> ServiceManager = Class.forName("android.os.ServiceManager");
            Class<?> Stub = Class.forName("android.os.IPowerManager$Stub");

            Method getService = ServiceManager.getDeclaredMethod("getService", String.class);
            Method asInterface = Stub.getDeclaredMethod("asInterface", IBinder.class);
            getService.setAccessible(true);
            asInterface.setAccessible(true);
            IBinder iBinder = (IBinder) getService.invoke(null, Context.POWER_SERVICE);
            iPowerManager = asInterface.invoke(null, iBinder);

            // Kitkat version of goToSleep only has 2 parameters, Lollipop and above have 3
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                Method goToSleep = iPowerManager.getClass().getMethod("goToSleep", long.class, int.class, int.class);
                goToSleep.setAccessible(true);
                goToSleep.invoke(iPowerManager, SystemClock.uptimeMillis(), 0, 0);
            } else {
                Method goToSleep = iPowerManager.getClass().getMethod("goToSleep", long.class, int.class);
                goToSleep.setAccessible(true);
                goToSleep.invoke(iPowerManager, SystemClock.uptimeMillis(), 0);
            }

            Timber.v("Successfully put device to sleep using reflection");
        } catch (Exception e) {
            Timber.i("Unable to put device to sleep using reflection");
            Timber.w(e);
        }

    }

    /**
     * The functions below are Static Functions used to manipulate kernel settings.  This specifically
     * applies to Kernels for the Nexus 7 2012 (Kangaroo Kernel, DC Kernel).  It may also work
     * for Timur's Kernel.
     */

    public static boolean setFixedInstallMode(boolean enabled) {

        String num = enabled ? "1" : "0";
        final String command = "echo " + num + " > " + FIXED_INSTALL_SETTINGS_FILE;

        if (RootManager.isRootAvailable()) {
            RootManager.runCommand(command);
            return true;
        } else {
            Timber.w("Error setting fixed install mode, Root not available");
            return false;
        }
    }

    public static boolean setFastchargeMode(boolean enabled) {

        String num = enabled ? "1" : "0";
        final String command = "echo " + num + " > " + FAST_CHARGE_SETTINGS_FILE;

        if (RootManager.isRootAvailable()) {
            RootManager.runCommand(command);
            return true;
        } else {
            Timber.w("Error setting fast charge, Root not available");
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

    public static boolean hasKernelUsbSettings() {
        File fixedInstallFile = new File(FIXED_INSTALL_SETTINGS_FILE);
        File fastChargeFile = new File(FAST_CHARGE_SETTINGS_FILE);

        return fixedInstallFile.exists() && fastChargeFile.exists();
    }

    private static String getValueFromFile(String filePath) {
        File settingsFile = new File(filePath);
        if (settingsFile.exists()) {
            BufferedReader reader;
            try {
                reader = new BufferedReader(new FileReader(settingsFile));
            } catch (FileNotFoundException e) {
                Timber.w("File not found: %s", filePath);
                return "0";
            }

            String fileValue;
            try {
                fileValue = reader.readLine().trim();
            } catch (IOException e) {
                Timber.w("Error reading file: %s", filePath);
                fileValue = "0";
            }

            try {
                reader.close();
            } catch (IOException e) {
                Timber.e(e);
            }

            return fileValue;

        } else {
            Timber.w("File does not exist: %s", filePath);
            return "0";
        }
    }
 }
