package com.arksine.autointegrate.microcontroller;

import com.arksine.autointegrate.utilities.UtilityFunctions;

/**
 * Represents a steering wheel button press, read by a Micro Controller
 */

public class ResistiveButton {

    private int id;         // Button id sent from micro controller
    private int tolerance;  // Margin of Error / Debounce
    private boolean multiplied;  // determine if the tolerance should use a multiplier

    private String clickType;
    private String clickAction;
    private String holdType;
    private String holdAction;

    public ResistiveButton() {
        this.id = 0;
        this.tolerance = 0;
        this.multiplied = false;
        this.clickType = "None";
        this.clickAction = "None";
        this.holdType = "None";
        this.holdAction = "None";
    }

    public ResistiveButton(int id, int tolerance, boolean multiplied, String clickType,
                           String clickAction, String holdAction, String holdType) {
        this.id = id;
        this.tolerance = tolerance;
        this.multiplied = multiplied;
        this.clickType = clickType;
        this.clickAction = clickAction;
        this.holdType = holdType;
        this.holdAction = holdAction;
    }

    public int getId() {
        return id;
    }

    // Returns the ID as a string with leading zeroes and surrounding brackets
    public String getIdAsString() {
        String strId = UtilityFunctions.addLeadingZeroes(String.valueOf(id), 5);
        return ("[" + strId + "]");
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getTolerance() {
        return tolerance;
    }

    public void setTolerance(int tolerance) {
        this.tolerance = tolerance;
    }

    public String getClickAction() {
        return clickAction;
    }

    public void setClickAction(String clickAction) {
        this.clickAction = clickAction;
    }

    public String getHoldAction() {
        return holdAction;
    }

    public void setHoldAction(String holdAction) {
        this.holdAction = holdAction;
    }

    public boolean isMultiplied() {
        return multiplied;
    }

    public void setMultiplied(boolean multiplied) {
        this.multiplied = multiplied;
    }

    public String getClickType() {
        return clickType;
    }

    public void setClickType(String clickType) {
        this.clickType = clickType;
    }

    public String getHoldType() {
        return holdType;
    }

    public void setHoldType(String holdType) {
        this.holdType = holdType;
    }

    @Override
    public boolean equals(Object obj) {
        if(!(obj instanceof ResistiveButton))
            return false;
        if (obj == this)
            return true;

        ResistiveButton btn =  (ResistiveButton) obj;
        return ((this.id == btn.id)
                && (this.tolerance == btn.tolerance)
                && (this.multiplied == btn.multiplied)
                && (this.clickType.equals(btn.clickType))
                && (this.clickAction.equals(btn.clickAction))
                && (this.holdType.equals(btn.holdType))
                && (this.holdAction.equals(btn.holdAction)));

    }

    public void assign(ResistiveButton btn) {
        this.id = btn.id;
        this.tolerance = btn.tolerance;
        this.multiplied = btn.multiplied;
        this.clickType = btn.clickType;
        this.clickAction = btn.clickAction;
        this.holdType = btn.holdType;
        this.holdAction = btn.holdAction;
    }
}
