package com.arksine.autointegrate.interfaces;

/**
 * Provides and interface for Activities within the package context to control the HD radio
 */

public interface RadioControlInterface {
    void setSeekAll(boolean seekAll);
    boolean getSeekAll();

    void togglePower(boolean status);
    boolean getPowerStatus();

    void toggleMute(boolean status);
    boolean getMuteStatus();

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
}
