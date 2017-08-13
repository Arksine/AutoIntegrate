package com.arksine.autointegrate.microcontroller;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.Process;
import android.preference.PreferenceManager;
import android.util.Log;

import com.arksine.autointegrate.AutoIntegrate;
import com.arksine.autointegrate.MainService;
import com.arksine.autointegrate.interfaces.MCUControlInterface;
import com.arksine.autointegrate.interfaces.McuLearnCallbacks;
import com.arksine.autointegrate.interfaces.ServiceControlInterface;
import com.arksine.autointegrate.utilities.SerialHelper;
import com.arksine.autointegrate.R;
import com.arksine.autointegrate.utilities.BluetoothHelper;
import com.arksine.autointegrate.utilities.DLog;
import com.arksine.autointegrate.utilities.SerialCom;
import com.arksine.autointegrate.utilities.UsbHelper;
import com.arksine.autointegrate.utilities.UsbSerialSettings;
import com.arksine.autointegrate.microcontroller.MCUDefs.*;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Class MicroControllerCom
 *
 * This class handles serial communication with the micro controller.  First, it establishes
 * a serial connection and confirms that the micro controller is connected.
 */
public class MicroControllerCom extends SerialCom {
    private static final String TAG = MicroControllerCom.class.getSimpleName();

    private String mMcuId = "NOT SET";
    private McuRadioDriver mMcuRadioDriver = null;  // TODO: make this an atomic reverence
    private volatile boolean mRadioStatus = false;

    interface McuEvents {
        void OnStarted(String idStarted);
        void OnIdReceived(String id);
        void OnRadioStatusReceived(boolean status);
        void OnRadioDataReceived(byte[] radioData);
    }

    private final McuEvents mMcuEvents = new McuEvents() {
        @Override
        public void OnStarted(String id) {
            mMcuId = id;
            resumeThread();
        }

        @Override
        public void OnIdReceived(String id) {
            mMcuId = id;
        }

        @Override
        public void OnRadioStatusReceived(boolean status) {
            mRadioStatus = status;
            mControlInterface.resumeFromWait();
        }

        @Override
        public void OnRadioDataReceived(byte[] radioData) {
            if (mMcuRadioDriver != null) {
                mMcuRadioDriver.readBytes(radioData);
            }
        }
    };

    private ControllerInputHandler mInputHandler;
    private Handler mWriteHandler;
    private final Handler.Callback mWriteCallback = new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            McuOutputCommand command = McuOutputCommand.getCommandFromOrdinal(msg.what);

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
                    case REQUEST_ID:
                    case RADIO_REQUEST_STATUS:
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
        AtomicBoolean mControlWait = new AtomicBoolean(false);

        @Override
        public void sendMcuCommand(McuOutputCommand command, Object data) {

            Message msg = mInputHandler.obtainMessage(command.ordinal(), data);
            mWriteHandler.sendMessage(msg);
        }

        @Override
        public void setMode(boolean isLearningMode) {
            mInputHandler.setMode(isLearningMode);
        }

        @Override
        public synchronized void resumeFromWait() {
            if (mControlWait.compareAndSet(true, false)) {
                this.notify();
            }
        }

        @Override
        public boolean setRadioDriver(McuRadioDriver radioDriver) {
            if (radioDriver != null) {
                sendMcuCommand(McuOutputCommand.RADIO_REQUEST_STATUS, null);
                synchronized (this) {
                    try {
                        mControlWait.set(true);
                        this.wait(10000);
                    } catch (InterruptedException e) {
                        Log.w(TAG, e.getMessage());
                    } finally {
                        // Error, response from MCU Timed out
                        if (mControlWait.compareAndSet(true, false)) {
                            mRadioStatus = false;
                            mMcuRadioDriver = null;
                        }
                    }

                    if (mRadioStatus) {
                        mMcuRadioDriver = radioDriver;
                    }
                }
            } else {
                // Radio Driver is disabled
                mRadioStatus = false;
                mMcuRadioDriver = null;
            }
            return mRadioStatus;
        }

        @Override
        public boolean isConnected() {
            return mConnected;
        }

        @Override
        public String getDeviceId() {
            return mMcuId;
        }
    };

    // Broadcast receiver to listen for write commands.
    public class WriteReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (mService.getString(R.string.ACTION_SEND_DATA).equals(action)) {
                // TODO: should the write receiver allow app commands, and only accept custom
                // commands?  App commands should only be sent by apps bound with the service
                // or via localbroadcasts anyway

                // stops all queued services
                byte command = intent.getByteExtra(mService.getString(R.string.EXTRA_COMMAND), (byte)0x00);
                McuOutputCommand cmd = McuOutputCommand.getCommand(command);

                Message msg = mInputHandler.obtainMessage();
                switch (cmd) {
                    case NONE:
                        // The command was not found, in the enumeration, so it will be sent as custom
                        msg.what = McuOutputCommand.CUSTOM.ordinal();
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
    private final WriteReceiver writeReceiver = new WriteReceiver();
    private boolean isWriteReceiverRegistered = false;

    public MicroControllerCom(MainService svc, boolean learningMode, McuLearnCallbacks cbs) {
        super(svc);
        AutoIntegrate.setMcuControlInterface(this.mControlInterface);

        HandlerThread inputThread = new HandlerThread("ControllerMessageHandler",
                Process.THREAD_PRIORITY_BACKGROUND);
        inputThread.start();
        mInputHandler = new ControllerInputHandler(inputThread.getLooper(), mService,
                mMcuEvents, learningMode, cbs);

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
                ServiceControlInterface serviceControl = AutoIntegrate.getServiceControlInterface();
                serviceControl.refreshMcuConnection(false, null);
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

            // wait until the connection is finished with a timeout of 10 seconds
            synchronized (this) {
                try {
                    mIsWaiting = true;
                    wait(10000);
                } catch (InterruptedException e) {
                    Log.w(TAG, e.getMessage());
                } finally {
                    // Error, response from MCU Timed out
                    if (mIsWaiting) {
                        mIsWaiting = false;
                        mConnected = false;
                    }
                }
            }
        } else {
            mConnected = false;
        }

        if (mConnected) {
            mDeviceError = false;

            // Tell the Arudino that it is time to start
            mControlInterface.sendMcuCommand(McuOutputCommand.START, null);

            // wait until the MCU returns its ID from the attempt to start
            synchronized (this) {
                try {
                    mIsWaiting = true;
                    wait(10000);
                } catch (InterruptedException e) {
                    Log.w(TAG, e.getMessage());
                } finally {
                    // Error, response from MCU Timed out
                    if (mIsWaiting) {
                        mIsWaiting = false;
                        mDeviceError = true;
                    }
                }
            }

            if (mDeviceError) {
                // If the response timed out, disconnect from device
                disconnect();

            } else {
                //Register write data receiver
                IntentFilter sendDataFilter = new IntentFilter(mService.getString(R.string.ACTION_SEND_DATA));
                mService.registerReceiver(writeReceiver, sendDataFilter);
                isWriteReceiverRegistered = true;

                DLog.v(TAG, "Sucessfully connected to Micro Controller");
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
                mControlInterface.sendMcuCommand(McuOutputCommand.STOP, null);

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
            mService.unregisterReceiver(writeReceiver);
            isWriteReceiverRegistered = false;
        }
    }

    public MCUControlInterface getControlInterface() {
        return mControlInterface;
    }

}
