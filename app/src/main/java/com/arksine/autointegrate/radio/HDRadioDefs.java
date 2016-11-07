package com.arksine.autointegrate.radio;

import android.util.ArrayMap;

/**
 * Created by Eric on 11/6/2016.
 */

public class HDRadioDefs {
    private byte mStartByte;
    private byte mEscapeByte;
    private ArrayMap<String, byte[]> mCommands;
    private ArrayMap<String, byte[]> mOperations;
    private ArrayMap<String, byte[]> mBands;
    private ArrayMap<String, byte[]> mConstants;

    public HDRadioDefs() {
        mStartByte = (byte)0xA4;
        mEscapeByte = (byte)0x1B;

        mCommands = new ArrayMap<>(26);
        mOperations = new ArrayMap<>(3);
        mBands = new ArrayMap<>(2);
        mConstants = new ArrayMap<>(4);

        initDefinitions();
    }

    private void initDefinitions() {

        mCommands.put("power", new byte[]{(byte)0x01, (byte)0x00});
        mCommands.put("mute", new byte[]{(byte)0x02, (byte)0x00});
        mCommands.put("signal_strength", new byte[]{(byte)0x01, (byte)0x01});
        mCommands.put("tune", new byte[]{(byte)0x02, (byte)0x01});
        mCommands.put("seek", new byte[]{(byte)0x03, (byte)0x01});
        mCommands.put("hd_active", new byte[]{(byte)0x01, (byte)0x02});
        mCommands.put("hd_stream_lock", new byte[]{(byte)0x02, (byte)0x02});
        mCommands.put("hd_signal_strength", new byte[]{(byte)0x03, (byte)0x02});
        mCommands.put("hd_sub_channel", new byte[]{(byte)0x04, (byte)0x02});
        mCommands.put("hd_sub_channel_count", new byte[]{(byte)0x05, (byte)0x02});
        mCommands.put("hd_enable_hd_tuner", new byte[]{(byte)0x06, (byte)0x02});
        mCommands.put("hd_title", new byte[]{(byte)0x07, (byte)0x02});
        mCommands.put("hd_artist", new byte[]{(byte)0x08, (byte)0x02});
        mCommands.put("hd_callsign", new byte[]{(byte)0x09, (byte)0x02});
        mCommands.put("hd_station_name", new byte[]{(byte)0x10, (byte)0x02});
        mCommands.put("hd_unique_id", new byte[]{(byte)0x11, (byte)0x02});
        mCommands.put("hd_api_version", new byte[]{(byte)0x12, (byte)0x02});
        mCommands.put("hd_hw_version", new byte[]{(byte)0x12, (byte)0x02});   // this is the same as above, maybe its supposed to be 13?
        mCommands.put("rds_enable", new byte[]{(byte)0x01, (byte)0x03});
        mCommands.put("rds_genre", new byte[]{(byte)0x07, (byte)0x03});
        mCommands.put("rds_program_service", new byte[]{(byte)0x08, (byte)0x03});
        mCommands.put("rds_radio_text", new byte[]{(byte)0x09, (byte)0x03});
        mCommands.put("volume", new byte[]{(byte)0x03, (byte)0x04});
        mCommands.put("bass", new byte[]{(byte)0x04, (byte)0x04});
        mCommands.put("treble", new byte[]{(byte)0x04, (byte)0x04});       // this is the same as above, should be another value?
        mCommands.put("compression", new byte[]{(byte)0x05, (byte)0x04});

        mOperations.put("set", new byte[]{(byte)0x00, (byte)0x00});
        mOperations.put("get", new byte[]{(byte)0x01, (byte)0x00});
        mOperations.put("reply", new byte[]{(byte)0x02, (byte)0x00});

        mBands.put("am", new byte[]{(byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00});
        mBands.put("fm", new byte[]{(byte)0x01, (byte)0x00, (byte)0x00, (byte)0x00});

        mConstants.put("up",  new byte[]{(byte)0x01, (byte)0x00, (byte)0x00, (byte)0x00});
        mConstants.put("down",  new byte[]{(byte)0xFF, (byte)0xFF, (byte)0xFF, (byte)0xFF});
        mConstants.put("one",  new byte[]{(byte)0x01, (byte)0x00, (byte)0x00, (byte)0x00});
        mConstants.put("zero",  new byte[]{(byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00});
        mConstants.put("seek_id", new byte[]{(byte)0xA5, (byte)0x00, (byte)0x00, (byte)0x00});
        mConstants.put("seek_all", new byte[]{(byte)0x00, (byte)0x00, (byte)0x3D, (byte)0x00});
        mConstants.put("seek_hd_only", new byte[]{(byte)0x00, (byte)0x00, (byte)0x3D, (byte)0x01});


    }

    public byte getStartByte() {
        return mStartByte;
    }

    public byte getEscapeByte() {
        return mEscapeByte;
    }

    public byte[] getCommand(String def) {
        return mCommands.get(def);
    }

    public byte[] getOp(String op) {
        return mOperations.get(op);
    }

    public byte[] getBand(String band) {
        return mBands.get(band);
    }

    public byte[] getConstant(String constant) {
        return mConstants.get(constant);
    }

}
