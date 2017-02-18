package com.arksine.autointegrate.interfaces;

import com.arksine.autointegrate.microcontroller.MCUDefs;

/**
 * Inteface to access MCU functionality
 */

public interface MCUControlInterface {
    void sendMcuCommand(MCUDefs.McuOutputCommand command, Object data);
    void setMode(boolean isLearningMode);
    boolean isConnected();

    // TODO: I'm sure there is more functionality that would be useful, like for example
    //       the ability to trigger commands without receiving input from the
    //       MCU;
}
