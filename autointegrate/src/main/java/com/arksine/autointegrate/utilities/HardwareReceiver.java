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

import com.arksine.autointegrate.R;

import java.lang.reflect.Method;

import timber.log.Timber;

/**
 * This class manages USB and Bluetooth Hardware related broadcasts.  We want to make sure
 * that they are available at all times for activities and services
 */

public class HardwareReceiver extends BroadcastReceiver {

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
                            Timber.w("Permission denied for device %s", uDev);
                            usbCallback.onUsbPermissionRequestComplete(false);
                        }
                    }
                }
                break;
            case ACTION_USB_ATTACHED:
                synchronized (this) {
                    // send a broadcast for the Microcontroller settings  to repopulate the
                    // usb device list if a device is connected or disconnected


                    Intent devChanged = new Intent(ACTION_DEVICE_CHANGED);
                    LocalBroadcastManager.getInstance(context).sendBroadcast(devChanged);
                    Timber.v("Usb device attached");

                }
                break;
            case ACTION_USB_DETACHED:
                synchronized (this) {
                    // send a broadcast for the Microcontroller settings  to repopulate the
                    // usb device list if a device is connected or disconnected
                    Intent devChanged = new Intent(ACTION_DEVICE_CHANGED);
                    LocalBroadcastManager.getInstance(context).sendBroadcast(devChanged);
                    Timber.v("Usb device removed");

                    /*TODO: The broadcast below is temporary until I can implement error handling
                      directly into the UsbHelper class.  I believe I am limited by the UsbSerial
                      Library in that hardware disconnections do not trigger any kind of callback
                      or errors.
                    */

                    // TODO: I should probably implement this post-delayed, so the power manager
                    // has a chance to shut down first
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
                    Timber.v("Bluetooth device disconnected");
                    // Errors are handled by the Bluetooth helper class
                }
                break;
        }
    }

    public synchronized static void requestUsbPermission(@NonNull UsbDevice device,
                                                         @NonNull UsbCallback usbRequestComplete,
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
            Object iUsbManager;
            Class<?> ServiceManager = Class.forName("android.os.ServiceManager");
            Class<?> Stub = Class.forName("android.hardware.usb.IUsbManager$Stub");

            PackageManager pkgManager=context.getPackageManager();
            ApplicationInfo appInfo=pkgManager.getApplicationInfo(context.getPackageName(), PackageManager.GET_META_DATA);

            Method getServiceMethod=ServiceManager.getDeclaredMethod("getService",String.class);
            getServiceMethod.setAccessible(true);
            android.os.IBinder binder=(android.os.IBinder)getServiceMethod.invoke(null, Context.USB_SERVICE);

            Method asInterfaceMethod=Stub.getDeclaredMethod("asInterface", android.os.IBinder.class);
            asInterfaceMethod.setAccessible(true);
            iUsbManager=asInterfaceMethod.invoke(null, binder);


            System.out.println("UID : " + appInfo.uid + " " + appInfo.processName + " " + appInfo.permission);
            final Method grantDevicePermissionMethod = iUsbManager.getClass().getDeclaredMethod("grantDevicePermission", UsbDevice.class,int.class);
            grantDevicePermissionMethod.setAccessible(true);
            grantDevicePermissionMethod.invoke(iUsbManager, usbDevice,appInfo.uid);


            Timber.i("Method OK : %s %s", binder.toString(), iUsbManager.toString());
            return true;
        }
        catch(Exception e)
        {
            Timber.i("SignatureOrSystem permission not available, " +
                            " cannot assign automatic usb permission : %s",
                            usbDevice.getDeviceName());
            Timber.w(e);
            return false;
        }
    }

}
