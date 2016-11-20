package com.arksine.autointegrate.interfaces;

import android.provider.MediaStore;

import com.arksine.autointegrate.radio.RadioKey;

/**
 * Provides and interface for Activities within the package context to control the HD radio
 */

public interface RadioControlInterface {
    void setSeekAll(boolean seekAll);
    boolean getSeekAll();

    void togglePower(boolean status);
    boolean getPowerStatus();

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


    void tune(RadioKey.Band band, int frequency, int subchannel);
    void tuneUp();
    void tuneDown();

    void seekUp();
    void seekDown();

    void requestUpdate(RadioKey.Command key);
    Object getHdValue(RadioKey.Command key);
}
