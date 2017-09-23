package com.arksine.autointegrate.radio;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.RemoteException;

import com.arksine.hdradiolib.HDSongInfo;
import com.arksine.hdradiolib.TuneInfo;
import com.arksine.hdradiolib.enums.RadioCommand;

/**
 * Callback received from bound activity
*/

public abstract class RemoteRadioEvents implements IInterface {

    private Binder mBinder = new Binder();

    // Radio Callbacks
    public abstract void onError() throws RemoteException;
    public abstract void onClosed() throws RemoteException;
    public abstract void onPowerOn () throws RemoteException;
    public abstract void onPowerOff () throws RemoteException;
    public abstract void onRadioMute(final boolean muteStatus) throws RemoteException;
    public abstract void onRadioSignalStrength(final int signalStrength) throws RemoteException;
    public abstract void onRadioTune(final TuneInfo tuneInfo) throws RemoteException;
    public abstract void onRadioSeek(final TuneInfo seekInfo) throws RemoteException;
    public abstract void onRadioHdActive(final boolean hdActive) throws RemoteException;
    public abstract void onRadioHdStreamLock(final boolean hdStreamLock) throws RemoteException;
    public abstract void onRadioHdSignalStrength(final int hdSignalStrength) throws RemoteException;
    public abstract void onRadioHdSubchannel(final int subchannel) throws RemoteException;
    public abstract void onRadioHdSubchannelCount(final int subchannelCount) throws RemoteException;
    public abstract void onRadioHdTitle(final HDSongInfo hdTitle) throws RemoteException;
    public abstract void onRadioHdArtist(final HDSongInfo hdArtist) throws RemoteException;
    public abstract void onRadioHdCallsign(final String hdCallsign) throws RemoteException;
    public abstract void onRadioHdStationName(final String hdStationName) throws RemoteException;
    public abstract void onRadioRdsEnabled(final boolean rdsEnabled) throws RemoteException;
    public abstract void onRadioRdsGenre(final String rdsGenre) throws RemoteException;
    public abstract void onRadioRdsProgramService(final String rdsProgramService) throws RemoteException;
    public abstract void onRadioRdsRadioText(final String rdsRadioText) throws RemoteException;
    public abstract void onRadioVolume(final int volume) throws RemoteException;
    public abstract void onRadioBass(final int bass) throws RemoteException;
    public abstract void onRadioTreble(final int treble) throws RemoteException;
    public abstract void onRadioCompression(final int compression) throws RemoteException;

    @Override
    public IBinder asBinder() {
        return mBinder;
    }
}
