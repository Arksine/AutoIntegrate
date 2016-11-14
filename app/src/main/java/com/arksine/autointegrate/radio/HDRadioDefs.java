package com.arksine.autointegrate.radio;

import android.util.ArrayMap;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Created by Eric on 11/6/2016.
 */

public class HDRadioDefs {
    private static final String TAG = "HDRadioDefs";

    private static final ArrayMap<String, RadioCommand> mCommands = new ArrayMap<>(26);
    private static final ArrayMap<String, byte[]> mOperations = new ArrayMap<>(3);
    private static final ArrayMap<String, byte[]> mBands = new ArrayMap<>(2);
    private static final ArrayMap<String, byte[]> mConstants = new ArrayMap<>(7);

    private HDRadioDefs() {}

    static {
        mCommands.put("power", new RadioCommand("power", RadioCommand.Type.BOOLEAN,
                new byte[]{(byte)0x01, (byte)0x00}));
        mCommands.put("mute", new RadioCommand("mute", RadioCommand.Type.BOOLEAN,
                new byte[]{(byte)0x02, (byte)0x00}));
        mCommands.put("signal_strength", new RadioCommand("signal_strength", RadioCommand.Type.INT,
                new byte[]{(byte)0x01, (byte)0x01}));
        mCommands.put("tune", new RadioCommand("tune", RadioCommand.Type.TUNEINFO,
                new byte[]{(byte)0x02, (byte)0x01}));
        mCommands.put("seek", new RadioCommand("seek", RadioCommand.Type.NONE,
                new byte[]{(byte)0x03, (byte)0x01}));
        mCommands.put("hd_active", new RadioCommand("hd_active", RadioCommand.Type.BOOLEAN,
                new byte[]{(byte)0x01, (byte)0x02}));
        mCommands.put("hd_stream_lock", new RadioCommand("hd_stream_lock", RadioCommand.Type.BOOLEAN,
                new byte[]{(byte)0x02, (byte)0x02}));
        mCommands.put("hd_signal_strength", new RadioCommand("hd_signal_strength", RadioCommand.Type.INT,
                new byte[]{(byte)0x03, (byte)0x02}));
        mCommands.put("hd_sub_channel", new RadioCommand("hd_sub_channel", RadioCommand.Type.INT,
                new byte[]{(byte)0x04, (byte)0x02}));
        mCommands.put("hd_sub_channel_count", new RadioCommand("hd_sub_channel_count", RadioCommand.Type.INT,
                new byte[]{(byte)0x05, (byte)0x02}));
        mCommands.put("hd_enable_hd_tuner", new RadioCommand("hd_enable_hd_tuner", RadioCommand.Type.BOOLEAN,
                new byte[]{(byte)0x06, (byte)0x02}));
        mCommands.put("hd_title", new RadioCommand("hd_title", RadioCommand.Type.HDSONGINFO,
                new byte[]{(byte)0x07, (byte)0x02}));
        mCommands.put("hd_artist", new RadioCommand("hd_artist", RadioCommand.Type.HDSONGINFO,
                new byte[]{(byte)0x08, (byte)0x02}));
        mCommands.put("hd_callsign", new RadioCommand("hd_callsign", RadioCommand.Type.STRING,
                new byte[]{(byte)0x09, (byte)0x02}));
        mCommands.put("hd_station_name", new RadioCommand("hd_station_name", RadioCommand.Type.STRING,
                new byte[]{(byte)0x10, (byte)0x02}));
        mCommands.put("hd_unique_id", new RadioCommand("hd_unique_id", RadioCommand.Type.STRING,
                new byte[]{(byte)0x11, (byte)0x02}));
        mCommands.put("hd_api_version", new RadioCommand("hd_api_verson", RadioCommand.Type.STRING,
                new byte[]{(byte)0x12, (byte)0x02}));
        mCommands.put("hd_hw_version", new RadioCommand("hd_hw_version", RadioCommand.Type.STRING,
                new byte[]{(byte)0x13, (byte)0x02}));   // HDRC app had this as 0x12, test 0x13
        mCommands.put("rds_enable", new RadioCommand("rds_enable", RadioCommand.Type.BOOLEAN,
                new byte[]{(byte)0x01, (byte)0x03}));
        mCommands.put("rds_genre", new RadioCommand("rds_genre", RadioCommand.Type.STRING,
                new byte[]{(byte)0x07, (byte)0x03}));
        mCommands.put("rds_program_service", new RadioCommand("rds_program_service", RadioCommand.Type.STRING,
                new byte[]{(byte)0x08, (byte)0x03}));
        mCommands.put("rds_radio_text", new RadioCommand("rds_radio_text", RadioCommand.Type.STRING,
                new byte[]{(byte)0x09, (byte)0x03}));
        mCommands.put("volume", new RadioCommand("volume", RadioCommand.Type.INT,
                new byte[]{(byte)0x03, (byte)0x04}));
        mCommands.put("bass", new RadioCommand("bass", RadioCommand.Type.INT,
                new byte[]{(byte)0x04, (byte)0x04}));
        mCommands.put("treble", new RadioCommand("treble", RadioCommand.Type.INT,
                new byte[]{(byte)0x05, (byte)0x04}));       // HDRC had this as 0x04, test as 0x05
        mCommands.put("compression", new RadioCommand("compression", RadioCommand.Type.INT,
                new byte[]{(byte)0x06, (byte)0x04}));  // HDRC had this as 0x05, test as 0x06

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

    public static byte getStartByte() {
        return (byte) 0xA4;
    }

    public static byte getEscapeByte() {
        return (byte) 0x1B;
    }

    public static byte[] getCommandBytes(String key) {
        RadioCommand cmd = mCommands.get(key);
        if (cmd == null) {
            return null;
        } else {
            return cmd.getCommandBytes();
        }
    }


    public static byte[] getOpBytes(String key) {
        return mOperations.get(key);
    }

    public static byte[] getBandBytes(String key) {
        return mBands.get(key);
    }

    public static byte[] getConstantBytes(String key) {
        return mConstants.get(key);
    }

    //** Bytes are received from HD radio in little endian format, so we will convert in the same manner

    public static RadioCommand getCommandFromValue(int value) {
        for (int i = 0; i < mCommands.size(); i++) {
            RadioCommand cmd = mCommands.valueAt(i);
            ByteBuffer buf = ByteBuffer.wrap(cmd.getCommandBytes());
            buf.order(ByteOrder.LITTLE_ENDIAN);
            int byteVal = buf.getShort();

            if (value == byteVal) {
                return cmd;
            }
        }

        // no key found for value
        return null;
    }

    public static int getOpValue(String key) {
        byte[] bytes = mOperations.get(key);

        if (bytes == null) {
            return -1;
        }

        ByteBuffer buf = ByteBuffer.wrap(bytes);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        return buf.getShort();
    }

    public static int getBandValue(String key) {
        byte[] bytes = mBands.get(key);

        if (bytes == null) {
            return -1;
        }

        ByteBuffer buf = ByteBuffer.wrap(bytes);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        return buf.getInt();
    }

    public static int getConstantValue(String key) {
        byte[] bytes = mConstants.get(key);

        if (bytes == null) {
            return -1;
        }
        ByteBuffer buf = ByteBuffer.wrap(bytes);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        return buf.getInt();

    }

    public static RadioCommand getRadioCommand(String key) {
        return mCommands.get(key);
    }

    public static void setCommandData(String key, Object data) {
        RadioCommand cmd = mCommands.get(key);
        if (cmd != null) {
            cmd.setData(data);
        }
    }

    public static Object getCommandData(String key) {
        RadioCommand cmd = mCommands.get(key);
        if (cmd != null) {
            return cmd.getData();
        } else {
            return null;
        }
    }


}
