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
import android.widget.Toast;

import com.arksine.autointegrate.R;
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

import timber.log.Timber;

//TODO: The USBSerial library doesnt seem to have any way to handle errors.  Temporarily handle disconnections
//      with a broadcast receiver.Will have to research
//      mik3y's library, or just settle for handling disconnect event's via android intents

/**
 *  Helper class to enumerate usb devices and establish a connection
 */
public class UsbHelper extends SerialHelper {

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

    @Override
    public ArrayList<String> enumerateSerialDevices() {

        ArrayList<String> deviceList = new ArrayList<>(5);

        HashMap<String, UsbDevice> usbDeviceList = mUsbManager.getDeviceList();

        for (UsbDevice uDevice : usbDeviceList.values()) {


            Timber.d("Device ID: %d", uDevice.getDeviceId());
            Timber.d("Device Name: %s", uDevice.getDeviceName());
            Timber.d("Vendor: ID %#x", uDevice.getVendorId());
            Timber.d("Product ID: %#x", uDevice.getProductId());
            Timber.d("Class: %#x", uDevice.getDeviceClass());
            Timber.d("SubClass: %#x", uDevice.getDeviceSubclass());
            Timber.d("Protocol: %#x", uDevice.getDeviceProtocol());

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                Timber.d("Manufacturer: %s", uDevice.getManufacturerName());
                Timber.d("Serial Number: %s", uDevice.getSerialNumber());
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

            Timber.v("USB comm device found: %s", name);

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
                Timber.v("MJS Cable found, skipping from list");
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
    @Override
    public boolean connectDevice(String id, SerialHelper.Callbacks cbs) {

        // TODO: I can still get the serial number on devices older than lollipop by opening
        // a usb connection, however many devices do not have a serial number.  The better
        // way of doing this would be to use the id retrieved from the MCU, and let the
        // MicroControllerCom class loop through all devices, connecting to them and checking the id
        // until it finds the ID its looking for

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
            Timber.i("Invalid USB entry: %s", id);
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
                Timber.v("USB Device Found");
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

            Timber.i("Invalid usb device: %s", id);
            return false;
        }
    }

    @Override
    public String getConnectedId() {
        if (serialPortConnected) {
            return (mUsbDevice.getVendorId() + ":" + mUsbDevice.getProductId() + ":"
                    + mUsbDevice.getDeviceName());
        }
        return "";
    }

    @Override
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

    @Override
    public boolean writeBytes(byte[] data) {
        if (mSerialPort != null) {
            mSerialPort.write(data);
            return true;
        }

        return false;
    }

    @Override
    public boolean writeString(String data) {
        return writeBytes(data.getBytes());
    }

    @Override
    public boolean isDeviceConnected() {
        return serialPortConnected;
    }

    @Override
    public void toggleDTR(boolean state) {
        if (serialPortConnected) {
            mSerialPort.setDTR(state);
        }
    }

    @Override
    public void toggleRTS(boolean state) {
        if (serialPortConnected) {
            mSerialPort.setRTS(state);
        }
    }

    @Override
    public void setBaud(int baud) {
        if (serialPortConnected) {
            mSerialPort.setBaudRate(baud);
        }
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
                            Timber.w(e);
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
                            Timber.w(e);
                        }
                    }

                    // Device is open and ready
                    serialPortConnected = true;
                    mSerialHelperCallbacks.OnDeviceReady(true);
                 } else {
                    // Serial port could not be opened
                    if (mSerialPort instanceof CDCSerialDevice) {
                        Timber.i("Unable to open CDC Serial device");
                        mSerialHelperCallbacks.OnDeviceReady(false);
                    } else {
                        Timber.i("Unable to open serial device");
                        mSerialHelperCallbacks.OnDeviceReady(false);
                    }
                }
            } else {
                // No driver for given device, even generic CDC driver could not be loaded
                Timber.i("Serial Device not supported");
                mSerialHelperCallbacks.OnDeviceReady(false);
            }
        }
    }
}
