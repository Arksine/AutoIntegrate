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

import com.arksine.autointegrate.AutoIntegrate;
import com.arksine.autointegrate.MainService;
import com.arksine.autointegrate.interfaces.MCUControlInterface;
import com.arksine.autointegrate.interfaces.McuLearnCallbacks;
import com.arksine.autointegrate.interfaces.ServiceControlInterface;
import com.arksine.autointegrate.utilities.SerialHelper;
import com.arksine.autointegrate.R;
import com.arksine.autointegrate.utilities.BluetoothHelper;
import com.arksine.autointegrate.utilities.SerialCom;
import com.arksine.autointegrate.utilities.UsbHelper;
import com.arksine.autointegrate.utilities.UsbSerialSettings;
import com.arksine.autointegrate.microcontroller.MCUDefs.*;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import timber.log.Timber;

/**
 * Class MicroControllerCom
 *
 * This class handles serial communication with the micro controller.  First, it establishes
 * a serial connection and confirms that the micro controller is connected.
 */
public class MicroControllerCom extends SerialCom {

    private static final int CONNECTION_TIMEOUT = 60000;
    private static final int DEVICE_RESP_TIMEOUT = 5000;

    private String mMcuId = "NOT SET";
    private AtomicReference<McuRadioDriver> mMcuRadioDriver = new AtomicReference<>(null);
    private AtomicBoolean mRadioStatus = new AtomicBoolean(false);

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
            mRadioStatus.set(status);
            mControlInterface.resumeFromWait();
        }

        @Override
        public void OnRadioDataReceived(byte[] radioData) {
            if (mMcuRadioDriver.get() != null) {
                mMcuRadioDriver.get().readBytes(radioData);
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
                            Timber.w("Cannot send command, data is not a byte array");
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
                            Timber.w("Cannot send command, data is not a byte array");
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
                        Timber.i("Unknown Command, cannot send");
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
        public void setMode(boolean isLearningMode, McuLearnCallbacks cbs) {
            mInputHandler.setMode(isLearningMode, cbs);
        }

        @Override
        public void updateBaud(int baud) {
            mSerialHelper.setBaud(baud);
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
                        this.wait(DEVICE_RESP_TIMEOUT);
                    } catch (InterruptedException e) {
                        Timber.w(e);
                    } finally {
                        // Error, response from MCU Timed out
                        if (mControlWait.compareAndSet(true, false)) {
                            Timber.e("Radio Enabled request timed out");
                            mRadioStatus.set(false);
                            mMcuRadioDriver.set(null);
                        }
                    }

                    if (mRadioStatus.get()) {
                        mMcuRadioDriver.set(radioDriver);
                    } else {
                        mMcuRadioDriver.set(null);
                    }
                }
            } else {
                // Radio Driver is disabled
                mRadioStatus.set(false);
                mMcuRadioDriver.set(null);
            }
            return mRadioStatus.get();
        }

        @Override
        public boolean isConnected() {
            return mConnected.get();
        }

        @Override
        public String getDeviceId() {
            return mMcuId;
        }

        @Override
        public void updateButtonMap(List<ResistiveButton> buttonList) {
            mInputHandler.updateButtonMap(buttonList);
        }

        @Override
        public void updateDimmerMap(int mode, int highReading, int lowReading,
                                    int highBrightness, int lowBrightness) {
            mInputHandler.updateDimmerMap(mode, highReading, lowReading,
                    highBrightness, lowBrightness);
        }

        @Override
        public void updateReverseMap(String camSetting, String appPackage) {
            mInputHandler.updateReverseCameraMap(camSetting, appPackage);
        }

    };

    // Broadcast receiver to listen for write commands.
    public class WriteReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (mService.getString(R.string.ACTION_CUSTOM_MCU_COMMAND).equals(action)) {
                // Custom command received to be sent to the microcontroller
                byte command = intent.getByteExtra(mService.getString(R.string.EXTRA_COMMAND), (byte)0x00);

                Message msg = mInputHandler.obtainMessage();
                msg.what = McuOutputCommand.CUSTOM.ordinal();
                msg.arg1 = command;
                msg.obj = intent.getByteArrayExtra(mService.getString(R.string.EXTRA_DATA));

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
                mConnected.set(deviceReadyStatus);
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
                Timber.i("Device Error, disconnecting");
                mDeviceError.set(true);

                if (mMcuRadioDriver.get() != null) {
                    mMcuRadioDriver.get().flagConnectionError();
                }

                ServiceControlInterface serviceControl = AutoIntegrate.getServiceControlInterface();
                if (serviceControl != null) {
                    serviceControl.refreshMcuConnection(false, null);
                }
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
            Timber.i("Cannot open, No device selected");
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
         * Attept to connect to the device.  If  the prerequisites are met to attempt connection,
         * we'll wait until the connection thread notifies it is done.
          */
        Timber.d("Attempting connection to device:\n%s", devId);
        synchronized (this) {
            if (mSerialHelper.connectDevice(devId, mCallbacks)) {

                // wait until the connection is finished with a timeout of 60 seconds

                try {
                    mIsWaiting.set(true);
                    wait(CONNECTION_TIMEOUT);
                } catch (InterruptedException e) {
                    Timber.w(e);
                } finally {
                    // Error, response from MCU Timed out
                    if (mIsWaiting.compareAndSet(true, false)) {
                        Timber.d("Connection attempt interrupted");
                        mConnected.set(false);
                    }
                }

            } else {
                mConnected.set(false);
            }
        }

        if (mConnected.get()) {
            mDeviceError.set(false);

            synchronized (this) {
                // Tell the Arudino that it is time to start
                mControlInterface.sendMcuCommand(McuOutputCommand.START, null);

                // wait until the MCU returns its ID from the attempt to start
                try {
                    mIsWaiting.set(true);
                    wait(DEVICE_RESP_TIMEOUT);
                } catch (InterruptedException e) {
                    Timber.w(e);
                } finally {
                    // Error, response from MCU Timed out
                    if (mIsWaiting.compareAndSet(true, false)) {
                        Timber.e("START request timed out");
                        mDeviceError.set(true);
                    }
                }
            }

            if (mDeviceError.get()) {
                // If the response timed out, disconnect from device
                disconnect();

            } else {
                //Register write data receiver
                IntentFilter sendDataFilter = new IntentFilter(mService.getString(R.string.ACTION_CUSTOM_MCU_COMMAND));
                mService.registerReceiver(writeReceiver, sendDataFilter);
                isWriteReceiverRegistered = true;

                Timber.v("Sucessfully connected to Micro Controller");
            }


        } else {
            mSerialHelper = null;
        }

        return mConnected.get();

    }

    @Override
    public void disconnect() {
        mConnected.set(false);

        // if the Radio Driver is enabled, close it.
        if (mMcuRadioDriver.get() != null) {
            mMcuRadioDriver.get().close();
        }

        if (mSerialHelper!= null) {
            mInputHandler.close();
            // If there was a device error then we cannot write to it
            if (!mDeviceError.get()) {
                mControlInterface.sendMcuCommand(McuOutputCommand.STOP, null);

                // If we disconnect too soon after writing "stop", an exception will be thrown
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Timber.w(e);
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
