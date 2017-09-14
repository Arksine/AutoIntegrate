package com.arksine.autointegrate.utilities;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.ParcelUuid;
import android.support.v4.content.LocalBroadcastManager;
import android.widget.Toast;

import com.arksine.autointegrate.R;

import java.io.Closeable;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import timber.log.Timber;


/**
 * Class BluetoothHelper - Handles basic bluetooth tasks, implements async read/write
 */
public class BluetoothHelper extends SerialHelper {

    private final Object WRITELOCK = new Object();

    private ExecutorService EXECUTOR = null;
    private Future mReaderThreadFuture = null;
    private AtomicBoolean mIsWaiting = new AtomicBoolean(false);

    private Context mContext = null;

    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothSocket mSocket;
    private AtomicReference<BluetoothDevice> mBtDevice = new AtomicReference<>(null);
    private AtomicBoolean mReadThreadRunning = new AtomicBoolean(false);

    private InputStream mSerialIn;
    private OutputStream mSerialOut;


    private SerialHelper.Callbacks mSerialHelperCallbacks;

    private final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");


    private boolean mIsReceiverRegistered = false;
    private BroadcastReceiver mBtAdapterStatusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(context.getString(R.string.ACTION_BT_ADAPTER_ON))) {
                notifyThread();
            }
        }
    };

    public BluetoothHelper(Context context){
        mContext = context;
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
    @Override
    public void disconnect() {

        mReadThreadRunning.set(false);
        closeItem(mSocket);
        closeItem(mSerialIn);
        closeItem(mSerialOut);

        if (EXECUTOR != null) {
            EXECUTOR.shutdown();

            try {
               EXECUTOR.awaitTermination(10, TimeUnit.SECONDS);
            } catch (Exception e) {
                Timber.w(e);
            }

            if (!EXECUTOR.isTerminated()) {
                EXECUTOR.shutdownNow();
            }

            EXECUTOR = null;
        }

        mBtDevice.set(null);
        mSocket = null;
        mSerialIn = null;
        mSerialOut = null;

        if (mIsReceiverRegistered) {
            mIsReceiverRegistered = false;
            LocalBroadcastManager.getInstance(mContext).unregisterReceiver(mBtAdapterStatusReceiver);
        }

    }

    public boolean isBluetoothOn() {
        return mBluetoothAdapter != null && mBluetoothAdapter.isEnabled();
    }

    @Override
    public ArrayList<String> enumerateSerialDevices() {

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
                ParcelUuid[] features = device.getUuids();
                for (ParcelUuid uuid : features) {
                    Timber.v(uuid.toString());
                }

                // Add the name and address to an array adapter to show in a ListView
                mAdapterList.add(device.getName() + "\n" + device.getAddress());
            }
        }

        return mAdapterList;
    }

    /**
     * Retrieves a bluetooth socket from a specified device.  Returns true if an attempt to connect
     * will be attempted, false if the prerequisites to attempt connection are not met.
     *
     * @param macAddr - The mac address of the device to connect
     */
    @Override
    public boolean connectDevice (String macAddr, SerialHelper.Callbacks cbs) {
        if (isDeviceConnected()) {
            disconnect();
        }

        EXECUTOR = Executors.newCachedThreadPool(new BackgroundThreadFactory());
        mSerialHelperCallbacks = cbs;

        /**
         *  If the adapter is turning on, wait until its completed.  Timeout in 10 seconds.
         */
        if (mBluetoothAdapter.getState() == BluetoothAdapter.STATE_TURNING_ON) {
            synchronized (this) {
                try {
                    // shouldn't take 10 seconds to turn on
                    mIsWaiting.set(true);
                    wait(10000);
                } catch (InterruptedException e) {
                    Timber.e(e);
                } finally {
                    if (mIsWaiting.compareAndSet(true, false)) {
                        Timber.e("Attempt to turn on BT adapter interruped/timed out");
                    }
                }
            }
        }

        if (!isBluetoothOn()) {
            return false;
        }

        ConnectionThread btConnectThread = new ConnectionThread(macAddr);
        EXECUTOR.execute(btConnectThread);

        if (!mIsReceiverRegistered) {
            IntentFilter filter = new IntentFilter(mContext.getString(R.string.ACTION_BT_ADAPTER_ON));
            LocalBroadcastManager.getInstance(mContext)
                    .registerReceiver(mBtAdapterStatusReceiver, filter);
        }

        return true;
    }

    private synchronized void notifyThread() {
        if (mIsWaiting.compareAndSet(true, false)) {
            notify();
        }
    }

    @Override
    public String getConnectedId() {
        if (isDeviceConnected()) {
            return mBtDevice.get().getAddress();
        }

        return "";
    }


    private void closeItem(Closeable item) {
        if (item != null) {
            try {
                item.close();
            }
            catch (IOException e) {
                Timber.w(e);
            }
        }
    }

    public boolean isDeviceConnected() {
        return mBtDevice.get() != null;
    }

    @Override
    public boolean writeBytes(final byte[] data) {
        if (mSocket == null) return false;


        EXECUTOR.execute(new Runnable() {
            @Override
            public void run() {

                synchronized (WRITELOCK) {
                    try {
                        mSerialOut.write(data);
                    } catch (IOException e) {
                        Timber.w("Error writing to device\n", e);
                        mSerialHelperCallbacks.OnDeviceError();
                    }
                }
            }
        });

        return true;
    }

    @Override
    public boolean writeString(String data) {
        return writeBytes(data.getBytes());
    }

    @Override
    public void toggleDTR(boolean state) {
        // Stub, DTR cannot be toggled for BT devices
    }

    @Override
    public void toggleRTS(boolean state) {
        // Stub, RTS cannot be toggled for BT devices
    }

    @Override
    public void setBaud(int baud) {
        // Stub, baud cannot be set for BT devices
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

            BluetoothDevice btDevice = mBluetoothAdapter.getRemoteDevice(macAddr);
            if (btDevice == null) {
                // device does not exist
                Timber.i( "Unable to open bluetooth device at %s", macAddr);
                mSerialHelperCallbacks.OnDeviceReady(false);
                return;
            }

            // Attempt to create an insecure socket.  In the future I should probably
            // add an option for a secure connection, as this is subject to a man
            // in the middle attack.
            try {
                mSocket = btDevice.createInsecureRfcommSocketToServiceRecord(MY_UUID);
            }
            catch (IOException e) {
                Timber.i ("Unable to retrieve bluetooth socket for device %s", macAddr);
                mSocket = null;
                mSerialHelperCallbacks.OnDeviceReady(false);
                return;
            }

            mBluetoothAdapter.cancelDiscovery();

            try {
                // Connect the device through the socket. This will block
                // until it succeeds or throws an exception
                mSocket.connect();
            } catch (IOException connectException) {

                Timber.i ("Unable to connect to bluetooth socket for device %s", macAddr);
                // Unable to connect; onDisconnect the socket and get out
                closeItem(mSocket);

                mSocket = null;
                mSerialHelperCallbacks.OnDeviceReady(false);
                return;
            }

            // Get input stream
            try {
                mSerialIn = mSocket.getInputStream();
            } catch (IOException e) {
                mSerialIn = null;
            }

            // Get output stream
            try {
                mSerialOut = mSocket.getOutputStream();
            } catch (IOException e) {
                mSerialOut = null;
            }

            // start reader thread if connection is established
            if (mSerialOut != null && mSerialIn != null) {
                mReadThreadRunning.set(true);
                mBtDevice.set(btDevice);
                mReaderThreadFuture = EXECUTOR.submit(new Runnable() {
                    @Override
                    public void run() {
                        int available = 0;
                        byte[] buffer = new byte[256];
                        while (mReadThreadRunning.get()) {
                            try {
                                available = mSerialIn.read(buffer);
                                mSerialHelperCallbacks.OnDataReceived(Arrays.copyOfRange(buffer, 0, available));

                            } catch (IOException e) {
                                // connection was closed before the device was disconnected
                                if (mReadThreadRunning.get()) {
                                    Timber.w(e);
                                    mSerialHelperCallbacks.OnDeviceError();
                                }
                                return;
                            }
                        }
                    }
                });
            } else {
                closeItem(mSocket);
                closeItem(mSerialIn);
                closeItem(mSerialOut);
            }

            mSerialHelperCallbacks.OnDeviceReady(isDeviceConnected());
        }
    }
}
