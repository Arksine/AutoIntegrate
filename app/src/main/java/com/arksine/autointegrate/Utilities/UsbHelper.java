package com.arksine.autointegrate.utilities;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.widget.Toast;

import com.arksine.autointegrate.R;
import com.arksine.autointegrate.interfaces.SerialHelper;
import com.felhr.deviceids.CH34xIds;
import com.felhr.deviceids.CP210xIds;
import com.felhr.deviceids.FTDISioIds;
import com.felhr.deviceids.PL2303Ids;
import com.felhr.deviceids.XdcVcpIds;
import com.felhr.usbserial.CDCSerialDevice;
import com.felhr.usbserial.UsbSerialDevice;
import com.felhr.usbserial.UsbSerialInterface;

import java.util.ArrayList;
import java.util.HashMap;

//TODO: The USBSerial library doesnt seem to have any way to handle errors.  Temporarily handle disconnections
//      with a broadcast receiver.Will have to research
//      mik3y's library, or just settle for handling disconnect event's via android intents

/**
 *  Helper class to enumerate usb devices and establish a connection
 */
public class UsbHelper implements SerialHelper {

    private static final String TAG = "UsbHelper";

    private static final String ACTION_USB_PERMISSION = "com.arksine.autointegrate.USB_PERMISSION";

    private Context mContext;
    private UsbManager mUsbManager;
    private volatile UsbDevice mUsbDevice;
    private UsbSerialDevice mSerialPort;

    private volatile boolean serialPortConnected = false;
    private SerialHelper.Callbacks mSerialHelperCallbacks;

    // Broadcast Reciever to handle disconnections (this is temporary)
    private BroadcastReceiver mDisconnectReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(context.getString(R.string.ACTION_DEVICE_DISCONNECTED))) {
                synchronized (this) {
                    UsbDevice uDev = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (uDev.equals(mUsbDevice)) {

                        Toast.makeText(mContext, "USB Device Disconnected",
                                Toast.LENGTH_SHORT).show();

                        // Disconnect from a new thread so we don't block the UI thread
                        Thread errorThread = new Thread(new Runnable() {
                            @Override
                            public void run() {
                                mSerialHelperCallbacks.OnDeviceError();
                            }
                        });
                        errorThread.start();
                    }
                }
            }
        }
    };
    private volatile boolean mIsDisconnectReceiverRegistered = false;

    private UsbSerialInterface.UsbReadCallback mCallback = new UsbSerialInterface.UsbReadCallback() {

        @Override
        public void onReceivedData(byte[] arg0)
        {
            // Send the data back to the instantiating class via callback
            mSerialHelperCallbacks.OnDataReceived(arg0);
        }
    };


    public UsbHelper(Context context) {
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
            Log.i(TAG, "Class: " + uDevice.getDeviceClass());
            Log.i(TAG, "SubClass: " + uDevice.getDeviceSubclass());
            Log.i(TAG, "Protocol: " + uDevice.getDeviceProtocol());


            String id = uDevice.getVendorId() + ":" + uDevice.getProductId() + ":"
                    + uDevice.getDeviceName();

            String entry = name + "\n" +  id;
            deviceList.add(entry);
        }

        return deviceList;
    }

    /**
     * Connect to a USB device.  Returns true if the prerequisites to attempt connection are met,
     * false otherwise.  Note that return value of true does not mean that the connection was
     * succssful itself, that is handled through the onDeviceReady callback
     *
     * @param id
     * @param cbs
     * @return
     */
    public boolean connectDevice(String id, SerialHelper.Callbacks cbs) {

        if (serialPortConnected) {
            disconnect();
        }

        mSerialHelperCallbacks = cbs;
        HashMap<String, UsbDevice> usbDeviceList = mUsbManager.getDeviceList();
        String[] ids = id.split(":");

        // Make sure the entry value is formatted correctly
        if (ids.length != 3) {
            Log.e(TAG, "Invalid USB entry: " + id);
            return false;
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
            ConnectionThread mConnectionThread = new ConnectionThread();
            mConnectionThread.start();

            return true;

        } else {

            Log.i(TAG, "Invalid usb device: " + id);
            return false;
        }

    }

    public String getConnectedId() {
        if (serialPortConnected) {
            return (mUsbDevice.getVendorId() + ":" + mUsbDevice.getProductId() + ":"
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

        if (mIsDisconnectReceiverRegistered) {
            LocalBroadcastManager.getInstance(mContext).unregisterReceiver(mDisconnectReceiver);
            mIsDisconnectReceiverRegistered = false;
        }

    }

    public boolean writeString(final String data) {

        if (mSerialPort != null) {
            mSerialPort.write(data.getBytes());
            return true;
        }

        return false;
    }

    public boolean writeBytes(final byte[] data) {
        if (mSerialPort != null) {
            mSerialPort.write(data);
            return true;
        }

        return false;
    }

    public boolean isDeviceConnected() {
        return serialPortConnected;
    }



    // This thread opens a usb serial connection on the specified device
    private class ConnectionThread extends Thread {

        private volatile boolean requestApproved = false;


        public synchronized void resumeConnectionThread() {
            notify();
        }

        @Override
        public void run() {

            // check to see if we have permission, if not request it
            if(!mUsbManager.hasPermission(mUsbDevice)) {

                // Attempt to grant permission automatically (will only work if installed as system app)
                // If it fails, request permission the old fashioned way
                if(!HardwareReceiver.grantAutomaticUsbPermission(mUsbDevice, mContext)) {
                    HardwareReceiver.UsbCallback callback = new HardwareReceiver.UsbCallback() {
                        @Override
                        public void onUsbPermissionRequestComplete(boolean requestStatus) {
                            requestApproved = requestStatus;
                            resumeConnectionThread();
                        }
                    };

                    HardwareReceiver.requestUsbPermission(mUsbDevice, callback, mContext);

                    synchronized (this) {
                        try {
                            wait();
                        } catch (InterruptedException e) {
                            Log.e(TAG, e.getMessage());
                        }
                    }

                    if (!requestApproved) {
                        mSerialHelperCallbacks.OnDeviceReady(false);
                        return;
                    }
                }
            }

            UsbDeviceConnection mUsbConnection = mUsbManager.openDevice(mUsbDevice);

            mSerialPort = UsbSerialDevice.createUsbSerialDevice(mUsbDevice, mUsbConnection);
            if (mSerialPort != null) {
                if (mSerialPort.open()) {
                    String baudrate = PreferenceManager.getDefaultSharedPreferences(mContext)
                            .getString("controller_pref_key_select_baud", "9600");
                    mSerialPort.setBaudRate(Integer.valueOf(baudrate));
                    mSerialPort.setDataBits(UsbSerialInterface.DATA_BITS_8);
                    mSerialPort.setStopBits(UsbSerialInterface.STOP_BITS_1);
                    mSerialPort.setParity(UsbSerialInterface.PARITY_NONE);
                    mSerialPort.setFlowControl(UsbSerialInterface.FLOW_CONTROL_OFF);
                    mSerialPort.read(mCallback);

                    // since connection is successful, register the receiver
                    IntentFilter filter = new IntentFilter(mContext.getString(R.string.ACTION_DEVICE_DISCONNECTED));
                    LocalBroadcastManager.getInstance(mContext).registerReceiver(mDisconnectReceiver, filter);
                    mIsDisconnectReceiverRegistered = true;

                    // Some micro controllers need time to initialize before you can communicate.
                    // CH34x is one such device, others need to be tested.
                    if (CH34xIds.isDeviceSupported(mUsbDevice.getVendorId(), mUsbDevice.getProductId())) {
                        try {
                            Thread.sleep(2000);
                        } catch (InterruptedException e) {
                            Log.e(TAG, e.getMessage());
                        }
                    }

                    // Device is open and ready
                    serialPortConnected = true;
                    mSerialHelperCallbacks.OnDeviceReady(true);
                 } else {
                    // Serial port could not be opened, maybe an I/O error or if CDC driver was chosen, it does not really fit
                    // Send an Intent to Main Activity
                    if (mSerialPort instanceof CDCSerialDevice) {
                        Log.i(TAG, "Unable to open CDC Serial device");
                        mSerialHelperCallbacks.OnDeviceReady(false);
                    } else {
                        Log.i(TAG, "Unable to open serial device");
                        mSerialHelperCallbacks.OnDeviceReady(false);
                    }
                }
            } else {
                // No driver for given device, even generic CDC driver could not be loaded
                Log.i(TAG, "Serial Device not supported");
                mSerialHelperCallbacks.OnDeviceReady(false);
            }
        }
    }
}
