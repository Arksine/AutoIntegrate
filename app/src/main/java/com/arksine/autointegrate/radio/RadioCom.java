package com.arksine.autointegrate.radio;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.util.Log;

import com.arksine.autointegrate.AutoIntegrate;
import com.arksine.autointegrate.MainService;
import com.arksine.autointegrate.R;
import com.arksine.autointegrate.interfaces.MCUControlInterface;
import com.arksine.autointegrate.interfaces.ServiceControlInterface;
import com.arksine.autointegrate.microcontroller.McuRadioDriver;
import com.arksine.autointegrate.utilities.DLog;
import com.arksine.autointegrate.utilities.HardwareReceiver;
import com.arksine.autointegrate.utilities.UtilityFunctions;
import com.arksine.hdradiolib.HDRadio;
import com.arksine.hdradiolib.HDRadioEvents;
import com.arksine.hdradiolib.HDSongInfo;
import com.arksine.hdradiolib.RadioController;
import com.arksine.hdradiolib.TuneInfo;
import com.arksine.hdradiolib.enums.RadioError;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;


/**
 * HD Radio Serial Communications class.
 *
 */
public class RadioCom {

    private static final String TAG = RadioCom.class.getSimpleName();

    private MainService mService;
    private AtomicBoolean mConnected = new AtomicBoolean(false);
    private AtomicBoolean mIsWaiting = new AtomicBoolean(false);

    private HDRadio mHdRadio;
    private HDRadioEvents mRadioEvents;
    private McuRadioDriver mMcuRadioDriver = null;
    private RadioController mRadioController;

    // Broadcast reciever to listen for write commands.
    public class RadioCommandReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (mService.getString(R.string.ACTION_SEND_RADIO_COMMAND).equals(action)) {
                // Accept a limited numbers of radio commands via broadcast

                if (mRadioController != null) {
                    String radioCmd = intent.getStringExtra(mService.getString(R.string.EXTRA_COMMAND));
                    radioCmd = radioCmd.toUpperCase();
                    switch (radioCmd) {
                        case "TUNE UP":
                            mRadioController.tuneUp();
                            break;
                        case "TUNE DOWN":
                            mRadioController.tuneDown();
                            break;
                        case "SEEK UP":
                            mRadioController.seekUp();
                            break;
                        case "SEEK DOWN":
                            mRadioController.seekDown();
                            break;
                        case "VOLUME UP":
                            mRadioController.setVolumeUp();
                            break;
                        case "VOLUME DOWN":
                            mRadioController.setVolumeDown();
                            break;
                        case "MUTE":
                            boolean mute = mRadioController.getMute();
                            if (mute) {
                                mRadioController.muteOff();
                            } else {
                                mRadioController.muteOn();
                            }
                            break;
                        default:
                            Log.i(TAG, "Unknown radio command: " + radioCmd);
                    }
                }
            }
        }
    }
    private final RadioCommandReceiver radioCommandReceiver = new RadioCommandReceiver();
    private volatile boolean isRadioCommandReceiverRegistered = false;

    public RadioCom(MainService svc) {
        this.mService = svc;

        mRadioEvents = new HDRadioEvents() {
            @Override
            public void onOpened(boolean b, RadioController radioController) {
                DLog.v(TAG, "onOpened Callback triggered");
                RadioCom.this.mConnected.set(b);

                // Radio successfully connected
                if (RadioCom.this.mConnected.get()) {
                    mRadioController = radioController;

                    // Register Receiver if not already registered
                    if (!isRadioCommandReceiverRegistered) {
                        IntentFilter sendDataFilter = new IntentFilter(mService
                                .getString(R.string.ACTION_SEND_RADIO_COMMAND));
                        mService.registerReceiver(radioCommandReceiver, sendDataFilter);
                        isRadioCommandReceiverRegistered = true;
                    }

                } else {
                    Log.e(TAG, "Error connecting to device");
                }



                RadioCom.this.resumeThread();
            }

            @Override
            public void onClosed() {
                DLog.v(TAG, "onClosed Callback triggered");
                RadioCom.this.mConnected.set(false);

                int cbCount = mService.mRadioCallbacks.beginBroadcast();
                for (int i = 0; i < cbCount; i++) {
                    try {
                        mService.mRadioCallbacks.getBroadcastItem(i).onClosed();
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }
                mService.mRadioCallbacks.finishBroadcast();

                // Calling thread is waiting, resume
                RadioCom.this.resumeThread();
            }

            @Override
            public void onDeviceError(RadioError radioError) {
                Log.e(TAG, "Device Error: " + radioError.toString());

                int cbCount = mService.mRadioCallbacks.beginBroadcast();
                for (int i = 0; i < cbCount; i++) {
                    try {
                        mService.mRadioCallbacks.getBroadcastItem(i).onError();
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }
                mService.mRadioCallbacks.finishBroadcast();

                // Close and attempt to reopen connection
                ServiceControlInterface serviceControl = AutoIntegrate.getServiceControlInterface();
                if (serviceControl != null) {
                    serviceControl.refreshRadioConnection();
                }
            }

            @Override
            public void onRadioPowerOn() {
                DLog.v(TAG, "onRadioPowerOn Callback triggered");
                int cbCount = mService.mRadioCallbacks.beginBroadcast();
                for (int i = 0; i < cbCount; i++) {
                    try {
                        mService.mRadioCallbacks.getBroadcastItem(i).onPowerOn();
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }
                mService.mRadioCallbacks.finishBroadcast();
            }

            @Override
            public void onRadioPowerOff() {
                DLog.v(TAG, "onRadioPowerOff Callback triggered");
                int cbCount = mService.mRadioCallbacks.beginBroadcast();
                for (int i = 0; i < cbCount; i++) {
                    try {
                        mService.mRadioCallbacks.getBroadcastItem(i).onPowerOff();
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }
                mService.mRadioCallbacks.finishBroadcast();
            }

            @Override
            public void onRadioMute(boolean b) {
                int cbCount = mService.mRadioCallbacks.beginBroadcast();
                for (int i = 0; i < cbCount; i++) {
                    try {
                        mService.mRadioCallbacks.getBroadcastItem(i).onRadioMute(b);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }
                mService.mRadioCallbacks.finishBroadcast();
            }

            @Override
            public void onRadioSignalStrength(int signalStrength) {
                int cbCount = mService.mRadioCallbacks.beginBroadcast();
                for (int i = 0; i < cbCount; i++) {
                    try {
                        mService.mRadioCallbacks.getBroadcastItem(i).onRadioSignalStrength(signalStrength);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }
                mService.mRadioCallbacks.finishBroadcast();
            }

            @Override
            public void onRadioTune(TuneInfo tuneInfo) {
                int cbCount = mService.mRadioCallbacks.beginBroadcast();
                for (int i = 0; i < cbCount; i++) {
                    try {
                        mService.mRadioCallbacks.getBroadcastItem(i).onRadioTune(tuneInfo);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }
                mService.mRadioCallbacks.finishBroadcast();
            }

            @Override
            public void onRadioSeek(TuneInfo tuneInfo) {
                int cbCount = mService.mRadioCallbacks.beginBroadcast();
                for (int i = 0; i < cbCount; i++) {
                    try {
                        mService.mRadioCallbacks.getBroadcastItem(i).onRadioSeek(tuneInfo);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }
                mService.mRadioCallbacks.finishBroadcast();
            }

            @Override
            public void onRadioHdActive(boolean b) {
                int cbCount = mService.mRadioCallbacks.beginBroadcast();
                for (int i = 0; i < cbCount; i++) {
                    try {
                        mService.mRadioCallbacks.getBroadcastItem(i).onRadioHdActive(b);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }
                mService.mRadioCallbacks.finishBroadcast();
            }

            @Override
            public void onRadioHdStreamLock(boolean b) {
                int cbCount = mService.mRadioCallbacks.beginBroadcast();
                for (int i = 0; i < cbCount; i++) {
                    try {
                        mService.mRadioCallbacks.getBroadcastItem(i).onRadioHdStreamLock(b);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }
                mService.mRadioCallbacks.finishBroadcast();
            }

            @Override
            public void onRadioHdSignalStrength(int hdSignal) {
                int cbCount = mService.mRadioCallbacks.beginBroadcast();
                for (int i = 0; i < cbCount; i++) {
                    try {
                        mService.mRadioCallbacks.getBroadcastItem(i).onRadioHdSignalStrength(hdSignal);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }
                mService.mRadioCallbacks.finishBroadcast();
            }

            @Override
            public void onRadioHdSubchannel(int subchannel) {
                int cbCount = mService.mRadioCallbacks.beginBroadcast();
                for (int i = 0; i < cbCount; i++) {
                    try {
                        mService.mRadioCallbacks.getBroadcastItem(i).onRadioHdSubchannel(subchannel);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }
                mService.mRadioCallbacks.finishBroadcast();
            }

            @Override
            public void onRadioHdSubchannelCount(int count) {
                int cbCount = mService.mRadioCallbacks.beginBroadcast();
                for (int i = 0; i < cbCount; i++) {
                    try {
                        mService.mRadioCallbacks.getBroadcastItem(i).onRadioHdSubchannelCount(count);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }
                mService.mRadioCallbacks.finishBroadcast();
            }

            @Override
            public void onRadioHdTitle(HDSongInfo hdSongInfo) {
                int cbCount = mService.mRadioCallbacks.beginBroadcast();
                for (int i = 0; i < cbCount; i++) {
                    try {
                        mService.mRadioCallbacks.getBroadcastItem(i).onRadioHdTitle(hdSongInfo);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }
                mService.mRadioCallbacks.finishBroadcast();
            }

            @Override
            public void onRadioHdArtist(HDSongInfo hdSongInfo) {
                int cbCount = mService.mRadioCallbacks.beginBroadcast();
                for (int i = 0; i < cbCount; i++) {
                    try {
                        mService.mRadioCallbacks.getBroadcastItem(i).onRadioHdArtist(hdSongInfo);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }
                mService.mRadioCallbacks.finishBroadcast();
            }

            @Override
            public void onRadioHdCallsign(String s) {
                int cbCount = mService.mRadioCallbacks.beginBroadcast();
                for (int i = 0; i < cbCount; i++) {
                    try {
                        mService.mRadioCallbacks.getBroadcastItem(i).onRadioHdCallsign(s);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }
                mService.mRadioCallbacks.finishBroadcast();
            }

            @Override
            public void onRadioHdStationName(String s) {
                int cbCount = mService.mRadioCallbacks.beginBroadcast();
                for (int i = 0; i < cbCount; i++) {
                    try {
                        mService.mRadioCallbacks.getBroadcastItem(i).onRadioHdStationName(s);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }
                mService.mRadioCallbacks.finishBroadcast();
            }

            @Override
            public void onRadioRdsEnabled(boolean b) {
                int cbCount = mService.mRadioCallbacks.beginBroadcast();
                for (int i = 0; i < cbCount; i++) {
                    try {
                        mService.mRadioCallbacks.getBroadcastItem(i).onRadioRdsEnabled(b);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }
                mService.mRadioCallbacks.finishBroadcast();
            }

            @Override
            public void onRadioRdsGenre(String s) {
                int cbCount = mService.mRadioCallbacks.beginBroadcast();
                for (int i = 0; i < cbCount; i++) {
                    try {
                        mService.mRadioCallbacks.getBroadcastItem(i).onRadioRdsGenre(s);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }
                mService.mRadioCallbacks.finishBroadcast();
            }

            @Override
            public void onRadioRdsProgramService(String s) {
                int cbCount = mService.mRadioCallbacks.beginBroadcast();
                for (int i = 0; i < cbCount; i++) {
                    try {
                        mService.mRadioCallbacks.getBroadcastItem(i).onRadioRdsProgramService(s);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }
                mService.mRadioCallbacks.finishBroadcast();
            }

            @Override
            public void onRadioRdsRadioText(String s) {
                int cbCount = mService.mRadioCallbacks.beginBroadcast();
                for (int i = 0; i < cbCount; i++) {
                    try {
                        mService.mRadioCallbacks.getBroadcastItem(i).onRadioRdsRadioText(s);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }
                mService.mRadioCallbacks.finishBroadcast();
            }

            @Override
            public void onRadioVolume(int volume) {
                int cbCount = mService.mRadioCallbacks.beginBroadcast();
                for (int i = 0; i < cbCount; i++) {
                    try {
                        mService.mRadioCallbacks.getBroadcastItem(i).onRadioVolume(volume);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }
                mService.mRadioCallbacks.finishBroadcast();
            }

            @Override
            public void onRadioBass(int bass) {
                int cbCount = mService.mRadioCallbacks.beginBroadcast();
                for (int i = 0; i < cbCount; i++) {
                    try {
                        mService.mRadioCallbacks.getBroadcastItem(i).onRadioBass(bass);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }
                mService.mRadioCallbacks.finishBroadcast();
            }

            @Override
            public void onRadioTreble(int treble) {
                int cbCount = mService.mRadioCallbacks.beginBroadcast();
                for (int i = 0; i < cbCount; i++) {
                    try {
                        mService.mRadioCallbacks.getBroadcastItem(i).onRadioTreble(treble);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }
                mService.mRadioCallbacks.finishBroadcast();
            }

            @Override
            public void onRadioCompression(int compression) {
                int cbCount = mService.mRadioCallbacks.beginBroadcast();
                for (int i = 0; i < cbCount; i++) {
                    try {
                        mService.mRadioCallbacks.getBroadcastItem(i).onRadioCompression(compression);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }
                mService.mRadioCallbacks.finishBroadcast();
            }
        };

    }

    private boolean initRadioInstance() {
        DLog.i(TAG, "Creating HD Radio instance");

        // Find the correct driver
        int driverVal = Integer.parseInt(PreferenceManager
                .getDefaultSharedPreferences(mService).getString("radio_pref_key_select_driver", "0"));


        switch (driverVal) {
            case 0:     // MJS Driver Selected
                mHdRadio = new HDRadio(mService, mRadioEvents, HDRadio.DriverType.MJS_DRIVER);
                break;
            case 1:     // Stand alone Arduino Driver Selected
                mHdRadio = new HDRadio(mService, mRadioEvents, HDRadio.DriverType.ARDUINO_DRIVER);
                break;
            case 2:     // Integrated MCU Driver Selected
                MCUControlInterface controlInterface =  AutoIntegrate.getmMcuControlInterface();
                if (controlInterface != null) {
                    mMcuRadioDriver = new McuRadioDriver(controlInterface);
                    mHdRadio = new HDRadio(mService, mRadioEvents, mMcuRadioDriver);
                    return true;
                } else {
                    Log.e(TAG, "Cannot use Integrated MCU Driver, MCU not connected");
                    return false;
                }
            default:
                Log.e(TAG, "Unknown Radio Driver Selection");
                return false;
        }

        // Attempt to use signature permissions to grant automatic permission for connected
        // Radio devices
        if (UtilityFunctions.hasSignaturePermission(mService)) {

            ArrayList<UsbDevice> hdCableArray = mHdRadio.getDeviceList(UsbDevice.class);
            if (hdCableArray != null && !hdCableArray.isEmpty()) {
                for (UsbDevice uDev : hdCableArray) {
                    HardwareReceiver.grantAutomaticUsbPermission(uDev, mService);
                }
            }
        }
        return true;
    }


    public boolean connect() {
        // If already connected, attempt to disconnect
        if (mConnected.get()) {
            disconnect();
        }

        if (!initRadioInstance())
        {
            return false;
        }

        DLog.i(TAG, "Attempting to open connection to Directed HD Radio");
        mHdRadio.open();

        // Wait with a 10 second timeout for the onConnected Callback
        synchronized (this) {
            try {
                mIsWaiting.set(true);
                wait(10000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                if (mIsWaiting.compareAndSet(true, false)) {
                    Log.i(TAG, "Connection attempt timed out");
                }
            }
        }

        return mConnected.get();
    }


    public void disconnect() {
        if (mConnected.compareAndSet(true, false)) {

            if (isRadioCommandReceiverRegistered) {
                mService.unregisterReceiver(radioCommandReceiver);
                isRadioCommandReceiverRegistered = false;
            }

            if (mHdRadio != null) {
                mHdRadio.close();

                // Wait for onClose callback with a timeout of 10 seconds
                synchronized (this) {
                    try {
                        mIsWaiting.set(true);
                        wait(10000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    } finally {
                        if (mIsWaiting.compareAndSet(true, false)) {
                            Log.i(TAG, "Connection attempt timed out");
                        }
                    }
                }
                mHdRadio = null;
                mMcuRadioDriver = null;
            }
        }
    }

    public void updateDriver() {
        // Called when the MCU connection has been refreshed
        if (mMcuRadioDriver != null) {
            mMcuRadioDriver.updateMcuInterface();
            mMcuRadioDriver.open();
        }
    }


    public RadioController getRadioInterface() {
        return mRadioController;
    }

    public boolean isConnected() {
        return mConnected.get();
    }

    private synchronized void resumeThread() {
        if (mIsWaiting.compareAndSet(true, false)) {
            notify();
        }
    }

}
