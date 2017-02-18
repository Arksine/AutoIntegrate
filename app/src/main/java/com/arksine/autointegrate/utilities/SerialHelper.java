package com.arksine.autointegrate.utilities;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;

import java.util.ArrayList;

/**
 * Abstract Class for basic serial device functionality
 */
public abstract class SerialHelper {

    public interface Callbacks {
        void OnDeviceReady(boolean deviceReadyStatus);
        void OnDataReceived(byte[] data);
        void OnDeviceError();
    }

    public abstract ArrayList<String> enumerateSerialDevices();
    public abstract boolean connectDevice(String id, Callbacks cbs);
    public abstract void disconnect();
    public abstract String getConnectedId();
    public abstract boolean isDeviceConnected();
    public abstract boolean writeBytes(final byte[] data);
    public abstract boolean writeString(final String data);

}

