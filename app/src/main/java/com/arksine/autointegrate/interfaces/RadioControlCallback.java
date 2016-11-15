package com.arksine.autointegrate.interfaces;

/**
 * Created by Eric on 11/14/2016.
 */

public interface RadioControlCallback {

    void OnRadioDataReceived(String command, Object value);
    void OnRadioDataReceived(String command, int intValue);
    void OnRadioDataRecevied(String command, boolean boolValue);
    void OnRadioDataReceived(String command, String band, int frequency);
    void OnRadioDataReveived(String command, String songInfo, int subChannel);

    void OnExit();
}
