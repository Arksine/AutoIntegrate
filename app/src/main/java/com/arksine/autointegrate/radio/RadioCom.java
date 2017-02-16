package com.arksine.autointegrate.radio;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.os.RemoteException;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.arksine.autointegrate.MainService;
import com.arksine.autointegrate.R;
import com.arksine.autointegrate.utilities.DLog;
import com.arksine.autointegrate.utilities.HardwareReceiver;
import com.arksine.autointegrate.utilities.UtilityFunctions;
import com.arksine.hdradiolib.HDRadio;
import com.arksine.hdradiolib.HDRadioEvents;
import com.arksine.hdradiolib.HDSongInfo;
import com.arksine.hdradiolib.RadioController;
import com.arksine.hdradiolib.TuneInfo;
import com.arksine.hdradiolib.enums.RadioCommand;
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
    private RadioController mRadioController;

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
    private volatile boolean isRadioCommandReceiverRegistered = false;

    public RadioCom(MainService svc) {
        this.mService = svc;

        HDRadioEvents events = new HDRadioEvents() {
            @Override
            public void onOpened(boolean b, RadioController radioController) {
                DLog.v(TAG, "onOpened Callback triggered");
                RadioCom.this.mConnected.set(b);

                if (RadioCom.this.mConnected.get()) {
                    mRadioController = radioController;

                } else {
                    Log.e(TAG, "Error connecting to device");
                }

                // Register Receiver if not already registered
                if (!isRadioCommandReceiverRegistered) {
                    IntentFilter sendDataFilter = new IntentFilter(mService
                            .getString(R.string.ACTION_SEND_RADIO_COMMAND));
                    mService.registerReceiver(radioCommandReceiver, sendDataFilter);
                    isRadioCommandReceiverRegistered = true;
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
                Intent refreshConnection = new Intent(mService
                        .getString(R.string.ACTION_REFRESH_RADIO_CONNECTION));
                LocalBroadcastManager.getInstance(mService).sendBroadcast(refreshConnection);
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



        DLog.i(TAG, "Creating HD Radio instance");
        // TODO: defaults to mjs driver, get device type from settings and set it here
        mHdRadio = new HDRadio(mService, events);



        // TODO: this depends on the type of device.  If we arent dealing with usb devices we dont need to do this
        // If the array isn't empty and the Application has signature level permissions,
        // grant them to every HD Radio device in the array
        if (UtilityFunctions.hasSignaturePermission(mService)) {

            ArrayList<UsbDevice> hdCableArray = mHdRadio.getDeviceList(UsbDevice.class);
            if (!hdCableArray.isEmpty()) {
                for (UsbDevice uDev : hdCableArray) {
                    HardwareReceiver.grantAutomaticUsbPermission(uDev, mService);
                }
            }
        }

    }

    public boolean connect() {
        DLog.i(TAG, "Attempting to open connection to MJS Gadgets Cable");
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
        mConnected.set(false);

        if (isRadioCommandReceiverRegistered) {
            mService.unregisterReceiver(radioCommandReceiver);
            isRadioCommandReceiverRegistered = false;
        }

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
