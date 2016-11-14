package com.arksine.autointegrate.utilities;

import android.content.Context;

import com.arksine.autointegrate.interfaces.SerialHelper;

/**
 * Abstract class for Serial Communcations handling.  Children must override connect and
 * disconnect functions.
 */

public abstract class SerialCom {

    protected volatile boolean mConnected = false;
    protected Context mContext;

    protected volatile boolean mIsWaiting = false;
    protected volatile boolean mDeviceError = false;

    protected SerialHelper mSerialHelper;
    protected SerialHelper.Callbacks mCallbacks;

    public SerialCom(Context context) {
        mContext = context;
    }

    protected synchronized void resumeThread() {
        if (mIsWaiting) {
            mIsWaiting = false;
            notify();
        }
    }

    abstract public boolean connect();
    abstract public void disconnect();

    public boolean isConnected() {
        return mConnected;
    }
}
