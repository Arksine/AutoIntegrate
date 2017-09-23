package com.arksine.autointegrate.utilities;

import com.arksine.usbserialex.UsbSerialInterface;

/**
 * Settings for Usb Serial Port
 */

public class UsbSerialSettings {
    final int baudRate;
    final int dataBits;
    final int stopBits;
    final int parity;
    final int flowControl;

    public UsbSerialSettings() {
        this.baudRate = 9600;
        this.dataBits = UsbSerialInterface.DATA_BITS_8;
        this.stopBits = UsbSerialInterface.STOP_BITS_1;
        this.parity = UsbSerialInterface.PARITY_NONE;
        this.flowControl = UsbSerialInterface.FLOW_CONTROL_OFF;
    }

    public UsbSerialSettings(int baud) {
        this.baudRate = baud;
        this.dataBits = UsbSerialInterface.DATA_BITS_8;
        this.stopBits = UsbSerialInterface.STOP_BITS_1;
        this.parity = UsbSerialInterface.PARITY_NONE;
        this.flowControl = UsbSerialInterface.FLOW_CONTROL_OFF;
    }

    public UsbSerialSettings(int baud, int flowControl) {
        this.baudRate = baud;
        this.dataBits = UsbSerialInterface.DATA_BITS_8;
        this.stopBits = UsbSerialInterface.STOP_BITS_1;
        this.parity = UsbSerialInterface.PARITY_NONE;
        this.flowControl = flowControl;
    }

    public UsbSerialSettings(int baud, int dataBits, int stopBits, int parity, int flowControl) {
        this.baudRate = baud;
        this.dataBits = dataBits;
        this.stopBits = stopBits;
        this.parity = parity;
        this.flowControl = flowControl;
    }
}