package com.arksine.autointegrate.interfaces;

/**
 * Created by Eric on 8/13/2017.
 */

public interface ServiceControlInterface {
    void wakeUpDevice();
    void suspendDevice();
    void refreshMcuConnection(boolean learningMode, McuLearnCallbacks cbs);
    void refreshRadioConnection();
}
