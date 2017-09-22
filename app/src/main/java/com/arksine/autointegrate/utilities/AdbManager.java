package com.arksine.autointegrate.utilities;

import android.content.Context;
import android.provider.Settings;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;

import eu.chainfire.libsuperuser.Shell;
import timber.log.Timber;

/**
 * Allows basic interaction with Adb, such as toggling it on/off, and toggling
 * wireless ADB
 */

public class AdbManager {
    /**
     * TODO: I can't restart the ADB Daemon without root access, so it doesn't make much
     * sense to use Signature Access methods.  Signature Access allows me to toggle
     * the ADB Setting on/off, but without a restart the wireless ADB setting isn't
     * retained
     */


    private static final Object INSTANCE_LOCK = new Object();
    private static final String ADB_WIRELESS_PROP = "service.adb.tcp.port";

    private enum AccessType {STANDARD, SIGNATURE, ROOT};

    private interface PropertyFunctions {
        String getProperty(String key);
        void setProperty(String key, String value);
        void toggleAdb(boolean status);
    }

    private static AdbManager mAdbManager = null;

    private boolean mWirelessEnabled;
    private AccessType mAccessType;
    private PropertyFunctions mPropertyFuncs;
    //private Class<?> mSystemProperties;
    //private Method mSysPropGet;
    //private Method mSysPropSet;

    private AdbManager(final Context context) {

        // TODO: Signature Set property isn't working, even with a system signed apk. Am I missing a
        // permission?  Is it because I am saving the method as a class member?
        // Regardless, I can't restart ADB without root, so I am commenting
        // out the option until I can figure out a way to make everything work with signature
        // permission, but without root
        /*if (UtilityFunctions.hasSignaturePermission(context)) {
            try {
                mSystemProperties = Class.forName("android.os.SystemProperties");
                mSysPropGet = mSystemProperties.getDeclaredMethod("get", String.class);
                mSysPropSet = mSystemProperties.getDeclaredMethod("set", String.class, String.class);

                mPropertyFuncs = new PropertyFunctions() {
                    @Override
                    public String getProperty(String key) {
                        String value ;
                        try {
                            value = (String)mSysPropGet.invoke(mSystemProperties, key);
                        } catch (Exception e) {
                            Timber.e(e);
                            value = null;
                        }
                        return value;
                    }

                    // TODO: Set property not working for signature permissions
                    @Override
                    public void setProperty(String key, String value) {
                        try {
                            mSysPropSet.invoke(mSystemProperties, key, value);
                        } catch (Exception e) {
                            Timber.e(e);
                        }
                    }

                    @Override
                    public void toggleAdb(boolean status) {
                        int value = status ? 1 : 0;
                        Settings.Global.putInt(context.getContentResolver(),
                                Settings.Global.ADB_ENABLED, value);

                    }
                };
                mAccessType = AccessType.SIGNATURE;
            } catch (Exception e) {
                Timber.i(e);
                mPropertyFuncs = new PropertyFunctions() {
                    @Override
                    public String getProperty(String key) {
                        return getPropertyFromShell(key);
                    }

                    @Override
                    public void setProperty(String key, String value) {}

                    @Override
                    public void toggleAdb(boolean status) {}
                };
                mAccessType = AccessType.STANDARD;
            }

        } else*/ if (RootManager.isRootAvailable()) {
            // TODO: root not initialized in time
            mPropertyFuncs = new PropertyFunctions() {
                @Override
                public String getProperty(String key) {
                    return getPropertyFromShell(key);
                }

                @Override
                public void setProperty(String key, String value) {
                    String command = String.format(Locale.US, "setprop %s %s", key, value);
                    RootManager.runCommand(command);
                }

                @Override
                public void toggleAdb(boolean status) {
                    String value = status ? "1" : "0";
                    String cmd = String.format(Locale.US,
                            "settings put global adb_enabled %s\n", value);

                    RootManager.SuFinishedCallback outCb = new RootManager.SuFinishedCallback() {
                        @Override
                        public void onSuComplete(List<String> output) {
                            for (String o : output) {
                                Timber.d(o);
                            }
                        }
                    };

                    RootManager.runCommand(cmd, outCb);

                }
            };
            mAccessType = AccessType.ROOT;
        } else {
            // Error, cannot set properties without signature or root level permissions
            mPropertyFuncs = new PropertyFunctions() {
                @Override
                public String getProperty(String key) {
                    return getPropertyFromShell(key);
                }

                @Override
                public void setProperty(String key, String value) {}

                @Override
                public void toggleAdb(boolean status) {}
            };
            mAccessType = AccessType.STANDARD;
        }

        Timber.v("ADB Access Type: %s", mAccessType.toString());
        mWirelessEnabled = isWirelessAdbEnabled();

    }

    public static AdbManager getInstance(Context context) {
        synchronized (INSTANCE_LOCK) {
            if (mAdbManager == null) {
                mAdbManager = new AdbManager(context);
            }
        }

        return mAdbManager;
    }

    private String getPropertyFromShell(String key) {
        String command = String.format(Locale.US, "getprop %s", key);
        List<String> output = Shell.SH.run(command);

        if (output.isEmpty()) {
            Timber.i("Property output is empty");
            return null;
        } else {
            if (output.size() > 1) {
                Timber.i("Output list is greater than expected");
                for (String o : output) {
                    Timber.i(o);
                }
            }

            String value = output.get(0);
            return value.trim();
        }
    }

    public boolean isAdbEnabled(Context context) {
        int status = 0;
        try {
            status = Settings.Global.getInt(context.getContentResolver(),
                    Settings.Global.ADB_ENABLED);
        } catch (Settings.SettingNotFoundException e) {
            Timber.i(e);
        }

        Timber.v("Adb Enabled Status: %b", status);

        return (status != 0);

    }

    public boolean isWirelessAdbEnabled() {
        String status = mPropertyFuncs.getProperty(ADB_WIRELESS_PROP);

        boolean wirelessEnabled;
        if (status == null) {
            Timber.i("Unable to get Adb Wireless status, getProperty returned null");
            wirelessEnabled = false;
        } else if (status.equals("-1")) {
                Timber.v("Adb Wireless is disabled");
            wirelessEnabled = false;
        } else if (UtilityFunctions.isInteger(status)) {
            Timber.v("Adb Wireless is enabled on port %s", status);
            wirelessEnabled = true;
        } else {
            Timber.w("Error, incorrect property data: %s", status);
            wirelessEnabled = false;
        }

        return wirelessEnabled;
    }



    public void toggleAdb(boolean status, Context context) {
        if (status != isAdbEnabled(context)) {
            mPropertyFuncs.toggleAdb(status);

        } else {
            Timber.d("Adb already set to %b", status);
        }
    }

    public void toggleWirelessAdb(boolean status) {
        // TODO: Instead of using a boolean, I could set a port (or have the option to set it)
        if (status != isWirelessAdbEnabled()) {
            String value = status ? "5555" : "-1";
            mPropertyFuncs.setProperty(ADB_WIRELESS_PROP, value);
            mWirelessEnabled = status;

            restartAdb();
        }
    }

    /**
     *  REQUIRES ROOT
     *  Restarts the Adb Daemon
     */
    public void restartAdb() {

        if (mAccessType == AccessType.ROOT || RootManager.isRootAvailable()) {
            String[] commands = new String[2];
            commands[0] = "stop adbd\n";
            commands[1] = "start adbd";
            RootManager.runCommand(commands);
        } else {
            Timber.w("No root permissions, cannot restart ADB");
        }
    }

    /**
     *  TODO: Doesn't work  I could try toggling ADB off, changing the setting, delaying,
     *  and toggling back on
     *
     * Toggles the adb_enabled setting OFF, waits for the system to shut down
     * the ADB Daemon, then toggles it back on.  This is the only way to restart
     * adbd without root access (and relies on signature access)
     */
    private void adbDelayedRestart() {
        Thread restartThread = new Thread(new Runnable() {
            @Override
            public void run() {


                mPropertyFuncs.toggleAdb(false);

                int delayCount = 0;
                boolean adbRunning = isAdbdRunning();

                // Loop in 50 ms increments until either adbd is shut down or
                // 5 seconds has been reached
                while (adbRunning && delayCount < 100) {
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        Timber.i(e);
                    }
                    adbRunning = isAdbdRunning();
                    delayCount++;
                }



                if (!adbRunning) {
                    Timber.d("Delayed ADB Wireless Toggle");
                    mPropertyFuncs.toggleAdb(true);
                } else {
                    Timber.d("Error, the adbd daemon did not shut down");
                }
            }
        });
        restartThread.start();
    }

    private boolean isAdbdRunning() {
        List<String> out = Shell.SH.run("ps");

        for (String o : out) {
            if (o.contains("adbd")) {
                return true;
            }
        }

        return false;
    }


}
