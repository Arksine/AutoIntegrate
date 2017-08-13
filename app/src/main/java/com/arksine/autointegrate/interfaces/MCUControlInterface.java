package com.arksine.autointegrate.interfaces;

import com.arksine.autointegrate.microcontroller.MCUDefs.*;
import com.arksine.autointegrate.microcontroller.McuRadioDriver;

/**
 * Inteface to access MCU functionality
 */

public interface MCUControlInterface {
    void sendMcuCommand(McuOutputCommand command, Object data);
    void setMode(boolean isLearningMode);
    void resumeFromWait();
    boolean setRadioDriver(McuRadioDriver radioDriver);
    boolean isConnected();
    String getDeviceId();

    // TODO: I'm sure there is more functionality that would be useful, like for example
    //       the ability to trigger commands without receiving input from the
    //       MCU;
}
