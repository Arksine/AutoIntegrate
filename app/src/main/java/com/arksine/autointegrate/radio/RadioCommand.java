package com.arksine.autointegrate.radio;

/**
 * Contains data relating to each command sent to and received from the HD Radio
 */

public class RadioCommand {

    public static class Type {
        public static final int INT = 0;
        public static final int BOOLEAN = 1;
        public static final int STRING = 2;
        public static final int TUNEINFO = 3;
        public static final int HDSONGINFO = 4;
        public static final int NONE = 5;
    }

    private final String mCommandName;
    private final byte[] mCommandBytes;
    private final int mDataType;

    RadioCommand(String name, int type, byte[] bytes) {
        mCommandName = name;
        mDataType = type;
        mCommandBytes = bytes;
    }

    public String getCommandName() {
        return mCommandName;
    }

    public byte[] getCommandBytes() {
        return mCommandBytes;
    }

    public int getDataType() {
        return mDataType;
    }


}
