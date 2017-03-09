package com.arksine.autointegrate.microcontroller;


import com.arksine.autointegrate.utilities.DLog;

import java.util.EnumSet;

/**
 * Class containing definitions used by the mcu
 */

public class MCUDefs {

    private static final String TAG = MCUDefs.class.getSimpleName();

    private MCUDefs(){}

    public enum McuInputCommand {
        NONE((byte)0x00),
        STARTED(((byte)0x01)),
        IDENT((byte)0x02),
        CLICK((byte)0x03),
        HOLD(((byte)0x04)),
        RELEASE(((byte)0x05)),
        DIMMER(((byte)0x06)),
        REVERSE(((byte)0x07)),
        LOG(((byte)0x08)),
        CUSTOM(((byte)0x09));

        private final byte id;

        McuInputCommand(byte _id) {
            this.id = (byte)_id;
        }

        private static final McuInputCommand[] COMMAND_ARRAY = McuInputCommand.values();

        public static McuInputCommand getCommandFromByte(byte id) {
            for (McuInputCommand cmd : COMMAND_ARRAY) {
                if (cmd.id == id) {
                    return cmd;
                }
            }

            DLog.i(TAG, "Invalid id number: " + id);
            return McuInputCommand.NONE;
        }
    }


    public enum McuOutputCommand {
        NONE((byte)0x00),
        START((byte)0x01),
        STOP((byte)0x02),
        REQUEST_ID((byte)0x03),
        SET_DIMMER_ANALOG((byte)0x04),
        SET_DIMMER_DIGITAL((byte)0x05),
        AUDIO_SOURCE_HD((byte)0x06),
        AUDIO_SOURCE_AUX((byte)0x07),
        RADIO_SEND_PACKET((byte)0x08),
        RADIO_SET_DTR((byte)0x09),
        RADIO_SET_RTS((byte)0x0A),
        CUSTOM((byte)0x0B);

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

            DLog.i(TAG, "Invalid id number: " + id);
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

            DLog.i(TAG, "Invalid id number: " + id);
            return DataType.NONE;
        }
    }

}
