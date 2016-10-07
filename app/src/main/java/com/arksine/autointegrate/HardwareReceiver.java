package com.arksine.autointegrate;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

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

    interface UsbCallback {
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

                    /*TODO: rebroadcast to local reciever.  This is temporary until I can implement
                      error handling directly into code.
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

                    // TODO: broadcast to bluetooth helper when State is on notifying it?
                    if (state == BluetoothAdapter.STATE_ON || state == BluetoothAdapter.STATE_TURNING_OFF) {

                        Intent devChanged = new Intent(ACTION_DEVICE_CHANGED);
                        LocalBroadcastManager.getInstance(context).sendBroadcast(devChanged);
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

    static public void setUsbPermissionCallback(UsbDevice device,
                                                UsbCallback usbRequestComplete) {
        if (device == null || usbRequestComplete == null) {
            // passed bad parameters
            Log.d(TAG, "Passed null parameter in setUsbPermissionCallback");
            return;
        }
        requestedUsbDevice = device;
        usbCallback = usbRequestComplete;
    }

}
