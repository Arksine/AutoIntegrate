package com.arksine.autointegrate.interfaces;

import com.arksine.autointegrate.microcontroller.MicroControllerCom;

/**
 * Inteface to access MCU functionality
 */

public interface MCUControlInterface {
    void sendMcuCommand(String command, String data);
    void setMode(boolean isLearningMode);
    boolean isConnected();

    // TODO: I'm sure there is more functionality that would be useful, like for example
    //       the ability to trigger commands without receiving input from the
    //       MCU;
}
