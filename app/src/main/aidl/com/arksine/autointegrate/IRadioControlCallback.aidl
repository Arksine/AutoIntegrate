// IRadioControlCallback.aidl
package com.arksine.autointegrate;

// Declare any non-default types here with import statements

interface IRadioControlCallback {
	void OnBooleanValueReceived(String command, boolean newValue);
	void OnIntegerValueReceived(String command, int newValue);
	void OnStringValueReceived(String command, String newValue);
    void OnTuneReply(String command, String band, int frequency);
    void OnHdSongInfoReceived(String command, String songInfo, int subChannel);
}
