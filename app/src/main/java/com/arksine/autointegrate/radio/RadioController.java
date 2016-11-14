package com.arksine.autointegrate.radio;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.util.ArrayMap;
import android.util.Log;

import com.arksine.autointegrate.R;
import com.arksine.autointegrate.utilities.UtilityFunctions;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Calendar;

/**
 * Handles parsing of Radio Packet Data recieved from the radio, as well as building packets
 * to be sent back to the radio.
 */

public class RadioController {

    interface RadioDataCallback {
        void OnRadioDataReceived(String command);
    }
    private RadioDataCallback radioDataCallback = null;

    private static final String TAG = "RadioController";
    private static final boolean DEBUG = true;
    private static final int REFRESH_DELAY = 500;         // milliseconds
    private static final int STREAM_LOCK_TIMEOUT = 10;    // seconds

    private Context mContext = null;
    private boolean mSeekAll = false;
    private long mLastTimeRefreshed = 0;

    static class TuneInfo {
        public String band = "FM";
        public int frequency = 0;
    }

    // the below is used for both hdartist and hd song responses
    static class HDSongInfo {
        public int subchannel = 0;
        public String description = "";
    }

    private ArrayMap<Integer, String> mHdTitles = new ArrayMap<>(5);
    private ArrayMap<Integer, String> mHdArtists = new ArrayMap<>(5);


    public RadioController(Context context) {
        mContext = context;
    }

    public void setRadioDataCallback(RadioDataCallback cb) {
        radioDataCallback = cb;
    }

    public void parseDataPacket(byte[] data) {
        /**
         * TODO: Here is what we know about the data packet
         * -Bytes 0 and 1 are the message command(IE: tune, power, etc)
         * -Bytes 2 and 3 are the message operation (get, set, reply)
         * -We are really only interested in replies, get and set I assume are just confirmations,
         * or are trying to set a mode in the HD Radio's Control device
         */

        if (DEBUG) Log.v(TAG, "Data packet hex:\n" + UtilityFunctions.bytesToHex(data));

        ByteBuffer msgBuf = ByteBuffer.wrap(data);
        msgBuf.order(ByteOrder.LITTLE_ENDIAN);
        int messageCmd = msgBuf.getShort();
        int messageOp =  msgBuf.getShort();

        if (messageOp != HDRadioDefs.getOpValue("reply")) {
            Log.d(TAG, "Message is not a reply, discarding");
            return;
        }

        RadioCommand command = HDRadioDefs.getCommandFromValue(messageCmd);

        if (command == null) {
            Log.w(TAG, "Command not found in ArrayMap");
            return;
        }

        String radioCmd = command.getCommandName();
        int dataType = command.getDataType();
        byte[] stringBytes;

        switch (dataType) {
            case RadioCommand.Type.INT:
                int intVal = msgBuf.getInt();

                if (DEBUG) Log.i(TAG, radioCmd + " value: " + intVal);

                setHdValue(radioCmd, intVal);

                break;
            case RadioCommand.Type.BOOLEAN:
                int boolVal = msgBuf.getInt();

                if (boolVal == 1) {
                    setHdValue(radioCmd, true);
                } else if (boolVal == 0) {
                    setHdValue(radioCmd, false);
                } else {
                    Log.i(TAG, "Invalid boolean value: " + boolVal);
                }
                break;

            case RadioCommand.Type.STRING:
                // String are padded with four initial bytes
                int nullBuf = msgBuf.getInt();   // this value is ignored in HDRC, but it may mean something
                stringBytes = new byte[msgBuf.remaining()];
                msgBuf.get(stringBytes);
                String msg = new String(stringBytes);
                if (DEBUG) Log.d(TAG, "Null Portion: " + nullBuf +"\nConverted String: \n" + msg);

                setHdValue(radioCmd, msg);
                break;

            case RadioCommand.Type.TUNEINFO:
                TuneInfo tuneInfo = new TuneInfo();
                int band = msgBuf.getInt();
                if (band == 0) {
                    tuneInfo.band = "AM";
                } else if (band == 1) {
                    tuneInfo.band = "FM";
                } else {
                    Log.wtf(TAG, "Something went wrong setting the band");
                    return;
                }
                tuneInfo.frequency = msgBuf.getInt();
                setHdValue(radioCmd, tuneInfo);
                break;
            case RadioCommand.Type.HDSONGINFO:
                HDSongInfo songInfo = new HDSongInfo();
                songInfo.subchannel = msgBuf.getInt();

                if (songInfo.subchannel == -1) {
                    return;
                }

                stringBytes = new byte[msgBuf.remaining()];
                msgBuf.get(stringBytes);
                songInfo.description = new String(stringBytes);

                setHdValue(radioCmd, songInfo);

                if (HDRadioDefs.getCommandData("hd_sub_channel") == null) {
                    setHdValue("hd_sub_channel", songInfo.subchannel);
                }
                break;
            case RadioCommand.Type.NONE:
                // This is seek, not sure we should do anything here
                break;
            default:
                Log.w(TAG, "Unknown type parsed");
        }
    }

    private void setHdValue(String key, Object value) {
        boolean refresh = true;
        RadioCommand cmd = HDRadioDefs.getRadioCommand(key);
        if (cmd != null) {
            switch (key) {
                case "hd_sub_channel":
                    int index = (int) value;
                    HDRadioDefs.setCommandData("hd_title", mHdTitles.get(index));
                    HDRadioDefs.setCommandData("hd_artist", mHdArtists.get(index));
                    break;
                case "tune":
                    // reset values when we tune to a new channel
                    HDRadioDefs.setCommandData("hd_sub_channel", 0);
                    HDRadioDefs.setCommandData("hd_sub_channel_count", 0);
                    HDRadioDefs.setCommandData("hd_active", false);
                    HDRadioDefs.setCommandData("hd_stream_lock", false);
                    HDRadioDefs.setCommandData("rds_enable", false);
                    HDRadioDefs.setCommandData("rds_genre", "");
                    HDRadioDefs.setCommandData("rds_program_service", "");
                    HDRadioDefs.setCommandData("rds_radio_text", "");
                    HDRadioDefs.setCommandData("hd_callsign", "");
                    HDRadioDefs.setCommandData("hd_station_name", "");
                    HDRadioDefs.setCommandData("hd_title", "");
                    HDRadioDefs.setCommandData("hd_artist", "");
                    mHdArtists.clear();
                    mHdTitles.clear();
                    break;
                case "hd_title":
                    HDSongInfo val = (HDSongInfo)value;
                    mHdTitles.put(val.subchannel, val.description);
                    break;
                case "hd_artist":
                    HDSongInfo val2 = (HDSongInfo)value;
                    mHdArtists.put(val2.subchannel, val2.description);
                    break;
                case "power":
                    break;
                case "mute":
                    break;
                case "volume":
                    break;
                case "bass":
                    break;
                case "treble":
                    break;
                case "compression":
                    break;
                default:
                    refresh = false;
            }

            cmd.setData(value);

            // TODO: This handles IPC for activities within the package until AIDL is implemented
            if (radioDataCallback != null) {
                radioDataCallback.OnRadioDataReceived(key);
            }

        } else {
            Log.i(TAG, "Error setting HD value");
        }
    }

    public void setSeekAll(boolean status) {
        mSeekAll = status;
    }

    public boolean getSeekAll() {
        return mSeekAll;
    }

    public byte[] buildRadioPacket(String command, String op, String extra) {

        byte[] dataPacket;
        byte lengthByte;
        byte checkByte;

        switch (op) {
            case "get":
                dataPacket = buildGetDataPacket(command);
                break;
            case "set":
                dataPacket = buildSetDataPacket(command, extra);
                break;
            default:
                Log.i(TAG, "Invalid operation, must be get or set");
                return null;
        }

        if (dataPacket == null) {
            return null;
        }


        ByteArrayOutputStream bufferOut = new ByteArrayOutputStream(dataPacket.length + 3);
        bufferOut.write((byte)0xA4);  // header byte

        // Write the length byte, escape it if necessary
        lengthByte = (byte) (dataPacket.length & 0xFF);
        if (lengthByte == (byte)0x1B) {
            bufferOut.write((byte)0x1B);
            bufferOut.write(lengthByte);
        } else if (lengthByte == (byte)0xA4) {
            bufferOut.write((byte)0x1B);
            bufferOut.write((byte)0x48);
        } else {
            bufferOut.write(lengthByte);
        }

        // write the packet data
        int checksum = 0xA4 + (lengthByte & 0xFF);
        for (byte b : dataPacket) {
            checksum += (b & 0xFF);
            if (b == (byte)0x1B) {
                bufferOut.write((byte)0x1B);
                bufferOut.write(b);
            } else if (b == (byte)0xA4) {
                bufferOut.write((byte)0x1B);
                bufferOut.write((byte)0x48);
            } else {
                bufferOut.write(b);
            }
        }

        // Write the checksum, escape if necessary
        checksum = checksum % 256;
        checkByte = (byte)(checksum & 0xFF);
        if (checkByte == (byte)0x1B) {
            bufferOut.write((byte)0x1B);
            bufferOut.write(checkByte);
        } else if (checkByte == (byte)0xA4) {
            bufferOut.write((byte)0x1B);
            bufferOut.write((byte)0x48);
        } else {
            bufferOut.write(checkByte);
        }

        return bufferOut.toByteArray();
    }

    private byte[] buildGetDataPacket(String command) {
        ByteBuffer dataBuf = ByteBuffer.allocate(4);
        dataBuf.order(ByteOrder.LITTLE_ENDIAN);

        byte[] commandCode = HDRadioDefs.getCommandBytes(command);
        byte[] operationCode = HDRadioDefs.getOpBytes("get");

        if (commandCode == null || operationCode == null) {
            Log.w(TAG, "Invalid command or operation key, cannot build packet");
            return null;
        }

        dataBuf.put(commandCode);
        dataBuf.put(operationCode);
        return dataBuf.array();
    }

    private byte[] buildSetDataPacket(String command, String extra) {
        ByteBuffer dataBuf = ByteBuffer.allocate(256);  // 256 is absolute maximum length of packet
        dataBuf.order(ByteOrder.LITTLE_ENDIAN);

        byte[] commandCode = HDRadioDefs.getCommandBytes(command);
        byte[] operationCode = HDRadioDefs.getOpBytes("set");
        dataBuf.put(commandCode);
        dataBuf.put(operationCode);

        if (commandCode == null || operationCode == null) {
            Log.w(TAG, "Invalid command or operation key, cannot build packet");
            return null;
        }

        switch (command) {
            case "power":           // set boolean item
            case "mute":

                boolean statusOn = Boolean.parseBoolean(extra);
                if (statusOn) {
                    dataBuf.put(HDRadioDefs.getCommandBytes("one"));
                } else {
                    dataBuf.put(HDRadioDefs.getCommandBytes("zero"));
                }
                break;

            case "volume":          // set integer item
            case "bass":
            case "treble":
            case "compression":
            case "hd_sub_channel":
                int val = Integer.parseInt(extra);

                if (DEBUG) Log.i(TAG, "Setting value for " + command + ": " + val);

                // Check to see if integer value is outside of range
                if (val > 90) {
                    val = 90;
                } else if (val < 0) {
                    val = 0;
                }

                dataBuf.putInt(val);
                break;

            case "tune":
                String[] vals = extra.split(":");

                if (vals.length ==  1) {
                    if (!extra.equals("up") || !extra.equals("down")) {
                        Log.i(TAG, "Direction is not valid for tune command");
                        return null;
                    }
                    dataBuf.put(HDRadioDefs.getConstantBytes("zero"));     // pad with 8 zero bytes
                    dataBuf.put(HDRadioDefs.getConstantBytes("zero"));
                    dataBuf.put(HDRadioDefs.getConstantBytes(extra));
                }
                else if (vals.length == 2) {
                    String band = vals[0];
                    int frequency = Integer.parseInt(vals[1]);
                    byte[] bandBytes = HDRadioDefs.getBandBytes(band.toLowerCase());
                    if (bandBytes == null) {
                        Log.i(TAG, "Band is not valid for tune command");
                        return null;
                    }
                    dataBuf.put(bandBytes);
                    dataBuf.putInt(frequency);
                    // TODO: After requesting this packet and sending it to the radio, the
                    //       Radiocomm class should follow up by sending the subchannel if it
                    //       is HD
                } else {
                    // The data is incorrect
                    Log.i(TAG, "Extra data is not valid for tune command");
                    return null;
                }

                break;
            case "seek":
                // should be "up" or "down"
                if (!extra.equals("up") || !extra.equals("down")) {
                    Log.i(TAG, "Direction is not valid for tune command");
                    return null;
                }
                dataBuf.put(HDRadioDefs.getConstantBytes("seek_id"));
                dataBuf.put(HDRadioDefs.getConstantBytes("zero"));
                dataBuf.put(HDRadioDefs.getConstantBytes(extra));

                if (mSeekAll) {
                    dataBuf.put(HDRadioDefs.getConstantBytes("seek_all"));
                } else {
                    dataBuf.put(HDRadioDefs.getConstantBytes("seek_hd_only"));
                }

                dataBuf.put(HDRadioDefs.getConstantBytes("zero"));
                break;
        }

        dataBuf.flip();
        byte[] returnBytes = new byte[dataBuf.limit()];
        dataBuf.get(returnBytes);

        return returnBytes;
    }

}
