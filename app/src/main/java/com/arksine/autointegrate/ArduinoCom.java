package com.arksine.autointegrate;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

// TODO:  Create a base class serialcom that ArudinoCom extends.  Much of the functionality
//        is the same as will be necessary for RadioCom.  connect() would need to be
//        overridden, the Handler is different, and t
/**
 * Class ArduinoCom
 *
 * This class handles serial communication with the arduino.  First, it establishes
 * a serial connection and confirms that the Arudino is connected.
 */
class ArduinoCom  {

    private static final String TAG = "ArduinoCom";

    private volatile boolean mConnected = false;
    private Context mContext;

    private volatile boolean mIsWaiting = false;
    private volatile boolean mDeviceError = false;

    private SerialHelper mSerialHelper;
    private SerialHelper.Callbacks mCallbacks;

    private volatile byte[] mReceivedBuffer = new byte[256];
    private volatile int mReceivedBytes = 0;

    private ArduinoHandler mInputHandler;

    // Broadcast reciever to listen for write commands.
    public class WriteReciever extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (mContext.getString(R.string.ACTION_SEND_DATA).equals(action)) {
                // stops all queued services
                String command = intent.getStringExtra(mContext.getString(R.string.EXTRA_COMMAND));
                String data = intent.getStringExtra(mContext.getString(R.string.EXTRA_DATA));
                String out = "<" + command + ":" + data + ">";
                mSerialHelper.writeString(out);
            }
        }
    }
    private final WriteReciever writeReciever = new WriteReciever();
    private boolean isWriteReceiverRegistered = false;


    ArduinoCom(Context context) {

        mContext = context;

        HandlerThread thread = new HandlerThread("ArduinoMessageHandler",
                Process.THREAD_PRIORITY_BACKGROUND);
        thread.start();
        Looper mInputLooper = thread.getLooper();

        // TODO: When this
        mInputHandler = new ArduinoHandler(mInputLooper, mContext);

        mCallbacks = new SerialHelper.Callbacks() {
            @Override
            public void OnDeviceReady(boolean deviceReadyStatus) {
                mConnected = deviceReadyStatus;
                resumeThread();
            }

            @Override
            public void OnDataReceived(byte[] data) {
                // add the incoming bytes to a buffer
                for (byte ch : data) {
                    if (ch == '<') {
                        // start of packet, clear buffer
                        mReceivedBuffer = new byte[256];
                        mReceivedBytes =  0;
                    } else if (ch == '>') {
                        // end of packet, handle message
                        Message msg = mInputHandler.obtainMessage();
                        msg.obj = new String (mReceivedBuffer, 0 ,mReceivedBytes);
                        mInputHandler.sendMessage(msg);

                    } else {
                        // add byte to received buffer
                        mReceivedBuffer[mReceivedBytes] = ch;
                        mReceivedBytes++;
                    }
                }
            }

            @Override
            public void OnDeviceError() {
                mDeviceError = true;
                disconnect();
            }
        };

    }

    public synchronized void resumeThread() {
        if (mIsWaiting) {
            mIsWaiting = false;
            notify();
        }
    }

    public boolean connect() {

        // If we are currently connected to a device, we need to disconnect.
        if (mSerialHelper != null && mSerialHelper.isDeviceConnected()) {
            disconnect();
        }

        final SharedPreferences sharedPrefs =
                PreferenceManager.getDefaultSharedPreferences(mContext);

        // No device selected, exit
        final String devId = sharedPrefs.getString("arduino_pref_key_select_device", "NO_DEVICE");
        if (devId.equals("NO_DEVICE")){
            return false;
        }

        String deviceType = sharedPrefs.getString("arduino_pref_key_select_device_type", "BLUETOOTH");
        if (deviceType.equals("BLUETOOTH")) {
            // user selected bluetooth device
            mSerialHelper = new BluetoothHelper(mContext);
        }
        else {
            // user selected usb device
            mSerialHelper = new UsbHelper(mContext);

        }

        mSerialHelper.connectDevice(devId, mCallbacks);

        // wait until the connection is finished.  The onDeviceReady callback will
        // set mConnected to the connection status
        synchronized (this) {
            try {
                mIsWaiting = true;
                wait(30000);
            } catch (InterruptedException e) {
                Log.e(TAG, e.getMessage());
            }
        }

        if (mConnected) {
            //Register write data receiver
            IntentFilter sendDataFilter = new IntentFilter(mContext.getString(R.string.ACTION_SEND_DATA));
            mContext.registerReceiver(writeReciever, sendDataFilter);
            isWriteReceiverRegistered = true;

            mDeviceError = false;

            // Tell the Arudino that it is time to start
            if (!mSerialHelper.writeString("<START>")) {
                // unable to write start command
                Log.e(TAG, "Unable to start arduino");
                mSerialHelper.disconnect();
                mConnected = false;
                mSerialHelper = null;
            } else {
                // Its possible that usb device location changes, so we will put the most recently
                // connected Id in a preference that the Arudino Settings fragment can check
                PreferenceManager.getDefaultSharedPreferences(mContext).edit()
                        .putString("arduino_pref_key_connected_id", mSerialHelper.getConnectedId())
                        .apply();
                Log.i(TAG, "Sucessfully connected to Arduino");
            }
        } else {
            mSerialHelper = null;
        }

        return mConnected;

    }

    public boolean isConnected() {
        return mConnected;
    }

    void disconnect() {
        mConnected = false;

        if (mSerialHelper!= null) {
            // If there was a device error then we cannot write to it
            if (!mDeviceError) {
                mSerialHelper.writeString("<STOP>");

                // If we disconnect too soon after writing "stop", an exception will be thrown
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Log.e(TAG, e.getMessage());
                }

                mSerialHelper.disconnect();
            }
            mSerialHelper = null;
        }

        if (isWriteReceiverRegistered) {
            mContext.unregisterReceiver(writeReciever);
            isWriteReceiverRegistered = false;
        }
    }



}
