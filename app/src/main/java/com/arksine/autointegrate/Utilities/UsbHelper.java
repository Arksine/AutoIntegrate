package com.arksine.autointegrate.utilities;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Build;
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


    private UsbSerialSettings mUsbSettings;

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
        mUsbSettings = new UsbSerialSettings();
        mUsbManager = (UsbManager) mContext.getSystemService(Context.USB_SERVICE);

    }


    public UsbHelper(Context context, UsbSerialSettings settings) {
        this.mContext = context;
        this.mUsbSettings = settings;
        mUsbManager = (UsbManager) mContext.getSystemService(Context.USB_SERVICE);

    }

    public ArrayList<String> enumerateSerialDevices() {

        ArrayList<String> deviceList = new ArrayList<>(5);

        HashMap<String, UsbDevice> usbDeviceList = mUsbManager.getDeviceList();

        for (UsbDevice uDevice : usbDeviceList.values()) {


            DLog.v(TAG, "Device ID: " + uDevice.getDeviceId());
            DLog.v(TAG, "Device Name: " + uDevice.getDeviceName());
            DLog.v(TAG, "Vendor: ID " + uDevice.getVendorId());
            DLog.v(TAG, "Product ID: " + uDevice.getProductId());
            DLog.v(TAG, "Class: " + uDevice.getDeviceClass());
            DLog.v(TAG, "SubClass: " + uDevice.getDeviceSubclass());
            DLog.v(TAG, "Protocol: " + uDevice.getDeviceProtocol());

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                DLog.v(TAG, "Manufacturer: " + uDevice.getManufacturerName());
                DLog.v(TAG, "Serial Number: " + uDevice.getSerialNumber());
            }

            String name;

            // Check for supported devices
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
                // not a supported USB Serial device, break
                break;
            }

            DLog.v(TAG, "USB comm device found: " + name);

            // replace the name with the device driver name if on API 21 or above
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                name = uDevice.getProductName();
            }






            /**
             * Don't add the MJS HD Radio cable to the list if its connected.  Its an FTDI serial
             * comm device, but its specialized, not for MCU use
             *
             * MJS Cable - VID 0x0403 (1027), PID 0x9378 (37752)
             *
             *  TODO: I bet the 3rd ID (sub pid ) is 937C
              */
            if ((uDevice.getVendorId() == 1027) && (uDevice.getProductId() ==  37752)) {
                DLog.v(TAG, "MJS Cable found, skipping from list");
                break;
            }

            String id;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                id = uDevice.getVendorId() + ":" + uDevice.getProductId() + ":"
                        + uDevice.getSerialNumber();
            } else {
                id = uDevice.getVendorId() + ":" + uDevice.getProductId();
            }

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
        boolean correctFormat;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            correctFormat = ids.length == 3;
        } else {
            correctFormat = ids.length == 2;
        }
        if (!correctFormat) {
            Log.i(TAG, "Invalid USB entry: " + id);
            return false;
        }

        // Because usbfs locations don't persist across disconnects, devices are found by
        // searching for the vid/pid/serialnumber.  The Serial Number is only available in
        // Lollipop and later, meaning the only search done can be by VID/PID.  It is NOT recommended
        // to have two devices with the same VID/PID, particularly on lollipop and below.
        boolean found;
        for (UsbDevice dev : usbDeviceList.values()) {

            found = dev.getVendorId() == Integer.parseInt(ids[0]) &&
                    dev.getProductId() == Integer.parseInt(ids[1]);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                String serialNumber = dev.getSerialNumber();

                // Sometimes the serial number is not available, so check it
                if (serialNumber != null) {
                    found = found && serialNumber.equals(ids[2]);
                }
            }

            if (found) {
                DLog.i(TAG, "USB Device Found");
                mUsbDevice = dev;
                break;
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
                            Log.w(TAG, e.getMessage());
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

                    mSerialPort.setBaudRate(mUsbSettings.baudRate);
                    mSerialPort.setDataBits(mUsbSettings.dataBits);
                    mSerialPort.setStopBits(mUsbSettings.stopBits);
                    mSerialPort.setParity(mUsbSettings.parity);
                    mSerialPort.setFlowControl(mUsbSettings.flowControl);
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
                            Log.w(TAG, e.getMessage());
                        }
                    }

                    // Device is open and ready
                    serialPortConnected = true;
                    mSerialHelperCallbacks.OnDeviceReady(true);
                 } else {
                    // Serial port could not be opened
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
