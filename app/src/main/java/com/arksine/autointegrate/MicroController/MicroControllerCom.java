package com.arksine.autointegrate.microcontroller;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.arksine.autointegrate.MainService;
import com.arksine.autointegrate.interfaces.MCUControlInterface;
import com.arksine.autointegrate.interfaces.SerialHelper;
import com.arksine.autointegrate.R;
import com.arksine.autointegrate.utilities.BluetoothHelper;
import com.arksine.autointegrate.utilities.DLog;
import com.arksine.autointegrate.utilities.SerialCom;
import com.arksine.autointegrate.utilities.UsbHelper;
import com.arksine.autointegrate.utilities.UsbSerialSettings;

import java.nio.ByteBuffer;

/* TODO: I have decided that in an effort to reduce the number of USB devices connected to the
* Android tablet it would be a good idea to have the option to use the MCU and one of its UARTs
* to interface with the HD Radio.  This means that I can't use the control characters (<, >, :) that
* I have been using, because its possible they could be included in the HDRadio data.  This is going
* to make parsing much more complex
*
* I have decided to use non-printable ASCII values as control characters, hopefully it will work.
* /


/**
 * Class MicroControllerCom
 *
 * This class handles serial communication with the micro controller.  First, it establishes
 * a serial connection and confirms that the micro controller is connected.
 */
public class MicroControllerCom extends SerialCom {



    private static final String TAG = "MicroControllerCom";

    private ControllerInputHandler mInputHandler;
    private MCUControlInterface mControlInterface;

    // Broadcast reciever to listen for write commands.
    public class WriteReciever extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (mService.getString(R.string.ACTION_SEND_DATA).equals(action)) {
                // stops all queued services
                String command = intent.getStringExtra(mService.getString(R.string.EXTRA_COMMAND));
                String data = intent.getStringExtra(mService.getString(R.string.EXTRA_DATA));
                String out = "<" + command + ":" + data + ">";
                mSerialHelper.writeString(out);
            }
        }
    }
    private final WriteReciever writeReciever = new WriteReciever();
    private boolean isWriteReceiverRegistered = false;

    private ByteBuffer mReceivedBuffer = ByteBuffer.allocate(512);
    private volatile boolean mIsLengthByte = false;
    private volatile boolean mIsEscapedByte = false;
    private volatile boolean mIsValidPacket = false;
    private volatile int mPacketLength = 0;
    private volatile int mChecksum = 0;

    public MicroControllerCom(MainService svc, boolean learningMode) {
        super(svc);

        HandlerThread thread = new HandlerThread("ControllerMessageHandler",
                Process.THREAD_PRIORITY_BACKGROUND);
        thread.start();
        Looper mInputLooper = thread.getLooper();

        mControlInterface = buildInterface();

        mInputHandler = new ControllerInputHandler(mInputLooper, mService, mControlInterface,
                learningMode);

        mCallbacks = new SerialHelper.Callbacks() {
            @Override
            public void OnDeviceReady(boolean deviceReadyStatus) {
                mConnected = deviceReadyStatus;
                resumeThread();
            }

            @Override
            public void OnDataReceived(byte[] data) {
                // TODO: I should move this to the handler
                // TODO: I should include the header in the checksum calculation (need to do it in sketch as well)
                // add the incoming bytes to a buffer
                for (byte ch : data) {
                    if (ch == (byte) 0xF1) {
                        mIsValidPacket = true;
                        mIsEscapedByte = false;
                        mIsLengthByte = true;

                        mReceivedBuffer.clear();
                        mPacketLength = 0;
                        mChecksum = 0;
                    } else if (!mIsValidPacket) {
                      DLog.i(TAG, "Invalid byte received: " + ch);
                    } else if (ch == (byte)0x1B && !mIsEscapedByte) {
                        mIsEscapedByte = true;
                    } else {
                        if (mIsEscapedByte) {
                            mIsEscapedByte = false;
                            if (ch == (byte)0x20) {
                                // 0xF1 is escaped as 0x20
                                ch = (byte) 0xF1;
                            }
                            // Note: 0x1B is escaped as 0x1B, so we don't need to reset the current byte
                        }

                        if (mIsLengthByte) {
                            mIsLengthByte = false;
                            mPacketLength = ch & (0xFF);
                            mChecksum = mPacketLength;
                        } else if (mReceivedBuffer.position() == mPacketLength) {
                           // This is the checksum byte

                            // Checksum is all bytes added up (not counting header and escape bytes) mod 256
                            if ((mChecksum % 256) == (ch & 0xFF)) {
                                Message msg = mInputHandler.obtainMessage();
                                mReceivedBuffer.flip();
                                byte[] buf = new byte[mReceivedBuffer.limit()];
                                mReceivedBuffer.get(buf);
                                msg.obj = buf;
                                mInputHandler.sendMessage(msg);

                            } else {
                                Log.i(TAG, "Invalid checksum, discarding packet");
                            }

                            // The next byte received must be 0xF1, regardless of what happened here
                            mIsValidPacket = false;
                        } else {
                            // Add byte to packet buffer
                            mReceivedBuffer.put(ch);
                            mChecksum += (ch & 0xFF);
                        }
                    }
                }
            }

            @Override
            public void OnDeviceError() {
                DLog.i(TAG, "Device Error, disconnecting");
                mDeviceError = true;
                Intent refreshConnection = new Intent(mService
                        .getString(R.string.ACTION_REFRESH_CONTROLLER_CONNECTION));
                LocalBroadcastManager.getInstance(mService).sendBroadcast(refreshConnection);
            }
        };


    }

    @Override
    public boolean connect() {

        // If we are currently connected to a device, we need to disconnect.
        if (mSerialHelper != null && mSerialHelper.isDeviceConnected()) {
            disconnect();
        }

        final SharedPreferences sharedPrefs =
                PreferenceManager.getDefaultSharedPreferences(mService);

        // No device selected, exit
        final String devId = sharedPrefs.getString("controller_pref_key_select_device", "NO_DEVICE");
        if (devId.equals("NO_DEVICE")){
            DLog.v(TAG, "No device selected");
            return false;
        }

        String deviceType = sharedPrefs.getString("controller_pref_key_select_device_type", "BLUETOOTH");
        if (deviceType.equals("BLUETOOTH")) {
            // user selected bluetooth device
            mSerialHelper = new BluetoothHelper(mService);
        }
        else {

            String baudrate = PreferenceManager.getDefaultSharedPreferences(mService)
                    .getString("controller_pref_key_select_baud", "9600");
            // user selected usb device
            UsbSerialSettings settings = new UsbSerialSettings(Integer.valueOf(baudrate));
            mSerialHelper = new UsbHelper(mService, settings);

        }

        /**
         * Attept to connect to the device.  If we the prerequisites are met to attempt connection,
         * we'll wait until the connection thread notifies it is done.
          */
        DLog.v(TAG, "Attempting connection to device:\n" + devId);
        if (mSerialHelper.connectDevice(devId, mCallbacks)) {

            // wait until the connection is finished.  Only wait if the
            synchronized (this) {
                try {
                    mIsWaiting = true;
                    wait(30000);
                } catch (InterruptedException e) {
                    Log.w(TAG, e.getMessage());
                }
            }
        } else {
            mConnected = false;
        }

        if (mConnected) {
            mDeviceError = false;

            // Tell the Arudino that it is time to start
            if (!mSerialHelper.writeString("<START>")) {
                // unable to write start command
                Log.w(TAG, "Unable to start Micro Controller");
                mSerialHelper.disconnect();
                mConnected = false;
                mSerialHelper = null;
            } else {   // Connection was successful

                //Register write data receiver
                IntentFilter sendDataFilter = new IntentFilter(mService.getString(R.string.ACTION_SEND_DATA));
                mService.registerReceiver(writeReciever, sendDataFilter);
                isWriteReceiverRegistered = true;

                DLog.v(TAG, "Sucessfully connected to Micro Controller");
                // TODO: need to request dimmer status
            }
        } else {
            mSerialHelper = null;
        }

        return mConnected;

    }

    @Override
    public void disconnect() {
        mConnected = false;

        if (mSerialHelper!= null) {
            mInputHandler.close();
            // If there was a device error then we cannot write to it
            if (!mDeviceError) {
                mSerialHelper.writeString("<STOP>");

                // If we disconnect too soon after writing "stop", an exception will be thrown
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Log.w(TAG, e.getMessage());
                }

                mSerialHelper.disconnect();
            }
            mSerialHelper = null;
        }

        if (isWriteReceiverRegistered) {
            mService.unregisterReceiver(writeReciever);
            isWriteReceiverRegistered = false;
        }
    }

    private MCUControlInterface buildInterface() {
        return new MCUControlInterface() {
            @Override
            public void sendMcuCommand(String command, String data) {
                // TODO: probably need to syncronize this.
                if (mSerialHelper != null) {
                    String packet = "<" + command + ":" + data + ">";
                    mSerialHelper.writeString(packet);
                }
            }

            @Override
            public void setMode(boolean isLearningMode) {
                mInputHandler.setMode(isLearningMode);
            }

            @Override
            public boolean isConnected() {
                return mConnected;
            }
        };
    }

    public MCUControlInterface getControlInterface() {
        return mControlInterface;
    }
}
