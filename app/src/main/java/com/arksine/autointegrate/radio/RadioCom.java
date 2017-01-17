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
import com.arksine.hdradiolib.HDRadioCallbacks;
import com.arksine.hdradiolib.RadioController;
import com.arksine.hdradiolib.enums.RadioCommand;
import com.arksine.hdradiolib.enums.RadioError;

import java.util.ArrayList;


/**
 * HD Radio Serial Communications class.
 *
 */
public class RadioCom {

    private static final String TAG = RadioCom.class.getSimpleName();

    private MainService mService;
    private volatile boolean mConnected = false;
    private volatile boolean mIsWaiting = false;

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

        HDRadioCallbacks callbacks = new HDRadioCallbacks() {
            @Override
            public void onOpened(boolean b, RadioController radioController) {
                DLog.v(TAG, "onOpened Callback triggered");
                RadioCom.this.mConnected = b;

                if (RadioCom.this.mConnected) {
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
                RadioCom.this.mConnected = false;

                int cbCount = mService.mRadioCallbacks.beginBroadcast();
                for (int i = 0; i < cbCount; i++) {
                    try {
                        mService.mRadioCallbacks.getBroadcastItem(i).OnDisconnect();
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
                        mService.mRadioCallbacks.getBroadcastItem(i).OnError();
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }
                mService.mRadioCallbacks.finishBroadcast();

                Intent refreshConnection = new Intent(mService
                        .getString(R.string.ACTION_REFRESH_RADIO_CONNECTION));
                LocalBroadcastManager.getInstance(mService).sendBroadcast(refreshConnection);
            }

            @Override
            public void onRadioPowerOn() {
                // TODO:  need to synchronize broadcasts, as they can be called from different threads
                DLog.v(TAG, "onRadioPowerOn Callback triggered");
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

            @Override
            public void onRadioPowerOff() {
                DLog.v(TAG, "onRadioPowerOff Callback triggered");
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

            @Override
            public void onRadioDataReceived(RadioCommand radioCommand, Object o) {
                int cbCount = mService.mRadioCallbacks.beginBroadcast();
                for (int i = 0; i < cbCount; i++) {
                    try {
                        mService.mRadioCallbacks.getBroadcastItem(i)
                                .OnRadioDataReceived(radioCommand, o);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }
                mService.mRadioCallbacks.finishBroadcast();
            }
        };

        DLog.i(TAG, "Creating HD Radio instance");
        mHdRadio = new HDRadio(mService, callbacks);

        ArrayList<UsbDevice> hdCableArray = mHdRadio.getUsbRadioDevices();

        // If the array isn't empty and the Application has signature level permissions,
        // grant them to every HD Radio device in the array
        if (!hdCableArray.isEmpty() && UtilityFunctions.hasSignaturePermission(mService)) {
            for (UsbDevice uDev : hdCableArray) {
                HardwareReceiver.grantAutomaticUsbPermission(uDev, mService);
            }
        }

    }

    public boolean connect() {
        DLog.i(TAG, "Attempting to open connection to MJS Gadgets Cable");
        mHdRadio.open();

        // Wait with a 10 second timeout for the onConnected Callback
        synchronized (this) {
            try {
                mIsWaiting = true;
                wait(10000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        return mConnected;
    }


    public void disconnect() {
        mConnected = false;

        if (isRadioCommandReceiverRegistered) {
            mService.unregisterReceiver(radioCommandReceiver);
            isRadioCommandReceiverRegistered = false;
        }

        mHdRadio.close();

        // Wait for onClose callback with a timeout of 10 seconds
        synchronized (this) {
            try {
                mIsWaiting = true;
                wait(10000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }


    public RadioController getRadioInterface() {
        return mRadioController;
    }

    public boolean isConnected() {
        return mConnected;
    }

    private synchronized void resumeThread() {
        if (mIsWaiting) {
            mIsWaiting = false;
            notify();
        }
    }

}
