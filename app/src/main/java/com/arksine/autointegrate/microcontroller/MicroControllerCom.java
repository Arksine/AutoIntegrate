package com.arksine.autointegrate.microcontroller;

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
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.arksine.autointegrate.MainService;
import com.arksine.autointegrate.interfaces.MCUControlInterface;
import com.arksine.autointegrate.utilities.SerialHelper;
import com.arksine.autointegrate.R;
import com.arksine.autointegrate.utilities.BluetoothHelper;
import com.arksine.autointegrate.utilities.DLog;
import com.arksine.autointegrate.utilities.SerialCom;
import com.arksine.autointegrate.utilities.UsbHelper;
import com.arksine.autointegrate.utilities.UsbSerialSettings;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

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
    private static final String TAG = MicroControllerCom.class.getSimpleName();

    private ControllerInputHandler mInputHandler;
    private Handler mWriteHandler;
    private final Handler.Callback mWriteCallback = new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            MCUDefs.McuOutputCommand command = MCUDefs.McuOutputCommand
                    .getCommandFromOrdinal(msg.what);

            if (mSerialHelper != null) {
                ByteBuffer outPacket = ByteBuffer.allocate(50);
                outPacket.order(ByteOrder.LITTLE_ENDIAN);
                outPacket.put((byte)0xF1);  // put header

                int checksum = 0xF1;
                byte length;

                switch (command) {
                    case START:
                    case STOP:
                    case SET_DIMMER_ANALOG:
                    case SET_DIMMER_DIGITAL:
                    case AUDIO_SOURCE_HD:
                    case AUDIO_SOURCE_AUX:
                        length = 1;
                        outPacket.put(length);
                        outPacket.put(command.getByte());
                        checksum += length + command.getByte();
                        break;
                    case RADIO_SET_DTR:
                    case RADIO_SET_RTS:
                        if (msg.obj != null && !(msg.obj instanceof Boolean)) {
                            Log.d(TAG, "Cannot send command, data is not a byte array");
                            return true;
                        }
                        byte boolByte = ((boolean)msg.obj) ? (byte)0x01 : (byte)0x00;

                        length = 2;
                        outPacket.put(length);
                        outPacket.put(command.getByte());
                        outPacket.put(boolByte);
                        checksum += length + command.getByte() + boolByte;
                        break;
                    case  RADIO_SEND_PACKET:
                        if (msg.obj == null || !(msg.obj instanceof byte[])) {
                            Log.d(TAG, "Cannot send command, data is not a byte array");
                            return true;
                        }
                        byte[] out = (byte[])msg.obj;
                        length = (byte)(out.length + 1);   // packet length plus command byte
                        checksum += length + command.getByte();
                        checkEscapeByte(outPacket, length);
                        outPacket.put(command.getByte());

                        for (byte b : out) {
                            checkEscapeByte(outPacket, b);
                            checksum += b;
                        }

                        break;
                    case CUSTOM:
                        // arg1 is the custom command
                        byte custom = (byte)msg.arg1;
                        length = 2;
                        if (msg.obj != null && (msg.obj instanceof byte[])) {
                            length += ((byte[])msg.obj).length;
                            checksum += length + command.getByte() + custom;
                            checkEscapeByte(outPacket, length);
                            outPacket.put(command.getByte());
                            checkEscapeByte(outPacket, custom);

                            for (byte b : (byte[])msg.obj) {
                                checkEscapeByte(outPacket, b);
                                checksum += b;
                            }
                        } else {
                            // no data, send only command
                            checksum += length + command.getByte() + custom;
                            checkEscapeByte(outPacket, length);
                            outPacket.put(command.getByte());
                            checkEscapeByte(outPacket, custom);
                        }

                        break;
                    default:
                        Log.d(TAG, "Unknown Command, cannot send");
                        return true;
                }

                byte chk = (byte)(checksum % 256);
                checkEscapeByte(outPacket, chk);

                outPacket.flip();
                byte[] outBuf = new byte[outPacket.limit()];
                outPacket.get(outBuf);
                mSerialHelper.writeBytes(outBuf);
            }

            return true;
        }

        private void checkEscapeByte(ByteBuffer outPacket, byte b) {
            if (b == (byte)0xF1) {
                outPacket.put((byte)0x1A);
                outPacket.put((byte)0x20);
            } else if (b == (byte)0x1A) {
                outPacket.put((byte)0x1A);
                outPacket.put((byte)0x1A);
            } else {
                outPacket.put(b);
            }
        }
    };

    private final MCUControlInterface mControlInterface = new MCUControlInterface() {
        @Override
        public void sendMcuCommand(MCUDefs.McuOutputCommand command, Object data) {

            Message msg = mInputHandler.obtainMessage(command.ordinal(), data);
            mWriteHandler.sendMessage(msg);
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

    // Broadcast reciever to listen for write commands.
    public class WriteReciever extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (mService.getString(R.string.ACTION_SEND_DATA).equals(action)) {
                // TODO: should the write receiver allow app commands, and only accept custom
                // commands?  App commands should only be sent by apps bound with the service
                // or via localbroadcasts anyway

                // stops all queued services
                byte command = intent.getByteExtra(mService.getString(R.string.EXTRA_COMMAND), (byte)0x00);
                MCUDefs.McuOutputCommand cmd = MCUDefs.McuOutputCommand.getCommand(command);

                Message msg = mInputHandler.obtainMessage();
                switch (cmd) {
                    case NONE:
                        // The command was not found, in the enumeration, so it will be sent as custom
                        msg.what = MCUDefs.McuOutputCommand.CUSTOM.ordinal();
                        msg.arg1 = command;
                        msg.obj = intent.getByteArrayExtra(mService.getString(R.string.EXTRA_DATA));
                        break;
                    case RADIO_SEND_PACKET:
                        msg.what = cmd.ordinal();
                        msg.obj = intent.getByteArrayExtra(mService.getString(R.string.EXTRA_DATA));
                        break;
                    case RADIO_SET_DTR:
                    case RADIO_SET_RTS:
                        msg.what = cmd.ordinal();
                        msg.obj = intent.getBooleanArrayExtra(mService.getString(R.string.EXTRA_DATA));
                        break;
                    default:
                        msg.what = cmd.ordinal();
                }

                mWriteHandler.sendMessage(msg);

            }
        }
    }
    private final WriteReciever writeReciever = new WriteReciever();
    private boolean isWriteReceiverRegistered = false;



    public MicroControllerCom(MainService svc, boolean learningMode) {
        super(svc);

        HandlerThread inputThread = new HandlerThread("ControllerMessageHandler",
                Process.THREAD_PRIORITY_BACKGROUND);
        inputThread.start();
        mInputHandler = new ControllerInputHandler(inputThread.getLooper(), mService,
                mControlInterface, learningMode);

        HandlerThread writeThread = new HandlerThread("Write Handler Thread",
                Process.THREAD_PRIORITY_BACKGROUND);
        writeThread.start();
        mWriteHandler = new Handler(writeThread.getLooper(), mWriteCallback);

        mCallbacks = new SerialHelper.Callbacks() {
            @Override
            public void OnDeviceReady(boolean deviceReadyStatus) {
                mConnected = deviceReadyStatus;
                resumeThread();
            }

            @Override
            public void OnDataReceived(byte[] data) {
                if (data.length > 0) {
                    Message msg = mInputHandler.obtainMessage();
                    msg.obj = data;
                    mInputHandler.sendMessage(msg);
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
            mControlInterface.sendMcuCommand(MCUDefs.McuOutputCommand.START, null);
            // TODO: Should I wait for a response?

            //Register write data receiver
            IntentFilter sendDataFilter = new IntentFilter(mService.getString(R.string.ACTION_SEND_DATA));
            mService.registerReceiver(writeReciever, sendDataFilter);
            isWriteReceiverRegistered = true;

            DLog.v(TAG, "Sucessfully connected to Micro Controller");


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
                mControlInterface.sendMcuCommand(MCUDefs.McuOutputCommand.STOP, null);

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

    public MCUControlInterface getControlInterface() {
        return mControlInterface;
    }
}
