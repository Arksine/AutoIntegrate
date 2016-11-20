package com.arksine.autointegrate.radio;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
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

import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;

/**
 * HD Radio Serial Communications class.
 *
 */

public class RadioCom extends SerialCom {

    private static final String TAG = "RadioCom";
    private static final int STREAM_LOCK_TIMEOUT = 10;   // seconds


    private RadioMessageHandler mInputHandler;
    private RadioController mRadioController;
    private RadioControlInterface mRadioControlInterface;

    private volatile boolean mIsPoweredOn = false;

    // Broadcast reciever to listen for write commands.
    public class RadioCommandReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (mService.getString(R.string.ACTION_SEND_RADIO_COMMAND).equals(action)) {
                // TODO: If I keep this Broadcast Receiver, I need to redo this function, as RadioController.buildPacket
                //       now expects Object data rather than string data.


                /*String command = intent.getStringExtra(mService.getString(R.string.EXTRA_COMMAND));
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
                }*/

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
                Log.w(TAG, "Device Error, disconnecting");
                mDeviceError = true;
                Intent refreshConnection = new Intent(mService
                        .getString(R.string.ACTION_REFRESH_RADIO_CONNECTION));
                LocalBroadcastManager.getInstance(mService).sendBroadcast(refreshConnection);
            }
        };

        mRadioControlInterface = buildRadioInterface();
    }


    @Override
    public boolean connect() {
        // HDRadio Cable is an FTDI device that runs at 115200 Baud with flow control
        UsbSerialSettings settings = new UsbSerialSettings(115200, UsbSerialInterface.FLOW_CONTROL_RTS_CTS);
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
                .getBoolean("radio_pref_key_auto_power", true);
        if (autoPower) {
            return powerOn(mjsCableLocation);
        }

        // Initial setup was successful, although its possible connection wasn't established
        return true;
    }

    @Override
    public void disconnect() {
        if (mConnected) {
            powerOff();
        }

        mSerialHelper = null;

        if (isRadioCommandReceiverRegistered) {
            mService.unregisterReceiver(radioCommandReceiver);
            isRadioCommandReceiverRegistered = false;
        }
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

    private boolean powerOn(String mjsCableLocation) {

        if (mConnected) {
            // already powered on
            return true;
        }

        // if we are passed a null string look for the device
        if (mjsCableLocation == null)
            mjsCableLocation = findMJSDevice();

        // if still null, cable is not found, exit
        if (mjsCableLocation == null) {
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
            mConnected = false;
        }

        if (mConnected) {

            // Sleep for .5 seconds to give the radio a chance to respond if it is powered up
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            initRadioVars();
        } else {
            Log.i(TAG, "Unable to connect to HD Radio");
        }

        return mConnected;
    }

    private void powerOff() {
        if (!mConnected) {
            // already powered off
            return;
        }
        mConnected = false;
        if (mSerialHelper!= null) {

            mRadioController.close(mService);

            // If there was a device error then we cannot write to it
            if (!mDeviceError) {
                // send shutdown command to radio if its on
                if ((boolean)mRadioController.getHdValue(RadioKey.Command.POWER)) {
                    mSerialHelper.writeBytes(mRadioController
                            .buildRadioPacket(RadioKey.Command.POWER, RadioKey.Operation.SET, false));
                }

                // If we disconnect too soon after writing an exception will be thrown
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Log.w(TAG, e.getMessage());
                }

                mSerialHelper.disconnect();
            }
        }
    }

    private boolean isPoweredOn() {
        return mConnected;
    }


    private void initRadioVars() {

        if (DLog.DEBUG) {
            sendRadioCommand(RadioKey.Command.VOLUME, RadioKey.Operation.GET, null);
            sendRadioCommand(RadioKey.Command.MUTE, RadioKey.Operation.GET, null);
            sendRadioCommand(RadioKey.Command.BASS, RadioKey.Operation.GET, null);
            sendRadioCommand(RadioKey.Command.TREBLE, RadioKey.Operation.GET, null);
            sendRadioCommand(RadioKey.Command.COMPRESSION, RadioKey.Operation.GET, null);
        }

        // Restore persisted values
        SharedPreferences globalPrefs = PreferenceManager.getDefaultSharedPreferences(mService);
        int frequency = globalPrefs.getInt("radio_pref_key_frequency", 879);
        RadioKey.Band band = (globalPrefs.getString("radio_pref_key_band", "FM").equals("FM")) ?
                RadioKey.Band.FM : RadioKey.Band.AM;
        int subchannel = globalPrefs.getInt("radio_pref_key_subchannel", 0);
        int volume = globalPrefs.getInt("radio_pref_key_volume", 75);
        int bass = globalPrefs.getInt("radio_pref_key_bass", 15);
        int treble = globalPrefs.getInt("radio_pref_key_treble", 15);

        mRadioControlInterface.tune(band, frequency, subchannel);
        mRadioControlInterface.setVolume(volume);
        mRadioControlInterface.setBass(bass);
        mRadioControlInterface.setTreble(treble);

    }

    private void sendRadioCommand(RadioKey.Command command, RadioKey.Operation operation, Object data) {
        byte[] radioPacket = mRadioController.buildRadioPacket(command, operation, data);
        if (radioPacket != null) {
            mSerialHelper.writeBytes(radioPacket);
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        } else {
            Log.i(TAG, "Invalid Radio Packet");
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
                    powerOn(null);
                } else {
                    powerOff();
                }
            }

            @Override
            public boolean getPowerStatus() {
                return mConnected;
            }

            @Override
            public void toggleMute(boolean status) {
                sendRadioCommand(RadioKey.Command.MUTE, RadioKey.Operation.SET, status);
            }

            @Override
            public void setVolume(int volume) {
                sendRadioCommand(RadioKey.Command.VOLUME, RadioKey.Operation.SET, volume);
            }

            @Override
            public void setVolumeUp() {
                int volume = (int)mRadioController.getHdValue(RadioKey.Command.VOLUME);
                volume++;

                if (volume <= 90) {
                    sendRadioCommand(RadioKey.Command.VOLUME, RadioKey.Operation.SET, volume);
                }
            }

            @Override
            public void setVolumeDown() {
                int volume = (int)mRadioController.getHdValue(RadioKey.Command.VOLUME);
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
                int bass = (int)mRadioController.getHdValue(RadioKey.Command.BASS);
                bass++;

                if (bass <= 90) {
                    sendRadioCommand(RadioKey.Command.BASS, RadioKey.Operation.SET, bass);
                }
            }

            @Override
            public void setBassDown() {
                int bass = (int)mRadioController.getHdValue(RadioKey.Command.BASS);
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
                int treble = (int)mRadioController.getHdValue(RadioKey.Command.TREBLE);
                treble++;

                if (treble <= 90) {
                    sendRadioCommand(RadioKey.Command.TREBLE, RadioKey.Operation.SET, treble);
                }
            }

            @Override
            public void setTrebleDown() {
                int treble = (int)mRadioController.getHdValue(RadioKey.Command.TREBLE);
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

                if (subchannel > 0 ) {
                    final byte[] subChPacket = mRadioController.buildRadioPacket(RadioKey.Command.HD_SUBCHANNEL,
                            RadioKey.Operation.SET, subchannel);
                    mSerialHelper.writeBytes(subChPacket);

                    Thread checkHDStreamLockThread = new Thread(new Runnable() {
                        @Override
                        public void run() {
                            Calendar calendar = Calendar.getInstance();
                            int setTime = calendar.get(Calendar.SECOND);

                            // HD Streamlock can take time, retry every 100ms for 10 seconds
                            // to set the subchannel.
                            while ((subchannel != (int) mRadioController.getHdValue(RadioKey.Command.HD_SUBCHANNEL)) &&
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

            @Override
            public Object getHdValue(RadioKey.Command key) {
                return mRadioController.getHdValue(key);
            }
        };
    }
}
