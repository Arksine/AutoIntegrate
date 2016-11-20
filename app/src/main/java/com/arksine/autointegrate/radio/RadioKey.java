package com.arksine.autointegrate.radio;

/**
 * Keys used to track, get, and update HD Radio Variables
 */

public class RadioKey {
    private RadioKey(){}

    public enum Command {
        POWER,
        MUTE,
        SIGNAL_STRENGTH,
        TUNE,
        SEEK,
        HD_ACTIVE,
        HD_STREAM_LOCK,
        HD_SIGNAL_STRENGH,
        HD_SUBCHANNEL,
        HD_SUBCHANNEL_COUNT,
        HD_TUNER_ENABLED,
        HD_TITLE,
        HD_ARTIST,
        HD_CALLSIGN,
        HD_STATION_NAME,
        HD_UNIQUE_ID,
        HD_API_VERSION,
        HD_HW_VERSION,
        RDS_ENABLED,
        RDS_GENRE,
        RDS_PROGRAM_SERVICE,
        RDS_RADIO_TEXT,
        VOLUME,
        BASS,
        TREBLE,
        COMPRESSION
    }

    public enum Operation {
        SET,
        GET,
        REPLY
    }

    public enum Band {
        AM,
        FM
    }

    public enum Constant {
        UP,
        DOWN,
        ONE,
        ZERO,
        SEEK_REQ_ID,
        SEEK_ALL_ID,
        SEEK_HD_ONLY_ID
    }
}
