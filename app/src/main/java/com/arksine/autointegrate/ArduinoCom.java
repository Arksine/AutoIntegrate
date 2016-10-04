package com.arksine.autointegrate;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Point;
import android.hardware.display.DisplayManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Display;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.widget.Toast;

// TODO:  Need to make this a base class (SerialCom).  Then ArduinoCom and HDRadioCom can both
//        extend it.  Both will need their own thread to poll for data.
/**
 * Class ArduinoCom
 *
 * This class handles bluetooth serial communication with the arduino.  First, it establishes
 * a serial connection and confirms that the Arudino is connected.    After setup is complete, it will listen
 * for resistive touch screen events from the arudino and send them to the NativeInput class
 * where they can be handled by the uinput driver in the NDK.
 */
class ArduinoCom implements Runnable {

    private static final String TAG = "ArduinoCom";

    private volatile boolean mConnected = false;
    private Context mContext;

    private volatile boolean mRunning = false;

    private SerialHelper mSerialHelper;
    private SerialHelper.DeviceReadyListener readyListener;

    private InputHandler mInputHandler;
    private Looper mInputLooper;

    // Handler that receives messages from arudino and sends them
    // to NativeInput
    private final class InputHandler extends Handler {

        InputHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            ArduinoMessage message = (ArduinoMessage) msg.obj;

            Log.i(TAG, message.command);
            // TODO: parse message and execute command.  We will need a new class for this.
        }
    }

    // TODO: need a broadcast receiver that disconnects when the selected device in arduino_preferences is
    //       changed.

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

    /**
     * Container for a message recieved from the arduino.  There are two message types we can receive,
     * logging types and point types.  Logging types fill in the desc string, point types fill in
     * the TouchPoint.
     */
    private class ArduinoMessage {
        public String command;
        public String data;
    }

    @Override
    public void run() {
        listenForInput();
    }

    ArduinoCom(Context context) {

        mContext = context;

        HandlerThread thread = new HandlerThread("ArduinoMessageHandler",
                Process.THREAD_PRIORITY_BACKGROUND);
        thread.start();
        mInputLooper = thread.getLooper();
        mInputHandler = new InputHandler(mInputLooper);

        readyListener = new SerialHelper.DeviceReadyListener() {
            @Override
            public void OnDeviceReady(boolean deviceReadyStatus) {
                mConnected = deviceReadyStatus;
                resumeThread();
            }
        };

        connect();

    }

    public synchronized void resumeThread() {
        notify();
    }

    public boolean connect() {

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

        mSerialHelper.connectDevice(devId, readyListener);

        // wait until the connection is finished.  The readyListener is a callback that will
        // set the variable below and set mConnected to the connection status
        synchronized (this) {
            try {
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

            // Tell the Arudino that it is time to start
            if (!mSerialHelper.writeString("<START>")) {
                // unable to write start command
                Log.e(TAG, "Unable to start arduino");
                mSerialHelper.disconnect();
                mConnected = false;
                mSerialHelper = null;
            } else {
                mSerialHelper.publishConnection(HardwareReceiver.UsbDeviceType.ARDUINO);
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

	/**
     * This function listens for input until the running loop is broken.  It is only
     * called from the Objects run() function, which should never be called from
     * the main thread, as it is blocking
     */
    private void listenForInput() {

        mRunning = true;

        while (mRunning) {

            ArduinoMessage message = readMessage();
            if (message != null) {
                if (message.command.equals("LOG")) {
                    Log.i("Arduino", message.data);
                    // TODO: add a preference for this toasts like this and only show if the pref is selected
                    Toast.makeText(mContext, "Arduino Info, check logcat", Toast.LENGTH_SHORT).show();

                } else {

                    Message msg = mInputHandler.obtainMessage();
                    msg.obj = message;
                    mInputHandler.sendMessage(msg);
                }
            }

        }
    }

    // Reads a message from the arduino and parses it
    private ArduinoMessage readMessage() {

        int bytes = 0;
        byte[] buffer = new byte[256];
        byte ch;

        //TODO: rather than polling and reading each byte, should I implement wait()/notify()
        //      functionality?  I should be able to do this easily with USB, as it relies on a callback.
        //      Need to check bluetooth.

        // get the first byte, anything other than a '<' is trash and will be ignored
        while(mRunning && (mSerialHelper.readByte() != '<')) {
            // sleep for 50ms between polling
            try {
                Thread.sleep(5);
            } catch (InterruptedException e) {
                Log.e(TAG, e.getMessage());
            }
        }

        // First byte is good, capture the rest until we get to the end of the message
        while (mRunning && ((ch = mSerialHelper.readByte()) != '>')) {
            buffer[bytes] = ch;
            bytes++;
        }

        ArduinoMessage ardMsg;
        String message = new String(buffer, 0, bytes);
        String[] tokens = message.split(":");

        if (tokens.length == 2) {
            // command received or log message
            ardMsg = new ArduinoMessage();

            ardMsg.command = tokens[0];
            ardMsg.data = tokens[1];

        }
        else {
            if (mRunning) {
                // Only log an error if the device has been shut down, it always throws an
                // IOExeception when the socket is closed
                Log.e(TAG, "Issue parsing string, invalid data recd");
            }

            ardMsg = null;

        }

        return ardMsg;
    }

    void disconnect() {
        mRunning = false;

        if (mSerialHelper!= null) {
            mSerialHelper.writeString("<STOP>");
            mSerialHelper.disconnect();
            mConnected = false;
            mSerialHelper = null;
        }

        if (isWriteReceiverRegistered) {
            mContext.unregisterReceiver(writeReciever);
            isWriteReceiverRegistered = false;
        }
    }

}
