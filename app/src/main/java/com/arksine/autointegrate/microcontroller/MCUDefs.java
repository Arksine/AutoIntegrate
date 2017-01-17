package com.arksine.autointegrate.microcontroller;


import com.arksine.autointegrate.utilities.DLog;

import java.util.EnumSet;

/**
 * Class containing definitions used by the mcu
 */

public class MCUDefs {

    private static final String TAG = MCUDefs.class.getSimpleName();

    private MCUDefs(){}

    /**
     * If you want to add your own command received from the MCU, add it here.  You can also use
     * the "CUSTOM" command to receive a String based command that will be broadcast in an intent
     */
    public enum MCUCommand {
        NONE((byte)0x00),
        CONNECTED(((byte)0x01)),
        CLICK((byte)0x02),
        HOLD(((byte)0x03)),
        RELEASE(((byte)0x04)),
        DIMMER(((byte)0x05)),
        REVERSE(((byte)0x06)),
        RADIO(((byte)0x07)),
        LOG(((byte)0x08)),
        CUSTOM(((byte)0x09));

        private final byte id;

        MCUCommand(byte _id) {
            this.id = (byte)_id;
        }

        public static MCUCommand getMcuCommand(byte id) {
            for (MCUCommand cmd : EnumSet.allOf(MCUCommand.class)) {
                if (cmd.id == id) {
                    return cmd;
                }
            }

            DLog.i(TAG, "Invalid id number: " + id);
            return MCUCommand.NONE;
        }
    }

    public enum RadioCommand {
        NONE((byte)0x00),
        POWER((byte)0x01),
        MUTE((byte)0x02),
        SIGNAL_STRENGTH((byte)0x03),
        TUNE((byte)0x04),
        SEEK((byte)0x05),
        HD_ACTIVE((byte)0x06),
        HD_STREAM_LOCK((byte)0x07),
        HD_SIGNAL_STRENGTH((byte)0x08),
        HD_SUBCHANNEL((byte)0x09),
        HD_SUBCHANNEL_COUNT((byte)0x0A),
        HD_ENABLE_HD_TUNER((byte)0x0B),
        HD_TITLE((byte)0x0C),
        HD_ARTIST((byte)0x0D),
        HD_CALLSIGN((byte)0x0E),
        HD_STATION_NAME((byte)0x0F),
        HD_UNIQUE_ID((byte)0x10),
        HD_API_VERSION((byte)0x11),
        HD_HW_VERSION((byte)0x12),
        RDS_ENABLED((byte)0x13),
        RDS_GENRE((byte)0x14),
        RDS_PROGRAM_SERVICE((byte)0x15),
        RDS_RADIO_TEXT((byte)0x16),
        VOLUME((byte)0x17),
        BASS((byte)0x18),
        TREBLE((byte)0x19),
        COMPRESSION((byte)0x1A);

        private final byte id;

        RadioCommand (byte _id) {
            this.id = _id;
        }

        public static RadioCommand getRadioCommand(byte id) {
            for (RadioCommand rc : EnumSet.allOf(RadioCommand.class)) {
                if (rc.id == id) {
                    return rc;
                }
            }

            DLog.i(TAG, "Invalid id number: " + id);
            return RadioCommand.NONE;
        }
    }


    public enum DataType {
        NONE((byte)0x00),
        SHORT((byte)0x01),
        INT((byte)0x02),
        STRING((byte)0x03),
        BOOLEAN((byte)0x04),
        TUNE_INFO((byte)0x05),
        HD_SONG_INFO((byte)0x06);

        private final byte id;

        DataType (byte _id) {
            this.id = _id;
        }


        public static DataType getDataType(byte id) {
            for (DataType dt : EnumSet.allOf(DataType.class)) {
                if (dt.id == id) {
                    return dt;
                }
            }

            DLog.i(TAG, "Invalid id number: " + id);
            return DataType.NONE;
        }
    }

}
