package com.arksine.autointegrate;

import java.util.ArrayList;

/**
 * Interface for basic serial device functionality
 */
interface SerialHelper {

    interface DeviceReadyListener{
        void OnDeviceReady(boolean deviceReadyStatus);
    }

    interface DataReceivedListener{
        void OnDataReceived(byte[] data);
    }

    ArrayList<String> enumerateDevices();
    void connectDevice(String id, DeviceReadyListener deviceReadyListener,
                       DataReceivedListener rcdListener);
    void publishConnection(HardwareReceiver.UsbDeviceType type);
    void disconnect();
    String getConnectedId();
    boolean isDeviceConnected();
    boolean writeString(String data);
    boolean writeBytes(byte[] data);
}

