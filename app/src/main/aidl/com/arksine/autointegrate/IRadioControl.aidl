// IRadioControl.aidl
package com.arksine.autointegrate;

// Declare any non-default types here with import statements

interface IRadioControl {
    void setSeekAll(boolean seekAll);
    boolean getSeekAll();

    void togglePower(boolean status);
    void toggleMute(boolean status);

    void setVolume(int volume);
    void setVolumeUp();
    void setVolumeDown();

    void setBass(int bass);
    void setBassUp();
    void setBassDown();

    void setTreble(int treble);
    void setTrebleUp();
    void setTrebleDown();


    void tune(String band, int frequency, int subchannel);
    void tuneUp();
    void tuneDown();

    void seekUp();
    void seekDown();

    void requestUpdate(String key);
}
