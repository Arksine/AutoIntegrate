package com.arksine.autointegrate.interfaces;

import java.util.ArrayList;

/**
 * Interface for basic serial device functionality
 */
public interface SerialHelper {

    interface Callbacks {
        void OnDeviceReady(boolean deviceReadyStatus);
        void OnDataReceived(byte[] data);
        void OnDeviceError();
    }


    ArrayList<String> enumerateSerialDevices();
    boolean connectDevice(String id, Callbacks cbs);
    void disconnect();
    String getConnectedId();
    boolean isDeviceConnected();
    boolean writeString(final String data);
    boolean writeBytes(final byte[] data);
}

