package com.arksine.autointegrate.microcontroller;

import android.support.annotation.NonNull;
import android.util.Log;

import com.arksine.autointegrate.interfaces.MCUControlInterface;
import com.arksine.hdradiolib.drivers.RadioDriver;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 *  Driver to control a Directed HD Radio, connected through the MCU responsible for
 *  all other automotive communication
 */

public class McuRadioDriver extends RadioDriver {

    private MCUControlInterface mControlInterface;
    private boolean mIsOpen;

    public McuRadioDriver(@NonNull MCUControlInterface controlInterface) {
        this.mControlInterface = controlInterface;
        this.mIsOpen = false;
    }

    @Override
    public <T> ArrayList<T> getDeviceList(Class<T> aClass) {
        // TODO: may not implement
        return null;
    }

    @Override
    public String getIdentifier() {
        return mControlInterface.getDeviceId();
    }

    @Override
    public boolean isOpen() {
        return mIsOpen;
    }

    @Override
    public void open() {
        if (!this.mIsOpen) {
            // Request HD Radio Connected status from MCU
            this.mIsOpen = this.mControlInterface.setRadioDriver(this);
        }
        mDriverEvents.onOpened(this.mIsOpen);

    }

    @Override
    public void openById(String s) {
        if (s.equals(getIdentifier())) {
            open();
        } else {
            mDriverEvents.onOpened(false);
        }
    }

    @Override
    public void close() {
        this.mControlInterface.setRadioDriver(null);
        this.mIsOpen = false;
        mDriverEvents.onClosed();
    }

    @Override
    public void raiseRts() {
        mControlInterface.sendMcuCommand(MCUDefs.McuOutputCommand.RADIO_SET_RTS, true);
    }

    @Override
    public void clearRts() {
        mControlInterface.sendMcuCommand(MCUDefs.McuOutputCommand.RADIO_SET_RTS, false);
    }

    @Override
    public void raiseDtr() {
        mControlInterface.sendMcuCommand(MCUDefs.McuOutputCommand.RADIO_SET_DTR, true);
    }

    @Override
    public void clearDtr() {
        mControlInterface.sendMcuCommand(MCUDefs.McuOutputCommand.RADIO_SET_DTR, false);
    }

    @Override
    public void writeData(byte[] bytes) {
        // write bytes to MCU using RADIO_SEND_PACKET command
        mControlInterface.sendMcuCommand(MCUDefs.McuOutputCommand.RADIO_SEND_PACKET, bytes);
    }

    public void readBytes(byte[] bytes) {
        this.handleIncomingBytes(bytes);
    }


    public void readByte(byte b) {
        byte[] bArray = new byte[1];
        bArray[0] = b;
        this.handleIncomingBytes(bArray);
    }

}
