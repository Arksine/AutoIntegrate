package com.arksine.autointegrate.microcontroller;

import com.arksine.autointegrate.utilities.UtilityFunctions;

import timber.log.Timber;

/**
 * Class containing definitions used by the mcu
 */

public class MCUDefs {

    private MCUDefs(){}

    public enum McuInputCommand {
        NONE((byte)0x00, DataType.NONE),
        STARTED(((byte)0x01), DataType.STRING),     // MCU Start notification (includes MCU ID)
        IDENT((byte)0x02, DataType.STRING),         // Unique MCU ID
        CLICK((byte)0x03, DataType.SHORT),          // Steering Wheel Resistive Button Click
        HOLD(((byte)0x04), DataType.SHORT),         // Steering Wheel Resistive Button Hold
        RELEASE(((byte)0x05), DataType.SHORT),      // Steering Wheel Resistive Button Release
        DIMMER(((byte)0x06), DataType.BOOLEAN),     // Dimmer On/Off notification
        DIMMER_LEVEL((byte)0x07, DataType.SHORT),   // Dimmer Level (when Analog Mode is enabled)
        REVERSE(((byte)0x08), DataType.BOOLEAN),    // Reverse On/Off Notification
        RADIO_STATUS((byte)0x09, DataType.BOOLEAN), // Radio Connected Status
        RADIO_DATA((byte)0x0A, DataType.BYTE_ARRAY),  // Data from HD Radio Received
        LOG(((byte)0x0B), DataType.STRING),           // MCU Logging Info
        CUSTOM(((byte)0x0C), DataType.BYTE_ARRAY);    // Custom MCU Command (Could do radio here?)

        private final byte id;
        private final DataType dType;

        McuInputCommand(byte _id, DataType type) {
            this.id = (byte)_id;
            this.dType = type;
        }

        private static final McuInputCommand[] COMMAND_ARRAY = McuInputCommand.values();

        public static McuInputCommand getCommandFromByte(byte id) {
            for (McuInputCommand cmd : COMMAND_ARRAY) {
                if (cmd.id == id) {
                    return cmd;
                }
            }

            Timber.i("Invalid Input Command ID: %#x", id);
            return McuInputCommand.NONE;
        }

        public DataType getDataType() {
            return this.dType;
        }
    }


    public enum McuOutputCommand {
        NONE((byte)0x00),
        START((byte)0x01),                  // Start MCU Main Loop
        STOP((byte)0x02),                   // Stop MCU Main Loop
        REQUEST_ID((byte)0x03),             // Request MCU ID
        SET_DIMMER_ANALOG((byte)0x04),      // Set MCU to measure Dimmer Voltage (Analog Mode)
        SET_DIMMER_DIGITAL((byte)0x05),     // Set MCU to only notify when Dimmer is On or Off
        AUDIO_SOURCE_HD((byte)0x06),        // Set MCU to toggle Audio Relay to HD Radio
        AUDIO_SOURCE_AUX((byte)0x07),       // Set MCU to toggle Audio Relay to Aux
        RADIO_REQUEST_STATUS((byte)0x08),   // Request Radio connected status from MCU
        RADIO_SEND_PACKET((byte)0x09),      // Send HD Radio Command through the MCU
        RADIO_SET_DTR((byte)0x0A),          // Toggle HD Radio DTR (Turns Radio On/Off)
        RADIO_SET_RTS((byte)0x0B),          // Toggle HD Radio RTS (HD Radio Hardware Mute)
        CUSTOM((byte)0x0C);                 // Send MCU a Custom Command

        private final byte id;

        McuOutputCommand(byte _id) {
            this.id = (byte)_id;
        }

        public byte getByte() {
            return id;
        }

        private static final McuOutputCommand[] COMMAND_ARRAY = McuOutputCommand.values();

        public static McuOutputCommand getCommand(byte id) {
            for (McuOutputCommand cmd : COMMAND_ARRAY) {
                if (cmd.id == id) {
                    return cmd;
                }
            }

            Timber.i("Invalid Output Command ID: %#x", id);
            return McuOutputCommand.NONE;
        }

        public static McuOutputCommand getCommandFromOrdinal(int ord) {
            if (ord < 0 || ord >= COMMAND_ARRAY.length) {
                return McuOutputCommand.NONE;
            }
            return COMMAND_ARRAY[ord];
        }

    }

    public enum DataType {
        NONE((byte)0x00),
        SHORT((byte)0x01),
        INT((byte)0x02),
        STRING((byte)0x03),
        BOOLEAN((byte)0x04),
        BYTE_ARRAY((byte)0x05);

        private final byte id;

        DataType (byte _id) {
            this.id = _id;
        }

        private static final DataType[] TYPE_ARRAY = DataType.values();

        public static DataType getDataType(byte id) {
            for (DataType dt : TYPE_ARRAY) {
                if (dt.id == id) {
                    return dt;
                }
            }

            Timber.i("Invalid DataType ID: %#x", id);
            return DataType.NONE;
        }
    }

}
