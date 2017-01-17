package com.arksine.autointegrate.interfaces;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.RemoteException;

import com.arksine.hdradiolib.enums.RadioCommand;

/**
 * Callback received from bound activity
*/

public abstract class RadioControlCallback implements IInterface {

    private Binder mBinder = new Binder();

    // Radio Callbacks
    public abstract void OnRadioDataReceived(RadioCommand command, Object value) throws RemoteException;
    public abstract void OnError() throws RemoteException;
    public abstract void OnDisconnect() throws RemoteException;
    public abstract void OnPowerOn () throws RemoteException;
    public abstract void OnPowerOff () throws RemoteException;

    @Override
    public IBinder asBinder() {
        return mBinder;
    }
}
