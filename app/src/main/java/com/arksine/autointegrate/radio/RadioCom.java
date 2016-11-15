package com.arksine.autointegrate.radio;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.arksine.autointegrate.MainService;
import com.arksine.autointegrate.R;
import com.arksine.autointegrate.interfaces.RadioControlCallback;
import com.arksine.autointegrate.interfaces.RadioControlInterface;
import com.arksine.autointegrate.interfaces.SerialHelper;
import com.arksine.autointegrate.utilities.SerialCom;
import com.arksine.autointegrate.utilities.UsbHelper;
import com.arksine.autointegrate.utilities.UsbSerialSettings;
import com.felhr.usbserial.UsbSerialInterface;

import java.util.Calendar;
import java.util.HashMap;

/**
 * HD Radio Serial Communications class.
 *
 * TODO:  Initially will attempt to use USBHelper class with USBSerial Lib for comms, but if it doesn't
 * work well i'll switch to FTDI's android library
 *
 */

public class RadioCom extends SerialCom {

    private static final String TAG = "RadioCom";
    private static final int STREAM_LOCK_TIMEOUT = 10;   // seconds


    private RadioMessageHandler mInputHandler;
    private RadioController mRadioController;

    // TODO: too much "burst" information is transmitted between the service and the activity/app
    //       to use intents.  Need to implement IPC using AIDL for apps not in the package context,
    //       however

    // Broadcast reciever to listen for write commands.
    public class RadioCommandReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (mService.getString(R.string.ACTION_SEND_RADIO_COMMAND).equals(action)) {
                // TODO: If I keep this Broadcast Receiver, I need to redo this function, as RadioController.buildPacket
                //       now expects Object data rather than string data.


                String command = intent.getStringExtra(mService.getString(R.string.EXTRA_COMMAND));
                String data = intent.getStringExtra(mService.getString(R.string.EXTRA_DATA));
                String op = intent.getStringExtra(mService.getString(R.string.EXTRA_OPERATION));

                int tuneSubChannel = 0;

                if (command.equals("seek")) {
                    String[] extras = data.split(":");
                    if (extras.length != 2) {
                        Log.e(TAG, "Extra data for seek command not formatted correctly");
                        return;
                    }
                    boolean seekAll = Boolean.parseBoolean(extras[1]);
                    mRadioController.setSeekAll(seekAll);
                    data = extras[0];
                } else if (command.equals("tune")) {
                    String[] extras = data.split(":");
                    if (extras.length == 3) {
                        // tune directly to frequency, format is band:frequency:subchannel
                        tuneSubChannel = Integer.parseInt(extras[2]);
                        data = extras[0] + ":" + extras[1];
                    }
                }

                byte[] radioPacket = mRadioController.buildRadioPacket(command, op, data);
                mSerialHelper.writeBytes(radioPacket);

                if (tuneSubChannel > 0) {
                    final int subCh = tuneSubChannel;
                    final byte[] subChPacket = mRadioController.buildRadioPacket("hd_sub_channel",
                            "set", String.valueOf(subCh));
                    mSerialHelper.writeBytes(subChPacket);

                    Thread checkHDStreamLockThread = new Thread(new Runnable() {
                        @Override
                        public void run() {
                            Calendar calendar = Calendar.getInstance();
                            int setTime = calendar.get(Calendar.SECOND);

                            // HD Streamlock can take time, retry every 100ms for 10 seconds
                            // to set the subchannel.
                            while ((subCh != (int) mRadioController.getHdValue("hd_sub_channel")) &&
                                    ((calendar.get(Calendar.SECOND) - setTime) < STREAM_LOCK_TIMEOUT )) {
                                // Try resetting every 100 ms
                                try {
                                    Thread.sleep(100);
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                                mSerialHelper.writeBytes(subChPacket);
                            }
                        }
                    });
                    checkHDStreamLockThread.start();
                }

            }
        }
    }
    private final RadioCommandReceiver radioCommandReceiver = new RadioCommandReceiver();
    private boolean isRadioCommandReceiverRegistered = false;

    public RadioCom(MainService service) {
        super(service);

        mRadioController = new RadioController(mService);

        HandlerThread thread = new HandlerThread("RadioMessageHandler",
                Process.THREAD_PRIORITY_BACKGROUND);
        thread.start();
        Looper mInputLooper = thread.getLooper();

        mInputHandler = new RadioMessageHandler(mInputLooper, mService, mRadioController);

        mCallbacks = new SerialHelper.Callbacks() {
            @Override
            public void OnDeviceReady(boolean deviceReadyStatus) {
                mConnected = deviceReadyStatus;
                resumeThread();
            }

            @Override
            public void OnDataReceived(byte[] data) {

                Message msg = mInputHandler.obtainMessage();
                msg.obj = data.clone();
                mInputHandler.sendMessage(msg);

            }

            @Override
            public void OnDeviceError() {
                Log.i(TAG, "Device Error, disconnecting");
                mDeviceError = true;
                Intent refreshConnection = new Intent(mService
                        .getString(R.string.ACTION_REFRESH_RADIO_CONNECTION));
                LocalBroadcastManager.getInstance(mService).sendBroadcast(refreshConnection);
            }
        };

        createRadioControlInterface();
    }

    private void createRadioControlInterface() {
        //TODO: implement interface functions

    }

    @Override
    public boolean connect() {
        // HDRadio Cable is an FTDI device that runs at 115200 Baud.
        // TODO: Test with RTS/CTS flow control, not sure if that will work
        UsbSerialSettings settings = new UsbSerialSettings(115200, UsbSerialInterface.FLOW_CONTROL_RTS_CTS);
        mSerialHelper = new UsbHelper(mService, settings);

        // Find MJS Cable
        String mjsCableLocation = findMJSDevice();
        if (mjsCableLocation == null) {
            Log.i(TAG, "MJS Cable not found");
            return false;
        }

        Log.d(TAG, "Attempting connection to MJS Cable: " + mjsCableLocation);
        if (mSerialHelper.connectDevice(mjsCableLocation, mCallbacks)) {

            // wait until the connection is finished or until timeout.
            synchronized (this) {
                try {
                    mIsWaiting = true;
                    wait(30000);
                } catch (InterruptedException e) {
                    Log.e(TAG, e.getMessage());
                }
            }
        } else {
            mConnected = false;
        }

        if (mConnected) {

            initRadioVars();

            //Register write data receiver
            IntentFilter sendDataFilter = new IntentFilter(mService.getString(R.string.ACTION_SEND_RADIO_COMMAND));
            mService.registerReceiver(radioCommandReceiver, sendDataFilter);
            isRadioCommandReceiverRegistered = true;
        } else {
            mSerialHelper = null;
        }

        return mConnected;
    }

    @Override
    public void disconnect() {
        mConnected = false;

        if (mSerialHelper!= null) {
            //mInputHandler.close();   // TODO: The current Handler doesn't have a close method, not sure if I need one

            // If there was a device error then we cannot write to it
            if (!mDeviceError) {
                // send shutdown command to radio if its on
                if ((boolean)mRadioController.getHdValue("power")) {
                    mSerialHelper.writeBytes(mRadioController.buildRadioPacket("power", "set", "false"));
                }

                // If we disconnect too soon after writing an exception will be thrown
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Log.e(TAG, e.getMessage());
                }

                mSerialHelper.disconnect();
            }
            mSerialHelper = null;
        }

        if (isRadioCommandReceiverRegistered) {
            mService.unregisterReceiver(radioCommandReceiver);
            isRadioCommandReceiverRegistered = false;
        }
    }

    private String findMJSDevice() {
        UsbManager manager = (UsbManager) mService.getSystemService(Context.USB_SERVICE);
        HashMap<String, UsbDevice> usbDeviceList = manager.getDeviceList();

        for (UsbDevice uDevice : usbDeviceList.values()) {
            if ((uDevice.getVendorId() == 1027) && (uDevice.getProductId() ==  37756)) {
                Log.v(TAG, "MJS Cable found");
                return uDevice.getDeviceName();
            }
        }

        // Nothing found, return null
        return null;
    }


    private void initRadioVars() {
        // Retreive current information available from radio (TODO:Save it to shared prefs on close?)
        sendRadioCommand("power", "get", null);
        sendRadioCommand("volume", "get", null);
        sendRadioCommand("mute", "get", null);
        sendRadioCommand("bass", "get", null);
        sendRadioCommand("treble", "get", null);
        sendRadioCommand("tune", "get", null);
        sendRadioCommand("hd_sub_channel", "get", null);
    }

    private void sendRadioCommand(String command, String operation, Object data) {
        byte[] radioPacket = mRadioController.buildRadioPacket(command, operation, data);
        if (radioPacket != null) {
            mSerialHelper.writeBytes(radioPacket);
        } else {
            Log.i(TAG, "Invalid Radio Packet");
        }
    }
}
