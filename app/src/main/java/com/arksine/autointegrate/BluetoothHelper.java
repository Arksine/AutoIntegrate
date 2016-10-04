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


/**
 * Class BluetoothHelper - Handles basic bluetooth tasks needed for this app.
 */
class BluetoothHelper implements SerialHelper {

    private static String TAG = "BluetoothHelper";

    private static final String ACTION_DEVICE_CHANGED = "com.arksine.autointegrate.ACTION_DEVICE_CHANGED";

    private Context mContext = null;

    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothSocket mSocket;
    private volatile BluetoothDevice mBtDevice;

    private volatile boolean deviceConnected = false;
    private volatile InputStream serialIn;
    private OutputStream serialOut;
    private Thread readerThread;

    private SerialHelper.DataReceivedListener dataReceivedListener;

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

        // stop the readerthread if closing the socket didn't kill it
        if(readerThread.isAlive()) {
            readerThread.interrupt();
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
    public void connectDevice (String macAddr, SerialHelper.DeviceReadyListener readyListener,
                               DataReceivedListener rcdListener) {

        if (!isBluetoothOn()) {
            readyListener.OnDeviceReady(false);
            return;
        }
        dataReceivedListener = rcdListener;
        ConnectionThread btConnectThread = new ConnectionThread(macAddr, readyListener);
        btConnectThread.start();

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

    public void publishConnection(HardwareReceiver.UsbDeviceType type) {
        HardwareReceiver.setConnectedDevice(mBtDevice);
    }

    public boolean isDeviceConnected() {

        return deviceConnected;
    }

    public boolean writeString(String data) {

        if (mSocket == null) return false;


        try {
            serialOut.write(data.getBytes());
        } catch(IOException e) {
            Log.e(TAG, "Error writing to device", e);
            // Error sending the start command to the arduino
            return false;
        }
        return true;
    }

    public boolean writeBytes(byte[] data) {

        if (mSocket == null) return false;

        try {
            serialOut.write(data);
        } catch(IOException e) {
            Log.e(TAG, "Error writing to device", e);
            // Error sending the start command to the arduino
            return false;
        }
        return true;
    }

    public byte readByte() {

        if (mSocket == null) return 0;

        byte input;
        try {
            input = (byte)serialIn.read();
        }
        catch (IOException e){
            Log.d(TAG, "Error reading from device", e);
            input = 0;
        }

        return input;
    }

    /**
     * Thread for connecting a device and creating its input and output streams.
     */
    private class ConnectionThread extends Thread {

        private String macAddr;
        private SerialHelper.DeviceReadyListener readyListener;

        ConnectionThread(String macAddr, SerialHelper.DeviceReadyListener readyListener) {
            this.macAddr = macAddr;
            this.readyListener = readyListener;
        }

        @Override
        public void run() {

            mBtDevice = mBluetoothAdapter.getRemoteDevice(macAddr);
            if (mBtDevice == null) {
                // device does not exist
                Log.e(TAG, "Unable to open bluetooth device at " + macAddr);
                deviceConnected = false;
                readyListener.OnDeviceReady(false);
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
                readyListener.OnDeviceReady(false);
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
                readyListener.OnDeviceReady(false);
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
                readerThread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        int available = 0;
                        byte[] buffer = new byte[256];
                        while (deviceConnected) {
                            try {
                                available = serialIn.read(buffer);
                                dataReceivedListener.OnDataReceived(Arrays.copyOfRange(buffer, 0, available));

                            } catch (IOException e) {
                                Log.d(TAG, "Error reading from bluetooth device", e);
                                return;
                            }
                        }
                    }
                });
                readerThread.setPriority(Process.THREAD_PRIORITY_BACKGROUND);
                readerThread.start();
            }

            readyListener.OnDeviceReady(deviceConnected);
        }
    }
}
