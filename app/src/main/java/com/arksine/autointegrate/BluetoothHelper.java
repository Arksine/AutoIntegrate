package com.arksine.autointegrate;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.os.Process;
import android.util.Log;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;


/**
 * Class BluetoothHelper - Handles basic bluetooth tasks, implements async read/write
 */
class BluetoothHelper implements SerialHelper {

    private static String TAG = "BluetoothHelper";

    private ExecutorService EXECUTOR = null;
    private Future mReaderThreadFuture = null;

    private Context mContext = null;

    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothSocket mSocket;
    private volatile BluetoothDevice mBtDevice;

    private volatile boolean deviceConnected = false;
    private volatile InputStream serialIn;
    private OutputStream serialOut;


    private SerialHelper.Callbacks mSerialHelperCallbacks;

    private final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    // TODO: May want to redo this using BTWiz library

    BluetoothHelper(Context context){
        mContext = context;
        deviceConnected = false;
        initBluetooth();
    }

    /**
     * Initializes bluetooth adapter, makes sure that it is turned on and prompts
     * user to turn it on if it isn't on.
     */
    private void initBluetooth() {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null) {
            // Device does not support Bluetooth, return an empty list
            Toast.makeText(mContext, "This device does not support bluetooth",
                    Toast.LENGTH_SHORT).show();

            return;
        }

        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            mContext.startActivity(enableBtIntent);
        }
    }

    /**
     * This unitializes the bluetooth manager.  It should always be called before a context is
     * destroyed in the onDestroy method.
     */
    public void disconnect() {

        deviceConnected = false;
        closeBluetoothSocket();

        if (EXECUTOR != null) {
            EXECUTOR.shutdown();

            try {
               EXECUTOR.awaitTermination(10, TimeUnit.SECONDS);
            } catch (Exception e) {
                Log.e(TAG, e.getMessage());
            }

            if (!EXECUTOR.isTerminated()) {
                EXECUTOR.shutdownNow();
            }

            EXECUTOR = null;
        }

    }

    public boolean isBluetoothOn() {
        if (mBluetoothAdapter == null)
            return false;
        else
            return mBluetoothAdapter.isEnabled();
    }

    public ArrayList<String> enumerateDevices() {

        if (!isBluetoothOn()) {
            return null;
        }

        ArrayList<String> mAdapterList = new ArrayList<>(5);

        // We know the bluetooth adapter is enabled, so we can retrieve it
        // and get devices that are mapped
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();

        // If there are paired devices
        if (pairedDevices.size() > 0) {
            // Loop through paired devices
            for (BluetoothDevice device : pairedDevices) {

                // TODO: may need to compare the device name to a list of supported
                //       devices before adding, as it doesn't appear I can get the
                //       device profiles supported

                // Add the name and address to an array adapter to show in a ListView
                mAdapterList.add(device.getName() + "\n" + device.getAddress());
            }
        }

        return mAdapterList;
    }

    /**
     * Retrieves a bluetooth socket from a specified device.  WARNING: This function is blocking, do
     * not call from the UI thread!
     * @param macAddr - The mac address of the device to connect
     */
    public void connectDevice (String macAddr, SerialHelper.Callbacks cbs) {
        if (deviceConnected) {
            disconnect();
        }

        EXECUTOR = Executors.newCachedThreadPool(new BackgroundThreadFactory());
        mSerialHelperCallbacks = cbs;

        //TODO: If state is turning_on then we should wait until it is on, or just let the servicethread
        //      continue connection attempts until its turned on

        if (!isBluetoothOn()) {
            mSerialHelperCallbacks.OnDeviceReady(false);
            return;
        }




        ConnectionThread btConnectThread = new ConnectionThread(macAddr);
        EXECUTOR.execute(btConnectThread);

    }

    public String getConnectedId() {
        if (deviceConnected) {
            return mBtDevice.getAddress();
        }

        return "";
    }

    public void closeBluetoothSocket() {

        if (mSocket != null) {
            try {
                mSocket.close();
            }
            catch (IOException e) {
                Log.e(TAG, "Unable to close Socket", e);
            }
        }
    }

    public boolean isDeviceConnected() {
        return deviceConnected;
    }

    public boolean writeString(final String data) {

        if (mSocket == null) return false;

        // TODO: needs to be synchronized?
        EXECUTOR.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    serialOut.write(data.getBytes());
                } catch (IOException e) {
                    Log.e(TAG, "Error writing to device\n", e);
                    mSerialHelperCallbacks.OnDeviceError();
                }
            }
        });


        return true;
    }

    public boolean writeBytes(final byte[] data) {

        if (mSocket == null) return false;

        // TODO: needs to be synchronized?
        EXECUTOR.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    serialOut.write(data);
                } catch (IOException e) {
                    Log.e(TAG, "Error writing to device\n", e);
                    mSerialHelperCallbacks.OnDeviceError();
                }
            }
        });


        return true;
    }

    /**
     * Thread for connecting a device and creating its input and output streams.
     */
    private class ConnectionThread extends Thread {

        private String macAddr;

        ConnectionThread(String macAddr) {
            this.macAddr = macAddr;
        }

        @Override
        public void run() {

            mBtDevice = mBluetoothAdapter.getRemoteDevice(macAddr);
            if (mBtDevice == null) {
                // device does not exist
                Log.e(TAG, "Unable to open bluetooth device at " + macAddr);
                deviceConnected = false;
                mSerialHelperCallbacks.OnDeviceReady(false);
                return;
            }

            // Attempt to create an insecure socket.  In the future I should probably
            // add an option for a secure connection, as this is subject to a man
            // in the middle attack.
            try {
                mSocket = mBtDevice.createInsecureRfcommSocketToServiceRecord(MY_UUID);
            }
            catch (IOException e) {
                Log.e (TAG, "Unable to retrieve bluetooth socket for device " + macAddr);
                mSocket = null;
                deviceConnected = false;
                mSerialHelperCallbacks.OnDeviceReady(false);
                return;
            }

            mBluetoothAdapter.cancelDiscovery();

            try {
                // Connect the device through the socket. This will block
                // until it succeeds or throws an exception
                mSocket.connect();
            } catch (IOException connectException) {

                Log.e (TAG, "Unable to connect to bluetooth socket for device " + macAddr);
                // Unable to connect; close the socket and get out
                try {
                    mSocket.close();
                } catch (IOException closeException) {
                    Log.e(TAG, "Error closing bluetooth socket", closeException);
                }

                mSocket = null;
                deviceConnected = false;
                mSerialHelperCallbacks.OnDeviceReady(false);
                return;
            }

            // Get input stream
            try {
                serialIn = mSocket.getInputStream();
            } catch (IOException e) {
                serialIn = null;
            }

            // Get output stream
            try {
                serialOut = mSocket.getOutputStream();
            } catch (IOException e) {
                serialOut = null;
            }

            deviceConnected = serialOut != null && serialIn != null;

            // start reader thread if connection is established
            if (deviceConnected) {
                mReaderThreadFuture = EXECUTOR.submit(new Runnable() {
                    @Override
                    public void run() {
                        int available = 0;
                        byte[] buffer = new byte[256];
                        while (deviceConnected) {
                            try {
                                available = serialIn.read(buffer);
                                mSerialHelperCallbacks.OnDataReceived(Arrays.copyOfRange(buffer, 0, available));

                            } catch (IOException e) {
                                // connection was closed before the device was disconnected
                                if (deviceConnected) {
                                    Log.d(TAG, "Error reading from bluetooth device", e);
                                    mSerialHelperCallbacks.OnDeviceError();
                                }
                                return;
                            }
                        }
                    }
                });
            }

            mSerialHelperCallbacks.OnDeviceReady(deviceConnected);
        }
    }
}
