package com.arksine.autointegrate.interfaces;

import com.arksine.autointegrate.microcontroller.MCUDefs.*;
import com.arksine.autointegrate.microcontroller.McuRadioDriver;
import com.arksine.autointegrate.microcontroller.ResistiveButton;

import java.util.List;

/**
 * Inteface to access MCU functionality
 */

public interface MCUControlInterface {
    void sendMcuCommand(McuOutputCommand command, Object data);
    void setMode(boolean isLearningMode, McuLearnCallbacks cbs);
    void updateBaud(int baud);
    void resumeFromWait();
    boolean setRadioDriver(McuRadioDriver radioDriver);
    boolean isConnected();
    String getDeviceId();

    void updateButtonMap(List<ResistiveButton> buttonList);
    void updateDimmerMap(int mode, final int highReading, final int lowReading,
                         final int highBrightness, final int lowBrightness);
    void updateReverseMap(String camSetting, String appPackage);

    // TODO: I'm sure there is more functionality that would be useful, like for example
    //       the ability to trigger commands without receiving input from the
    //       MCU;
}
