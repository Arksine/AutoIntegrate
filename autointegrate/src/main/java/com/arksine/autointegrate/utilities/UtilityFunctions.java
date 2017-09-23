package com.arksine.autointegrate.utilities;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.List;

import timber.log.Timber;


/**
 * Static class for various helper functions used throughout service / application
 */

public class UtilityFunctions {

    private final static String DEVICE_POWER_PERMISSION =
            "android.permission.DEVICE_POWER";
    private final static String WRITE_SECURE_SETTINGS_PERMISSION =
            "android.permission.WRITE_SECURE_SETTINGS";



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

    public static String byteToHex(byte b) {
        int i = b & 0xFF;
        return Integer.toHexString(i);
    }

    public static boolean isInteger(String s) {
        return isInteger(s,10);
    }

    public static boolean isInteger(String s, int radix) {
        if(s.isEmpty()) return false;
        for(int i = 0; i < s.length(); i++) {
            if(i == 0 && s.charAt(i) == '-') {
                if(s.length() == 1) return false;
                else continue;
            }
            if(Character.digit(s.charAt(i),radix) < 0) return false;
        }
        return true;
    }

    private static String getWifiIpAddress() {
        return getHostAddress("wlan0", true);
    }

    private static String getHostAddress(String netInterface, boolean useIPv4) {
        try {
            NetworkInterface intf = NetworkInterface.getByName(netInterface);
            if (intf != null) {
                List<InetAddress> addrs = Collections.list(intf.getInetAddresses());
                for (InetAddress addr : addrs) {
                    if (!addr.isLoopbackAddress()) {
                        String sAddr = addr.getHostAddress();
                        //boolean isIPv4 = InetAddressUtils.isIPv4Address(sAddr);
                        boolean isIPv4 = sAddr.indexOf(':') < 0;

                        if (useIPv4) {
                            if (isIPv4)
                                return sAddr;
                        } else {
                            if (!isIPv4) {
                                int delim = sAddr.indexOf('%'); // drop ip6 zone suffix
                                return delim < 0 ? sAddr.toUpperCase() : sAddr.substring(0, delim).toUpperCase();
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            Timber.v(e);
        }

        return "";
    }
}
