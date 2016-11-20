package com.arksine.autointegrate.interfaces;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.RemoteException;

import com.arksine.autointegrate.radio.RadioKey;

/**
 * Callback received from bound activity (Perhaps I should use RemoteCallbackList)
*/

public abstract class RadioControlCallback implements IInterface {

    private Binder mBinder = new Binder();

    // WARNING: This function is called in a synchronized block.  Avoid synchronizing methods
    //          inside the block, as nested synchronization can cause a deadlock.  Best way
    //          is to use a handler to process each call as they are coming in, then synchronize
    //          inside the handleMessage function if necessary
    public abstract void OnRadioDataReceived(RadioKey.Command key, Object value) throws RemoteException;
    public abstract void OnClose() throws RemoteException;

    @Override
    public IBinder asBinder() {
        return mBinder;
    }
}
