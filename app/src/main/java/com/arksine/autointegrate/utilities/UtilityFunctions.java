package com.arksine.autointegrate.utilities;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

import eu.chainfire.libsuperuser.Shell;

/**
 * Static class for various helper functions used throughout service / application
 */

public class UtilityFunctions {

    private final static String TAG = "UtilityFunctions";
    private final static Object ROOTLOCK = new Object();

    private final static String DEVICE_POWER_PERMISSION =
            "android.permission.DEVICE_POWER";
    private final static String WRITE_SECURE_SETTINGS_PERMISSION =
            "android.permission.WRITE_SECURE_SETTINGS";

    private static volatile Boolean mIsRootAvailable = null;
    public interface RootCallback {
        void OnRootInitialized(boolean rootStatus);
    }

    private static volatile List<AppItem> mAppItems = null;

    // Empty private constructor so class cannot be instantiated
    private UtilityFunctions() {}

    public static boolean isServiceRunning(Class<?> serviceClass, Context context) {
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    public static String addLeadingZeroes(String str, int targetLength) {
        int difference = targetLength - str.length();

        for(int i = 0; i < difference; i++) {
            str = "0" + str;
        }

        return str;
    }

    public static boolean checkSettingsPermission(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.System.canWrite(context)) {
                Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_WRITE_SETTINGS);
                intent.setData(Uri.parse("package:" + context.getPackageName()));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);

                return false;
            }
        }

        return true;
    }

    /**
     * Since some functionality relies on system permissions, check if a particular permission is
     * granted
     */
    public static boolean hasPermission(Context context, String permission) {
        int permissionStatus = context.getPackageManager()
                .checkPermission(permission, context.getPackageName());

        return (permissionStatus == PackageManager.PERMISSION_GRANTED);

    }

    public static void initAppList(final Context context) {

        Thread appListThread = new Thread(new Runnable() {
            @Override
            public void run() {
                PackageManager pm = context.getPackageManager();
                List<ApplicationInfo> apps = pm.getInstalledApplications(0);
                mAppItems = new ArrayList<>();

                for(ApplicationInfo app : apps) {
                    //Create App Items for each installed app
                    AppItem item = new AppItem(pm.getApplicationLabel(app).toString(),
                            app.packageName, pm.getApplicationIcon(app));
                    mAppItems.add(item);

                }
            }
        });
        appListThread.start();
    }

    public static void destroyAppList() {
        mAppItems = null;
    }

    public static List<AppItem> getAppItems() {
        return mAppItems;
    }

    public static void initRoot(final RootCallback cb) {

        Thread checkSUthread = new Thread(new Runnable() {
            @Override
            public void run() {
                synchronized (ROOTLOCK) {
                    mIsRootAvailable = Shell.SU.available();
                    Log.i(TAG, "Root availability status: " + mIsRootAvailable);
                    cb.OnRootInitialized(mIsRootAvailable);
                }
            }
        });
        checkSUthread.start();
    }

    public static Boolean isRootAvailable() {
        return mIsRootAvailable;
    }

    public static boolean hasSignaturePermission(Context context) {
        return (hasPermission(context, DEVICE_POWER_PERMISSION) &&
                hasPermission(context, WRITE_SECURE_SETTINGS_PERMISSION));
    }

    final private static char[] hexArray = "0123456789ABCDEF".toCharArray();
    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 3];
        for ( int j = 0; j < bytes.length; j++ ) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 3] = hexArray[v >>> 4];
            hexChars[j * 3 + 1] = hexArray[v & 0x0F];
            if (j > 0 && j % 14 == 0 ) {
                // newline every 15 bytes (15th byte is 14th index)
                hexChars[j * 3 + 2] = '\n';
            } else {
                hexChars[j * 3 + 2] = ' ';
            }
        }
        return new String(hexChars);
    }

}
