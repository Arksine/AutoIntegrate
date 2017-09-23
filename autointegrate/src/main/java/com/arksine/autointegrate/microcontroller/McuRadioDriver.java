package com.arksine.autointegrate.microcontroller;

import com.arksine.autointegrate.AutoIntegrate;
import com.arksine.autointegrate.interfaces.MCUControlInterface;
import com.arksine.hdradiolib.drivers.RadioDriver;
import com.arksine.hdradiolib.enums.RadioError;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;

import timber.log.Timber;

/**
 *  Driver to control a Directed HD Radio, connected through the MCU responsible for
 *  all other automotive communication
 */

public class McuRadioDriver extends RadioDriver {

    private MCUControlInterface mMcuControlInterface;
    private boolean mIsOpen;

    public McuRadioDriver() {
        this.mMcuControlInterface = AutoIntegrate.getMcuInterfaceRef().get();
        this.mIsOpen = false;
    }

    public void updateMcuInterface() {
        this.mMcuControlInterface = AutoIntegrate.getMcuInterfaceRef().get();
    }

    public void flagConnectionError() {
        this.mIsOpen = false;
        this.mDriverEvents.onError(RadioError.CONNECTION_ERROR);
    }

    @Override
    public <T> ArrayList<T> getDeviceList(Class<T> aClass) {
        // TODO: may not implement
        return null;
    }

    @Override
    public String getIdentifier() {
        if (mMcuControlInterface != null) {
            return mMcuControlInterface.getDeviceId();
        } else {
            return "No Control Interface";
        }
    }

    @Override
    public boolean isOpen() {
        return mIsOpen;
    }

    @Override
    public void open() {
        if (!this.mIsOpen && mMcuControlInterface != null) {
            // Request HD Radio Connected status from MCU
            this.mIsOpen = mMcuControlInterface.setRadioDriver(this);
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
        if (mMcuControlInterface != null) {
            mMcuControlInterface.setRadioDriver(null);
        }
        this.mIsOpen = false;
        mDriverEvents.onClosed();
    }

    @Override
    public void raiseRts() {
        Timber.v("Raise RTS");
        if (mMcuControlInterface != null) {
            mMcuControlInterface.sendMcuCommand(MCUDefs.McuOutputCommand.RADIO_SET_RTS, true);
        }
    }

    @Override
    public void clearRts() {
        Timber.v("Clear RTS");
        if (mMcuControlInterface != null) {
            mMcuControlInterface.sendMcuCommand(MCUDefs.McuOutputCommand.RADIO_SET_RTS, false);
        }
    }

    @Override
    public void raiseDtr() {
        Timber.v("Raise DTR");
        if (mMcuControlInterface != null) {
            mMcuControlInterface.sendMcuCommand(MCUDefs.McuOutputCommand.RADIO_SET_DTR, true);
        }
    }

    @Override
    public void clearDtr() {
        Timber.v("Clear DTR");
        if (mMcuControlInterface != null) {
            mMcuControlInterface.sendMcuCommand(MCUDefs.McuOutputCommand.RADIO_SET_DTR, false);
        }
    }

    @Override
    public void writeData(byte[] bytes) {
        Timber.v("Write Data to Radio");
        if (mMcuControlInterface != null) {
            // write bytes to MCU using RADIO_SEND_PACKET command
            mMcuControlInterface.sendMcuCommand(MCUDefs.McuOutputCommand.RADIO_SEND_PACKET, bytes);
        }
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
