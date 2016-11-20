package com.arksine.autointegrate.radio;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;

/**
 *  HD Radio definition maps
 */

public class HDRadioDefs {
    private static final String TAG = "HDRadioDefs";

    private static final HashMap<RadioKey.Command, RadioCommand> mCommands = new HashMap<>(26);
    private static final HashMap<RadioKey.Operation, byte[]> mOperations = new HashMap<>(3);
    private static final HashMap<RadioKey.Band, byte[]> mBands = new HashMap<>(2);
    private static final HashMap<RadioKey.Constant, byte[]> mConstants = new HashMap<>(7);

    private HDRadioDefs() {}

    static {
        mCommands.put(RadioKey.Command.POWER, new RadioCommand(RadioKey.Command.POWER, RadioCommand.Type.BOOLEAN,
                new byte[]{(byte)0x01, (byte)0x00}));
        mCommands.put(RadioKey.Command.MUTE, new RadioCommand(RadioKey.Command.MUTE, RadioCommand.Type.BOOLEAN,
                new byte[]{(byte)0x02, (byte)0x00}));
        mCommands.put(RadioKey.Command.SIGNAL_STRENGTH, new RadioCommand(RadioKey.Command.SIGNAL_STRENGTH, RadioCommand.Type.INT,
                new byte[]{(byte)0x01, (byte)0x01}));
        mCommands.put(RadioKey.Command.TUNE, new RadioCommand(RadioKey.Command.TUNE, RadioCommand.Type.TUNEINFO,
                new byte[]{(byte)0x02, (byte)0x01}));
        mCommands.put(RadioKey.Command.SEEK, new RadioCommand(RadioKey.Command.SEEK, RadioCommand.Type.TUNEINFO,
                new byte[]{(byte)0x03, (byte)0x01}));
        mCommands.put(RadioKey.Command.HD_ACTIVE, new RadioCommand(RadioKey.Command.HD_ACTIVE, RadioCommand.Type.BOOLEAN,
                new byte[]{(byte)0x01, (byte)0x02}));
        mCommands.put(RadioKey.Command.HD_STREAM_LOCK, new RadioCommand(RadioKey.Command.HD_STREAM_LOCK, RadioCommand.Type.BOOLEAN,
                new byte[]{(byte)0x02, (byte)0x02}));
        mCommands.put(RadioKey.Command.HD_SIGNAL_STRENGH, new RadioCommand(RadioKey.Command.HD_SIGNAL_STRENGH, RadioCommand.Type.INT,
                new byte[]{(byte)0x03, (byte)0x02}));
        mCommands.put(RadioKey.Command.HD_SUBCHANNEL, new RadioCommand(RadioKey.Command.HD_SUBCHANNEL, RadioCommand.Type.INT,
                new byte[]{(byte)0x04, (byte)0x02}));
        mCommands.put(RadioKey.Command.HD_SUBCHANNEL_COUNT, new RadioCommand(RadioKey.Command.HD_SUBCHANNEL_COUNT, RadioCommand.Type.INT,
                new byte[]{(byte)0x05, (byte)0x02}));
        mCommands.put(RadioKey.Command.HD_TUNER_ENABLED, new RadioCommand(RadioKey.Command.HD_TUNER_ENABLED, RadioCommand.Type.BOOLEAN,
                new byte[]{(byte)0x06, (byte)0x02}));
        mCommands.put(RadioKey.Command.HD_TITLE, new RadioCommand(RadioKey.Command.HD_TITLE, RadioCommand.Type.HDSONGINFO,
                new byte[]{(byte)0x07, (byte)0x02}));
        mCommands.put(RadioKey.Command.HD_ARTIST, new RadioCommand(RadioKey.Command.HD_ARTIST, RadioCommand.Type.HDSONGINFO,
                new byte[]{(byte)0x08, (byte)0x02}));
        mCommands.put(RadioKey.Command.HD_CALLSIGN, new RadioCommand(RadioKey.Command.HD_CALLSIGN, RadioCommand.Type.STRING,
                new byte[]{(byte)0x09, (byte)0x02}));
        mCommands.put(RadioKey.Command.HD_STATION_NAME, new RadioCommand(RadioKey.Command.HD_STATION_NAME, RadioCommand.Type.STRING,
                new byte[]{(byte)0x10, (byte)0x02}));
        mCommands.put(RadioKey.Command.HD_UNIQUE_ID, new RadioCommand(RadioKey.Command.HD_UNIQUE_ID, RadioCommand.Type.STRING,
                new byte[]{(byte)0x11, (byte)0x02}));
        mCommands.put(RadioKey.Command.HD_API_VERSION, new RadioCommand(RadioKey.Command.HD_API_VERSION, RadioCommand.Type.STRING,
                new byte[]{(byte)0x12, (byte)0x02}));
        mCommands.put(RadioKey.Command.HD_HW_VERSION, new RadioCommand(RadioKey.Command.HD_HW_VERSION, RadioCommand.Type.STRING,
                new byte[]{(byte)0x13, (byte)0x02}));   // HDRC app had this as 0x12, test 0x13
        mCommands.put(RadioKey.Command.RDS_ENABLED, new RadioCommand(RadioKey.Command.RDS_ENABLED, RadioCommand.Type.BOOLEAN,
                new byte[]{(byte)0x01, (byte)0x03}));
        mCommands.put(RadioKey.Command.RDS_GENRE, new RadioCommand(RadioKey.Command.RDS_GENRE, RadioCommand.Type.STRING,
                new byte[]{(byte)0x07, (byte)0x03}));
        mCommands.put(RadioKey.Command.RDS_PROGRAM_SERVICE, new RadioCommand(RadioKey.Command.RDS_PROGRAM_SERVICE, RadioCommand.Type.STRING,
                new byte[]{(byte)0x08, (byte)0x03}));
        mCommands.put(RadioKey.Command.RDS_RADIO_TEXT, new RadioCommand(RadioKey.Command.RDS_RADIO_TEXT, RadioCommand.Type.STRING,
                new byte[]{(byte)0x09, (byte)0x03}));
        mCommands.put(RadioKey.Command.VOLUME, new RadioCommand(RadioKey.Command.VOLUME, RadioCommand.Type.INT,
                new byte[]{(byte)0x03, (byte)0x04}));
        mCommands.put(RadioKey.Command.BASS, new RadioCommand(RadioKey.Command.BASS, RadioCommand.Type.INT,
                new byte[]{(byte)0x04, (byte)0x04}));
        mCommands.put(RadioKey.Command.TREBLE, new RadioCommand(RadioKey.Command.TREBLE, RadioCommand.Type.INT,
                new byte[]{(byte)0x05, (byte)0x04}));       // HDRC had this as 0x04, test as 0x05
        mCommands.put(RadioKey.Command.COMPRESSION, new RadioCommand(RadioKey.Command.COMPRESSION, RadioCommand.Type.INT,
                new byte[]{(byte)0x06, (byte)0x04}));  // HDRC had this as 0x05, test as 0x06

        mOperations.put(RadioKey.Operation.SET, new byte[]{(byte)0x00, (byte)0x00});
        mOperations.put(RadioKey.Operation.GET, new byte[]{(byte)0x01, (byte)0x00});
        mOperations.put(RadioKey.Operation.REPLY, new byte[]{(byte)0x02, (byte)0x00});

        mBands.put(RadioKey.Band.AM, new byte[]{(byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00});
        mBands.put(RadioKey.Band.FM, new byte[]{(byte)0x01, (byte)0x00, (byte)0x00, (byte)0x00});

        mConstants.put(RadioKey.Constant.UP,  new byte[]{(byte)0x01, (byte)0x00, (byte)0x00, (byte)0x00});
        mConstants.put(RadioKey.Constant.DOWN,  new byte[]{(byte)0xFF, (byte)0xFF, (byte)0xFF, (byte)0xFF});
        mConstants.put(RadioKey.Constant.ONE,  new byte[]{(byte)0x01, (byte)0x00, (byte)0x00, (byte)0x00});
        mConstants.put(RadioKey.Constant.ZERO,  new byte[]{(byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00});
        mConstants.put(RadioKey.Constant.SEEK_REQ_ID, new byte[]{(byte)0xA5, (byte)0x00, (byte)0x00, (byte)0x00});
        mConstants.put(RadioKey.Constant.SEEK_ALL_ID, new byte[]{(byte)0x00, (byte)0x00, (byte)0x3D, (byte)0x00});
        mConstants.put(RadioKey.Constant.SEEK_HD_ONLY_ID, new byte[]{(byte)0x00, (byte)0x00, (byte)0x3D, (byte)0x01});

    }

    public static byte[] getCommandBytes(RadioKey.Command key) {
        RadioCommand cmd = mCommands.get(key);
        if (cmd == null) {
            return null;
        } else {
            return cmd.getCommandBytes();
        }
    }

    public static byte[] getOpBytes(RadioKey.Operation key) {
        return mOperations.get(key);
    }

    public static byte[] getBandBytes(RadioKey.Band key) {
        return mBands.get(key);
    }

    public static byte[] getConstantBytes(RadioKey.Constant key) {
        return mConstants.get(key);
    }

    //** Bytes are received from HD radio in little endian format, so we will convert in the same manner

    public static RadioCommand getCommandFromValue(int value) {
        for (RadioCommand cmd : mCommands.values()) {
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

    public static int getOpValue(RadioKey.Operation key) {
        byte[] bytes = mOperations.get(key);

        if (bytes == null) {
            return -1;
        }

        ByteBuffer buf = ByteBuffer.wrap(bytes);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        return buf.getShort();
    }

    public static int getBandValue(RadioKey.Band key) {
        byte[] bytes = mBands.get(key);

        if (bytes == null) {
            return -1;
        }

        ByteBuffer buf = ByteBuffer.wrap(bytes);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        return buf.getInt();
    }

    public static int getConstantValue(RadioKey.Constant key) {
        byte[] bytes = mConstants.get(key);

        if (bytes == null) {
            return -1;
        }
        ByteBuffer buf = ByteBuffer.wrap(bytes);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        return buf.getInt();

    }

    public static RadioCommand getRadioCommand(RadioKey.Command key) {
        return mCommands.get(key);
    }

}
