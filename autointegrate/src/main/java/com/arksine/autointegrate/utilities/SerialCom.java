package com.arksine.autointegrate.utilities;

import com.arksine.autointegrate.MainService;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Abstract class for Serial Communcations handling.  Children must override connect and
 * disconnect functions.
 */

public abstract class SerialCom {

    protected AtomicBoolean mConnected = new AtomicBoolean(false);
    protected MainService mService;

    protected AtomicBoolean mIsWaiting = new AtomicBoolean(false);
    protected AtomicBoolean mDeviceError = new AtomicBoolean(false);

    protected SerialHelper mSerialHelper;
    protected SerialHelper.Callbacks mCallbacks;

    public SerialCom(MainService svc) {
        mService = svc;
    }

    protected synchronized void resumeThread() {
        if (mIsWaiting.compareAndSet(true, false)) {
            notify();
        }
    }

    abstract public boolean connect();
    abstract public void disconnect();

    public boolean isConnected() {
        return mConnected.get();
    }
}
