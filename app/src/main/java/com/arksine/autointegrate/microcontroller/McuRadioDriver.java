package com.arksine.autointegrate.microcontroller;

import com.arksine.hdradiolib.drivers.RadioDriver;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by eric on 2/18/17.
 */

public class McuRadioDriver extends RadioDriver {

    // TODO: Implement
    public McuRadioDriver() {}

    @Override
    public <T> ArrayList<T> getDeviceList(Class<T> aClass) {
        return null;
    }

    @Override
    public String getIdentifier() {
        return null;
    }

    @Override
    public boolean isOpen() {
        return false;
    }

    @Override
    public void open() {

    }

    @Override
    public void openById(String s) {

    }

    @Override
    public void close() {

    }

    @Override
    public void raiseRts() {

    }

    @Override
    public void clearRts() {

    }

    @Override
    public void raiseDtr() {

    }

    @Override
    public void clearDtr() {

    }

    @Override
    public void writeData(byte[] bytes) {

    }

    public void readByte(byte b) {
        byte[] bArray = new byte[1];
        bArray[0] = b;
        this.handleIncomingBytes(bArray);
    }

}
