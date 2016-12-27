package com.arksine.autointegrate.microcontroller;


/**
 * Container for a message recieved from the Micro Controller.  Packets are parsed into two parts, the
 * command received and its associated data
 */
public class ControllerMessage {

    public MCUDefs.DataType msgType;
    public MCUDefs.MCUCommand command;
    public MCUDefs.RadioCommand radioCmd;
    public Object data;
}
