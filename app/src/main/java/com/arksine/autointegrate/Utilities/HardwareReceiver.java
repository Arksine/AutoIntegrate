package com.arksine.autointegrate.utilities;

import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.support.annotation.NonNull;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.arksine.autointegrate.R;

import java.lang.reflect.Method;

/**
 * This class manages USB and Bluetooth Hardware related broadcasts.  We want to make sure
 * that they are available at all times for activities and services
 */

public class HardwareReceiver extends BroadcastReceiver {

    private static String TAG = "HardwareReceiver";

    private static final String ACTION_DEVICE_CHANGED = "com.arksine.autointegrate.ACTION_DEVICE_CHANGED";
    private static final String ACTION_USB_PERMISSION = "com.arksine.autointegrate.USB_PERMISSION";
    public static final String ACTION_USB_ATTACHED = "android.hardware.usb.action.USB_DEVICE_ATTACHED";
    public static final String ACTION_USB_DETACHED = "android.hardware.usb.action.USB_DEVICE_DETACHED";

    public interface UsbCallback {
        void onUsbPermissionRequestComplete(boolean requestStatus);
    }

    private static UsbCallback usbCallback;
    private static UsbDevice requestedUsbDevice;

    @Override
    public void onReceive(Context context, Intent intent) {

        String action = intent.getAction();
        switch (action) {
            case ACTION_USB_PERMISSION:
                synchronized (this) {
                    if (usbCallback == null) {
                        return;
                    }
                    UsbDevice uDev = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

                    // make sure this is the correct device
                    if (uDev.equals(requestedUsbDevice)) {
                        boolean accessGranted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);
                        if (accessGranted) {
                            usbCallback.onUsbPermissionRequestComplete(true);
                        } else {
                            Log.d(TAG, "permission denied for device " + uDev);
                            usbCallback.onUsbPermissionRequestComplete(false);
                        }
                    }
                }
                break;
            case ACTION_USB_ATTACHED:
                synchronized (this) {
                    // send a broadcast for the main activity to repopulate the device list if a device
                    // is connected or disconnected
                    Intent devChanged = new Intent(ACTION_DEVICE_CHANGED);
                    LocalBroadcastManager.getInstance(context).sendBroadcast(devChanged);
                    Log.i(TAG, "Usb device attached");

                }
                break;
            case ACTION_USB_DETACHED:
                synchronized (this) {
                    // send a broadcast for the main activity to repopulate the device list if a device
                    // is connected or disconnected
                    Intent devChanged = new Intent(ACTION_DEVICE_CHANGED);
                    LocalBroadcastManager.getInstance(context).sendBroadcast(devChanged);
                    Log.i(TAG, "Usb device removed");

                    /*TODO: The broadcast below is temporary until I can implement error handling
                      directly into the UsbHelper class.  Currently
                    */
                    UsbDevice uDev = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    Intent devDisconnected = new Intent(context.getString(R.string.ACTION_DEVICE_DISCONNECTED));
                    devDisconnected.putExtra(UsbManager.EXTRA_DEVICE, uDev);
                    LocalBroadcastManager.getInstance(context).sendBroadcast(devDisconnected);
                }
                break;
            case BluetoothAdapter.ACTION_STATE_CHANGED:
                synchronized (this) {
                    int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,
                            BluetoothAdapter.STATE_OFF);

                    if (state == BluetoothAdapter.STATE_ON) {

                        Intent localDevIntent = new Intent(context.getString(R.string.ACTION_BT_ADAPTER_ON));
                        LocalBroadcastManager.getInstance(context).sendBroadcast(localDevIntent);
                    }

                }
                break;
            case BluetoothAdapter.ACTION_DISCOVERY_FINISHED:
                synchronized (this) {
                    Intent devChanged = new Intent(ACTION_DEVICE_CHANGED);
                    LocalBroadcastManager.getInstance(context).sendBroadcast(devChanged);
                }
                break;
            case BluetoothDevice.ACTION_ACL_DISCONNECTED:
                synchronized (this) {
                    Log.i(TAG, "Bluetooth device disconnected");
                    // Errors are handled by the Bluetooth helper class
                }
                break;
        }
    }

    public static void setUsbPermissionCallback(UsbDevice device,
                                                UsbCallback usbRequestComplete) {
        if (device == null || usbRequestComplete == null) {
            // passed bad parameters
            Log.d(TAG, "Passed null parameter in setUsbPermissionCallback");
            return;
        }
        requestedUsbDevice = device;
        usbCallback = usbRequestComplete;
    }

    public static void requestUsbPermission(@NonNull UsbDevice device, @NonNull UsbCallback usbRequestComplete,
                                            @NonNull Context context) {

        requestedUsbDevice = device;
        usbCallback = usbRequestComplete;

        UsbManager usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);

        PendingIntent mPendingIntent = PendingIntent.getBroadcast(context, 0, new Intent(ACTION_USB_PERMISSION), 0);
        usbManager.requestPermission(requestedUsbDevice, mPendingIntent);
    }

    // The purpose of this function is to bypass the standard usb permission model and grant permission
    // without requesting it from the user
    public static boolean grantAutomaticUsbPermission(UsbDevice usbDevice, Context context)
    {
        try
        {

            PackageManager pkgManager=context.getPackageManager();
            ApplicationInfo appInfo=pkgManager.getApplicationInfo(context.getPackageName(), PackageManager.GET_META_DATA);

            Class serviceManagerClass=Class.forName("android.os.ServiceManager");
            Method getServiceMethod=serviceManagerClass.getDeclaredMethod("getService",String.class);
            getServiceMethod.setAccessible(true);
            android.os.IBinder binder=(android.os.IBinder)getServiceMethod.invoke(null, Context.USB_SERVICE);

            Class iUsbManagerClass=Class.forName("android.hardware.usb.IUsbManager");
            Class stubClass=Class.forName("android.hardware.usb.IUsbManager$Stub");
            Method asInterfaceMethod=stubClass.getDeclaredMethod("asInterface", android.os.IBinder.class);
            asInterfaceMethod.setAccessible(true);
            Object iUsbManager=asInterfaceMethod.invoke(null, binder);


            System.out.println("UID : " + appInfo.uid + " " + appInfo.processName + " " + appInfo.permission);
            final Method grantDevicePermissionMethod = iUsbManagerClass.getDeclaredMethod("grantDevicePermission", UsbDevice.class,int.class);
            grantDevicePermissionMethod.setAccessible(true);
            grantDevicePermissionMethod.invoke(iUsbManager, usbDevice,appInfo.uid);


            Log.i(TAG, "Method OK : " + binder + "  " + iUsbManager);
            return true;
        }
        catch(Exception e)
        {
            Log.e(TAG, "Error trying to assign automatic usb permission : " + usbDevice.getDeviceName());
            e.printStackTrace();
            return false;
        }
    }

}
