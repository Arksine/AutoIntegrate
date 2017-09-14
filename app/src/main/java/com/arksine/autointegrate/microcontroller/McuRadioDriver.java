package com.arksine.autointegrate.microcontroller;

import android.support.annotation.NonNull;

import com.arksine.autointegrate.AutoIntegrate;
import com.arksine.autointegrate.interfaces.MCUControlInterface;
import com.arksine.hdradiolib.drivers.RadioDriver;
import com.arksine.hdradiolib.enums.RadioError;

import java.util.ArrayList;

import timber.log.Timber;

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

    public McuRadioDriver() {
        this.mControlInterface = AutoIntegrate.getmMcuControlInterface();
        this.mIsOpen = false;
    }

    public void updateMcuInterface() {
        this.mControlInterface = AutoIntegrate.getmMcuControlInterface();
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
        if (mControlInterface != null) {
            return mControlInterface.getDeviceId();
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
        if (!this.mIsOpen && this.mControlInterface != null) {
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
        if (this.mControlInterface != null) {
            this.mControlInterface.setRadioDriver(null);
        }
        this.mIsOpen = false;
        mDriverEvents.onClosed();
    }

    @Override
    public void raiseRts() {
        Timber.v("Raise RTS");
        if (mControlInterface != null) {
            mControlInterface.sendMcuCommand(MCUDefs.McuOutputCommand.RADIO_SET_RTS, true);
        }
    }

    @Override
    public void clearRts() {

        Timber.v("Clear RTS");
        if (mControlInterface != null) {
            mControlInterface.sendMcuCommand(MCUDefs.McuOutputCommand.RADIO_SET_RTS, false);
        }
    }

    @Override
    public void raiseDtr() {
        Timber.v("Raise DTR");
        if (mControlInterface != null) {
            mControlInterface.sendMcuCommand(MCUDefs.McuOutputCommand.RADIO_SET_DTR, true);
        }
    }

    @Override
    public void clearDtr() {
        Timber.v("Clear DTR");
        if (mControlInterface != null) {
            mControlInterface.sendMcuCommand(MCUDefs.McuOutputCommand.RADIO_SET_DTR, false);
        }
    }

    @Override
    public void writeData(byte[] bytes) {
        Timber.v("Write Data to Radio");
        if (mControlInterface != null) {
            // write bytes to MCU using RADIO_SEND_PACKET command
            mControlInterface.sendMcuCommand(MCUDefs.McuOutputCommand.RADIO_SEND_PACKET, bytes);
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
