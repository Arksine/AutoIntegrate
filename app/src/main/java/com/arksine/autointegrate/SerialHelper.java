package com.arksine.autointegrate;

import java.util.ArrayList;

/**
 * Interface for basic serial device functionality
 */
interface SerialHelper {
    //TODO: put all the interfaces below into one callback

    interface Callbacks {
        void OnDeviceReady(boolean deviceReadyStatus);
        void OnDataReceived(byte[] data);
        void OnDeviceError();
    }


    ArrayList<String> enumerateDevices();
    void connectDevice(String id, Callbacks cbs);
    void disconnect();
    String getConnectedId();
    boolean isDeviceConnected();
    boolean writeString(final String data);
    boolean writeBytes(final byte[] data);
}

