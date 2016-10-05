package com.arksine.autointegrate;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.preference.PreferenceManager;
import android.util.Log;

import com.felhr.deviceids.CH34xIds;
import com.felhr.deviceids.CP210xIds;
import com.felhr.deviceids.FTDISioIds;
import com.felhr.deviceids.PL2303Ids;
import com.felhr.deviceids.XdcVcpIds;
import com.felhr.usbserial.CDCSerialDevice;
import com.felhr.usbserial.UsbSerialDevice;
import com.felhr.usbserial.UsbSerialInterface;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;

/**
 *  Helper class to enumerate usb devices and establish a connection
 */
class UsbHelper implements SerialHelper {

    private static final String TAG = "UsbHelper";


    private static final String ACTION_USB_PERMISSION = "com.arksine.autointegrate.USB_PERMISSION";
    private static final int BAUD_RATE = 9600; // BaudRate. Change this value if you need

    private Context mContext;
    private UsbManager mUsbManager;
    private volatile UsbDevice mUsbDevice;
    private UsbSerialDevice mSerialPort;

    private volatile boolean serialPortConnected = false;

    private SerialHelper.DataReceivedListener dataReceivedListener;

    private UsbSerialInterface.UsbReadCallback mCallback = new UsbSerialInterface.UsbReadCallback() {

        @Override
        public void onReceivedData(byte[] arg0)
        {
            // Send the data back to the instantiating class via callback
            dataReceivedListener.OnDataReceived(arg0);
        }
    };


    UsbHelper(Context context) {
        this.mContext = context;
        mUsbManager = (UsbManager) mContext.getSystemService(Context.USB_SERVICE);

    }

    public ArrayList<String> enumerateDevices() {

        ArrayList<String> deviceList = new ArrayList<>(5);

        HashMap<String, UsbDevice> usbDeviceList = mUsbManager.getDeviceList();

        for (UsbDevice uDevice : usbDeviceList.values()) {

            String name;
            // replace the name with the real name if android supports it
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                name = uDevice.getProductName();
            } else {
                if (UsbSerialDevice.isCdcDevice(uDevice)) {
                    name = "CDC serial device";
                } else if (CH34xIds.isDeviceSupported(uDevice.getVendorId(), uDevice.getProductId())) {
                    name = "CH34x serial device";
                } else if (CP210xIds.isDeviceSupported(uDevice.getVendorId(), uDevice.getProductId())) {
                    name = "CP210X serial device";
                } else if (FTDISioIds.isDeviceSupported(uDevice.getVendorId(), uDevice.getProductId())) {
                    name = "FTDI serial device";
                } else if (PL2303Ids.isDeviceSupported(uDevice.getVendorId(), uDevice.getProductId())) {
                    name = "PL2303 serial device";
                } else if (XdcVcpIds.isDeviceSupported(uDevice.getVendorId(), uDevice.getProductId())) {
                    name = "Virtual serial device";
                } else {
                    // not supported
                    name = "Unknown USB Device";
                }
            }



            Log.i(TAG, "usb device found: " + name);
            Log.i(TAG, "Device ID: " + uDevice.getDeviceId());
            Log.i(TAG, "Device Name: " + uDevice.getDeviceName());
            Log.i(TAG, "Vendor: ID " + uDevice.getVendorId());
            Log.i(TAG, "Product ID: " + uDevice.getProductId());

            String id = uDevice.getVendorId() + ":" + uDevice.getProductId() + ":"
                    + uDevice.getDeviceName();

            String entry = name + "\n" +  id;
            deviceList.add(entry);
        }

        return deviceList;
    }

    public void connectDevice(String id, SerialHelper.DeviceReadyListener readyListener,
                              DataReceivedListener rcdListener) {
        dataReceivedListener = rcdListener;
        HashMap<String, UsbDevice> usbDeviceList = mUsbManager.getDeviceList();
        String[] ids = id.split(":");

        // Make sure the entry value is formatted correctly
        if (ids.length != 3) {
            Log.e(TAG, "Invalid USB entry: " + id);
            readyListener.OnDeviceReady(false);

            return;
        }

        mUsbDevice = usbDeviceList.get(ids[2]);

        // if we have can't find the device by its USBFS location, attempt to find it by VID/PID
        if (mUsbDevice == null) {
            // Because UsbIDs don't persist across disconnects, we store the VID/PID if each USB device
            // and iterate though the list for matches
            for (UsbDevice dev : usbDeviceList.values()) {

                if (dev.getVendorId() == Integer.parseInt(ids[0]) &&
                        dev.getProductId() == Integer.parseInt(ids[1])) {
                    mUsbDevice = dev;
                    break;
                }
            }
        }

        if (mUsbDevice != null) {
            // valid device, request permission to use
            ConnectionThread mConnectionThread = new ConnectionThread(readyListener);
            mConnectionThread.start();

        } else {

            Log.i(TAG, "Invalid usb device: " + id);
            readyListener.OnDeviceReady(false);
        }

    }

    // Publishes the connection to the HardwareReciever so it can properly respond to connect and
    // disconnect events
    public void publishConnection(HardwareReceiver.UsbDeviceType type) {
        HardwareReceiver.setConnectedDevice(mUsbDevice, type);
    }

    public String getConnectedId() {
        if (serialPortConnected) {
            return (mUsbDevice.getVendorId() + ":" + mUsbDevice.getProductId()
                    + mUsbDevice.getDeviceName());
        }
        return "";
    }

    public void disconnect() {

        if (mSerialPort != null) {
            mSerialPort.close();
            mSerialPort = null;
        }
        serialPortConnected = false;

    }

    public boolean writeString(String data) {

        if (mSerialPort != null) {
            mSerialPort.write(data.getBytes());
            return true;
        }

        return false;
    }

    public boolean writeBytes(byte[] data) {
        if (mSerialPort != null) {
            mSerialPort.write(data);
            return true;
        }

        return false;
    }

    public boolean isDeviceConnected() {
        return serialPortConnected;
    }

    // The purpose of this function is to bypass the standard usb permission model and grant permission
    // without requesting it from the user
    private boolean grantAutomaticPermission(UsbDevice usbDevice)
    {
        try
        {

            PackageManager pkgManager=mContext.getPackageManager();
            ApplicationInfo appInfo=pkgManager.getApplicationInfo(mContext.getPackageName(), PackageManager.GET_META_DATA);

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
            Log.e(TAG, "Error trying to assign automatic usb permission : ");
            e.printStackTrace();
            return false;
        }
    }

    // This thread opens a usb serial connection on the specified device
    private class ConnectionThread extends Thread {

        private SerialHelper.DeviceReadyListener mReadyListener;
        private volatile boolean requestApproved = false;

        ConnectionThread(SerialHelper.DeviceReadyListener readyListener) {
            mReadyListener = readyListener;
        }

        public synchronized void resumeConnectionThread() {
            notify();
        }

        @Override
        public void run() {

            // check to see if we have permission, if not request it
            if(!mUsbManager.hasPermission(mUsbDevice)) {

                // Attempt to grant permission automatically (will only work if installed as system app)
                // If it fails, request permission the old fashioned way
                if(!grantAutomaticPermission(mUsbDevice)) {
                    HardwareReceiver.UsbCallback callback = new HardwareReceiver.UsbCallback() {
                        @Override
                        public void onUsbPermissionRequestComplete(boolean requestStatus) {
                            requestApproved = requestStatus;
                            resumeConnectionThread();
                        }
                    };
                    HardwareReceiver.setUsbPermissionCallback(mUsbDevice, callback);
                    PendingIntent mPendingIntent = PendingIntent.getBroadcast(mContext, 0, new Intent(ACTION_USB_PERMISSION), 0);
                    mUsbManager.requestPermission(mUsbDevice, mPendingIntent);

                    synchronized (this) {
                        try {
                            wait();
                        } catch (InterruptedException e) {
                            Log.e(TAG, e.getMessage());
                        }
                    }

                    if (!requestApproved) {
                        mReadyListener.OnDeviceReady(false);
                        return;
                    }
                }
            }

            UsbDeviceConnection mUsbConnection = mUsbManager.openDevice(mUsbDevice);

            mSerialPort = UsbSerialDevice.createUsbSerialDevice(mUsbDevice, mUsbConnection);
            if (mSerialPort != null) {
                if (mSerialPort.open()) {
                    mSerialPort.setBaudRate(BAUD_RATE);
                    mSerialPort.setDataBits(UsbSerialInterface.DATA_BITS_8);
                    mSerialPort.setStopBits(UsbSerialInterface.STOP_BITS_1);
                    mSerialPort.setParity(UsbSerialInterface.PARITY_NONE);
                    mSerialPort.setFlowControl(UsbSerialInterface.FLOW_CONTROL_OFF);
                    mSerialPort.read(mCallback);

                    // Device is open and ready
                    serialPortConnected = true;
                    mReadyListener.OnDeviceReady(true);
                 } else {
                    // Serial port could not be opened, maybe an I/O error or if CDC driver was chosen, it does not really fit
                    // Send an Intent to Main Activity
                    if (mSerialPort instanceof CDCSerialDevice) {
                        Log.i(TAG, "Unable to open CDC Serial device");
                        mReadyListener.OnDeviceReady(false);
                    } else {
                        Log.i(TAG, "Unable to open serial device");
                        mReadyListener.OnDeviceReady(false);
                    }
                }
            } else {
                // No driver for given device, even generic CDC driver could not be loaded
                Log.i(TAG, "Serial Device not supported");
                mReadyListener.OnDeviceReady(false);
            }
        }
    }
}
