package com.arksine.autointegrate.radio;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.os.RemoteException;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.arksine.autointegrate.MainService;
import com.arksine.autointegrate.R;
import com.arksine.autointegrate.interfaces.RadioControlInterface;
import com.arksine.autointegrate.interfaces.SerialHelper;
import com.arksine.autointegrate.utilities.DLog;
import com.arksine.autointegrate.utilities.SerialCom;
import com.arksine.autointegrate.utilities.UsbHelper;
import com.arksine.autointegrate.utilities.UsbSerialSettings;
import com.felhr.usbserial.UsbSerialInterface;

import java.util.HashMap;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

/**
 * HD Radio Serial Communications class.
 *
 */
public class RadioCom extends SerialCom {

    private static final String TAG = "RadioCom";
    private static final long STREAM_LOCK_TIMEOUT = 10000;   // milliseconds
    private static final long POWER_TOGGLE_DELAY = 2000;
    private static final long POST_TUNE_DELAY = 1000;
    private final Object SEND_COMMAND_LOCK = new Object();
    private final Object POWER_LOCK = new Object();


    private RadioMessageHandler mInputHandler;
    private RadioController mRadioController;
    private RadioControlInterface mRadioControlInterface;

    private volatile boolean mRadioReady = false;
    private volatile boolean mIsPoweredOn = false;
    private volatile long mPreviousPowerTime = 0;
    private volatile long mPreviousTuneTime = 0;

    // Broadcast reciever to listen for write commands.
    public class RadioCommandReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (mService.getString(R.string.ACTION_SEND_RADIO_COMMAND).equals(action)) {
                // TODO: Accept a limited number of commands(seek, tune up/down, maybe volume up down
                // power on off.   OR listen for  media button pushes
            }
        }
    }
    private final RadioCommandReceiver radioCommandReceiver = new RadioCommandReceiver();
    private boolean isRadioCommandReceiverRegistered = false;

    public RadioCom(MainService svc) {
        super(svc);

        mRadioController = new RadioController(mService);

        HandlerThread thread = new HandlerThread("RadioMessageHandler",
                Process.THREAD_PRIORITY_BACKGROUND);
        thread.start();
        Looper mInputLooper = thread.getLooper();

        mInputHandler = new RadioMessageHandler(mInputLooper, mRadioController);

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
                if (mConnected) {
                    Log.w(TAG, "Device Error, disconnecting");
                    // Execute callbacks for bound activities
                    int cbCount = mService.mRadioCallbacks.beginBroadcast();
                    for (int i = 0; i < cbCount; i++) {
                        try {
                            mService.mRadioCallbacks.getBroadcastItem(i).OnError();
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }
                    }
                    mService.mRadioCallbacks.finishBroadcast();

                    mDeviceError = true;
                }
                Intent refreshConnection = new Intent(mService
                        .getString(R.string.ACTION_REFRESH_RADIO_CONNECTION));
                LocalBroadcastManager.getInstance(mService).sendBroadcast(refreshConnection);
            }
        };

        mRadioControlInterface = buildRadioInterface();
    }


    @Override
    public boolean connect() {
        // HDRadio Cable is an FTDI device that runs at 115200 Baud.  No Flow control works.
        UsbSerialSettings settings = new UsbSerialSettings(115200, UsbSerialInterface.FLOW_CONTROL_OFF);
        mSerialHelper = new UsbHelper(mService, settings);

        // Initial Check for MJS Cable
        String mjsCableLocation = findMJSDevice();
        if (mjsCableLocation == null) {
            return false;
        }

        //Register received to listen for radio commands
        IntentFilter sendDataFilter = new IntentFilter(mService.getString(R.string.ACTION_SEND_RADIO_COMMAND));
        mService.registerReceiver(radioCommandReceiver, sendDataFilter);
        isRadioCommandReceiverRegistered = true;

        boolean autoPower = PreferenceManager.getDefaultSharedPreferences(mService)
                .getBoolean("radio_pref_key_auto_power", false);

        // TODO: The Serial library automatically raises DTR (apparently not RTS) when connected.   Thus
        // if we don't want to auto power on we can't connect.  The proper solution is to switch
        // to the FTDI android library, which I will do when I write remove this and write the android library
        if (autoPower) {
            mRadioReady = powerOn();
        } else {
            // TODO: request USB device permission (maybe do it in the findMJSDevice() function
            mRadioReady = true;  // We assume the radio is ready if we got this far
        }
        return mRadioReady;
    }

    @Override
    public void disconnect() {
        if (mConnected) {
            synchronized (POWER_LOCK) {
                // Don't allow power off within 2 seconds of power on
                long powerDelay = (mPreviousPowerTime + POWER_TOGGLE_DELAY) - SystemClock.elapsedRealtime();
                DLog.v(TAG, "Power Off, previous power time " + mPreviousPowerTime + '\n' + powerDelay);
                if (powerDelay > 0) {
                    // sleep
                    try {
                        Thread.sleep(powerDelay);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }

                mConnected = false;
                if (mSerialHelper != null) {


                    // If there was a device error then we cannot write to it
                    if (!mDeviceError) {
                        // send power off callback
                        if (mIsPoweredOn) {
                            int cbCount = mService.mRadioCallbacks.beginBroadcast();
                            for (int i = 0; i < cbCount; i++) {
                                try {
                                    mService.mRadioCallbacks.getBroadcastItem(i).OnPowerOff();
                                } catch (RemoteException e) {
                                    e.printStackTrace();
                                }
                            }
                            mService.mRadioCallbacks.finishBroadcast();
                        }

                        // send shutdown command to radio if its on
                        ((UsbHelper)mSerialHelper).toggleDTR(false);

                        // If we disconnect too soon after writing an exception will be thrown
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException e) {
                            Log.w(TAG, e.getMessage());
                        }

                        mSerialHelper.disconnect();
                    }
                }

                mPreviousPowerTime = SystemClock.elapsedRealtime();
            }
        }

        mRadioReady = false;
        mIsPoweredOn = false;
        mSerialHelper = null;

        if (isRadioCommandReceiverRegistered) {
            mService.unregisterReceiver(radioCommandReceiver);
            isRadioCommandReceiverRegistered = false;
        }
    }

    public boolean isRadioReady() {
        return mRadioReady;
    }

    private String findMJSDevice() {
        UsbManager manager = (UsbManager) mService.getSystemService(Context.USB_SERVICE);
        HashMap<String, UsbDevice> usbDeviceList = manager.getDeviceList();

        for (UsbDevice uDevice : usbDeviceList.values()) {
            if ((uDevice.getVendorId() == 1027) && (uDevice.getProductId() ==  37752)) {
                DLog.v(TAG, "MJS Cable found");

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    return String.format(Locale.US, "%1$d:%2$d:%3$s", 1027, 37752,
                            uDevice.getSerialNumber());
                } else {
                    return String.format(Locale.US, "%1$d:%2$d", 1027, 37752);
                }
            }
        }

        // Nothing found, return null
        Log.i(TAG, "MJS Cable NOT found");
        return null;
    }

    private boolean finishConnection(String mjsCableLocation) {
        if (mConnected) {
            // already powered on
            return true;
        }

        // if we are passed a null string look for the device
        if (mjsCableLocation == null)
            mjsCableLocation = findMJSDevice();

        // if still null, cable is not found, exit
        if (mjsCableLocation == null) {
            mRadioReady = false;
            return false;
        }

        DLog.v(TAG, "Attempting connection to MJS Cable: " + mjsCableLocation);
        if (mSerialHelper.connectDevice(mjsCableLocation, mCallbacks)) {

            // wait until the connection is finished or until timeout.
            synchronized (this) {
                try {
                    mIsWaiting = true;
                    wait(30000);
                } catch (InterruptedException e) {
                    Log.w(TAG, e.getMessage());
                }
            }
        } else {
            mRadioReady = false;
            mConnected = false;
        }

        if (!mConnected) {
            Log.i(TAG, "Unable to connect to HD Radio");
        }

        return mConnected;
    }

    private boolean powerOn() {
        synchronized (POWER_LOCK) {
            long powerDelay = (mPreviousPowerTime + POWER_TOGGLE_DELAY) - SystemClock.elapsedRealtime();
            if (powerDelay > 0) {
                // sleep
                try {
                    Thread.sleep(powerDelay);
                    DLog.v(TAG, "Power delay, slept for: " + powerDelay);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            if (!mIsPoweredOn) {
                if (!mConnected) {
                    mIsPoweredOn = finishConnection(null);
                } else {

                    // Set the hardware mute so speakers dont get blown by the initial power on
                    ((UsbHelper) mSerialHelper).toggleRTS(true);

                    // Raise DTR to power on
                    ((UsbHelper) mSerialHelper).toggleDTR(true);
                    mIsPoweredOn = true;

                }


                if (mIsPoweredOn) {
                    initRadio();
                }
            }

            mPreviousPowerTime = SystemClock.elapsedRealtime();


            return mIsPoweredOn;
        }
    }

    // TODO: should probably put power off and power on in runnables that are post delayed.  Remove
    //       the callbacks if power is continously toggled?
    private void powerOff() {
        synchronized (POWER_LOCK) {
            long powerDelay = (mPreviousPowerTime + POWER_TOGGLE_DELAY) - SystemClock.elapsedRealtime();
            if (powerDelay > 0) {
                // sleep
                try {
                    Thread.sleep(powerDelay);
                    DLog.v(TAG, "Power delay, slept for: " + powerDelay);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            if (mConnected && mIsPoweredOn) {
                ((UsbHelper) mSerialHelper).toggleDTR(false);
                mIsPoweredOn = false;

                int cbCount = mService.mRadioCallbacks.beginBroadcast();
                for (int i = 0; i < cbCount; i++) {
                    try {
                        mService.mRadioCallbacks.getBroadcastItem(i).OnPowerOff();
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }
                mService.mRadioCallbacks.finishBroadcast();
            }

            mPreviousPowerTime = SystemClock.elapsedRealtime();
        }
    }

    /**
     * Initialize radio after power on.
     */
    private void initRadio() {

        // must sleep for 2 seconds before sending radio a request
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Log.w(TAG, e.getMessage());
        }

        if (DLog.DEBUG) {
            sendRadioCommand(RadioKey.Command.HD_UNIQUE_ID, RadioKey.Operation.GET, null);
            sendRadioCommand(RadioKey.Command.HD_HW_VERSION, RadioKey.Operation.GET, null);
            sendRadioCommand(RadioKey.Command.HD_API_VERSION, RadioKey.Operation.GET, null);
            /*sendRadioCommand(RadioKey.Command.VOLUME, RadioKey.Operation.GET, null);
            sendRadioCommand(RadioKey.Command.MUTE, RadioKey.Operation.GET, null);
            sendRadioCommand(RadioKey.Command.BASS, RadioKey.Operation.GET, null);
            sendRadioCommand(RadioKey.Command.TREBLE, RadioKey.Operation.GET, null);
            sendRadioCommand(RadioKey.Command.COMPRESSION, RadioKey.Operation.GET, null);
            sendRadioCommand(RadioKey.Command.HD_TUNER_ENABLED, RadioKey.Operation.GET, null);
            sendRadioCommand(RadioKey.Command.HD_ACTIVE, RadioKey.Operation.GET, null);
            sendRadioCommand(RadioKey.Command.TUNE, RadioKey.Operation.GET, null);*/
        }

        // release RTS (hardware mute)
        ((UsbHelper)mSerialHelper).toggleRTS(false);

        // Execute power on callback
        int cbCount = mService.mRadioCallbacks.beginBroadcast();
        for (int i = 0; i < cbCount; i++) {
            try {
                mService.mRadioCallbacks.getBroadcastItem(i).OnPowerOn();
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
        mService.mRadioCallbacks.finishBroadcast();

    }

    private void sendRadioCommand(RadioKey.Command command, RadioKey.Operation operation, Object data) {
        synchronized (SEND_COMMAND_LOCK) {
            byte[] radioPacket = mRadioController.buildRadioPacket(command, operation, data);
            if (radioPacket != null && mSerialHelper != null) {
                // Do not allow any command to execute within 1 second of a direct tune
                long tuneDelay = (mPreviousTuneTime + POST_TUNE_DELAY) - SystemClock.elapsedRealtime();
                if (tuneDelay > 0) {
                    // sleep
                    try {
                        Thread.sleep(tuneDelay);
                        DLog.v(TAG, "Post Tune delay, slept for: " + tuneDelay);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }

                mSerialHelper.writeBytes(radioPacket);


                // If a tune command with a tuneInfo object was received, it is a direct tune.
                // Set the timer
                if (command == RadioKey.Command.TUNE && data instanceof RadioController.TuneInfo) {
                    mPreviousTuneTime = SystemClock.elapsedRealtime();
                }

                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            } else {
                Log.i(TAG, "Invalid Radio Packet");
            }
        }
    }

    public RadioControlInterface getRadioInterface() {
        return mRadioControlInterface;
    }


    public RadioControlInterface buildRadioInterface() {

        return new RadioControlInterface() {
            @Override
            public void setSeekAll(boolean seekAll) {
                mRadioController.setSeekAll(seekAll);
            }

            @Override
            public boolean getSeekAll() {
                return mRadioController.getSeekAll();
            }

            @Override
            public void togglePower(boolean status) {
                if (status) {
                    powerOn();
                } else {
                    powerOff();
                }
            }

            @Override
            public boolean getPowerStatus() {
                return mIsPoweredOn;
            }

            @Override
            public void toggleMute(boolean status) {
                sendRadioCommand(RadioKey.Command.MUTE, RadioKey.Operation.SET, status);
            }

            @Override
            public void toggleHardwareMute(boolean status) {
                if (mConnected) {
                    ((UsbHelper)mSerialHelper).toggleRTS(status);
                }
            }

            @Override
            public void setVolume(int volume) {
                sendRadioCommand(RadioKey.Command.VOLUME, RadioKey.Operation.SET, volume);
            }

            @Override
            public void setVolumeUp() {

                //sendRadioCommand(RadioKey.Command.VOLUME, RadioKey.Operation.SET, RadioKey.Constant.UP);
                int volume = mRadioController.getVolume();
                volume++;

                if (volume <= 90) {
                    sendRadioCommand(RadioKey.Command.VOLUME, RadioKey.Operation.SET, volume);
                }
            }

            @Override
            public void setVolumeDown() {
                //sendRadioCommand(RadioKey.Command.VOLUME, RadioKey.Operation.SET, RadioKey.Constant.DOWN);
                int volume = mRadioController.getVolume();
                volume--;

                if (volume >= 0) {
                    sendRadioCommand(RadioKey.Command.VOLUME, RadioKey.Operation.SET, volume);
                }

            }

            @Override
            public void setBass(int bass) {
                sendRadioCommand(RadioKey.Command.BASS, RadioKey.Operation.SET, bass);
            }

            @Override
            public void setBassUp() {
                int bass = (int)mRadioController.getBass();
                bass++;

                if (bass <= 90) {
                    sendRadioCommand(RadioKey.Command.BASS, RadioKey.Operation.SET, bass);
                }
            }

            @Override
            public void setBassDown() {
                int bass =  mRadioController.getBass();
                bass--;

                if (bass >= 0) {
                    sendRadioCommand(RadioKey.Command.BASS, RadioKey.Operation.SET, bass);
                }
            }

            @Override
            public void setTreble(int treble) {
                sendRadioCommand(RadioKey.Command.TREBLE, RadioKey.Operation.SET, treble);
            }

            @Override
            public void setTrebleUp() {
                int treble = mRadioController.getTreble();
                treble++;

                if (treble <= 90) {
                    sendRadioCommand(RadioKey.Command.TREBLE, RadioKey.Operation.SET, treble);
                }
            }

            @Override
            public void setTrebleDown() {
                int treble = mRadioController.getTreble();
                treble--;

                if (treble >= 0) {
                    sendRadioCommand(RadioKey.Command.TREBLE, RadioKey.Operation.SET, treble);
                }
            }

            @Override
            public void tune(RadioKey.Band band, int frequency, final int subchannel) {
                RadioController.TuneInfo info = new RadioController.TuneInfo();
                info.band = band;
                info.frequency = frequency;

                sendRadioCommand(RadioKey.Command.TUNE, RadioKey.Operation.SET, info);

                // TODO: This functionality should probably move to activity
                if (subchannel > 0 ) {
                    sendRadioCommand(RadioKey.Command.HD_SUBCHANNEL, RadioKey.Operation.SET, subchannel);

                    Thread checkHDStreamLockThread = new Thread(new Runnable() {
                        @Override
                        public void run() {
                            long startTime = SystemClock.elapsedRealtime();

                            // HD Streamlock can take time, retry every 100ms for 10 seconds
                            // to set the subchannel.
                            while (subchannel != mRadioController.getSubchannel()) {

                                if ( SystemClock.elapsedRealtime() > (STREAM_LOCK_TIMEOUT + startTime)) {
                                    DLog.i(TAG, "Unable to Tune to HD Subchannel: " + subchannel);
                                    break;
                                }

                                // Try resetting every 100 ms
                                try {
                                    Thread.sleep(100);
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                                sendRadioCommand(RadioKey.Command.HD_SUBCHANNEL, RadioKey.Operation.SET, subchannel);
                            }
                        }
                    });
                    checkHDStreamLockThread.start();
                }
            }

            @Override
            public void tuneUp() {
                sendRadioCommand(RadioKey.Command.TUNE, RadioKey.Operation.SET, RadioKey.Constant.UP);
            }

            @Override
            public void tuneDown() {
                sendRadioCommand(RadioKey.Command.TUNE, RadioKey.Operation.SET, RadioKey.Constant.DOWN);
            }

            @Override
            public void seekUp() {
                sendRadioCommand(RadioKey.Command.SEEK, RadioKey.Operation.SET, RadioKey.Constant.UP);
            }

            @Override
            public void seekDown() {
                sendRadioCommand(RadioKey.Command.SEEK, RadioKey.Operation.SET, RadioKey.Constant.DOWN);
            }

            @Override
            public void requestUpdate(RadioKey.Command key) {
                sendRadioCommand(key, RadioKey.Operation.GET, null);
            }

        };
    }
}
