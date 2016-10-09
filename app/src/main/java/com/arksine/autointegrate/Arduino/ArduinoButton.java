package com.arksine.autointegrate.Arduino;

/**
 * Created by Eric on 10/7/2016.
 */

public class ArduinoButton {

    private int id;         // Button id sent from arduino
    private int tolerance;  // Margin of Error
    private String clickAction;
    private String holdAction;

    public ArduinoButton(int id, int tolerance, String clickAction,
                         String holdAction) {
        this.id = id;
        this.tolerance = tolerance;
        this.clickAction = clickAction;
        this.holdAction = holdAction;
    }

    public int getId() {
        return id;
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

}
