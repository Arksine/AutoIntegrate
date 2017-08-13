package com.arksine.autointegrate.interfaces;

/**
 * Interface for learning event callbacks
 */

public interface McuLearnCallbacks {
    void onButtonClicked(int btnId);
    void onDimmerToggled(boolean dimmerStatus);
    void onDimmerLevelChanged(int dimmerLevel);
}
