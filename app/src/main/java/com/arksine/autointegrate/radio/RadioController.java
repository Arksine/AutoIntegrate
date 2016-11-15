package com.arksine.autointegrate.radio;

import android.os.RemoteException;
import android.util.ArrayMap;
import android.util.Log;

import com.arksine.autointegrate.MainService;
import com.arksine.autointegrate.utilities.UtilityFunctions;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;


/**
 * Handles parsing of Radio Packet Data recieved from the radio, as well as building packets
 * to be sent back to the radio.
 */

public class RadioController {

    private static final String TAG = "RadioController";
    private static final boolean DEBUG = true;

    private final Object LOCK = new Object();

    private MainService mService = null;
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
    private final ArrayMap<String, Object> mHdValues = new ArrayMap<>(26);


    public RadioController(MainService service) {
        mService = service;
        initHdValues();
    }

    private void initHdValues() {
        mHdValues.put("power", false);
        mHdValues.put("mute", false);
        mHdValues.put("signal_strength", 0);
        mHdValues.put("tune", new TuneInfo());
        mHdValues.put("seek", 0);
        mHdValues.put("hd_active", false);
        mHdValues.put("hd_stream_lock", false);
        mHdValues.put("hd_signal_strength", 0);
        mHdValues.put("hd_sub_channel", 0);
        mHdValues.put("hd_sub_channel_count", 0);
        mHdValues.put("hd_enable_hd_tuner", false);
        mHdValues.put("hd_title", new HDSongInfo());
        mHdValues.put("hd_artist", new HDSongInfo());
        mHdValues.put("hd_callsign", "");
        mHdValues.put("hd_station_name", "");
        mHdValues.put("hd_unique_id", "");
        mHdValues.put("hd_api_version", "");
        mHdValues.put("hd_hw_version", "");
        mHdValues.put("rds_enable", false);
        mHdValues.put("rds_genre", "");
        mHdValues.put("rds_program_service", "");
        mHdValues.put("rds_radio_text", "");
        mHdValues.put("volume", 0);
        mHdValues.put("bass", 0);
        mHdValues.put("treble", 0);
        mHdValues.put("compression", 0);
    }

    public Object getHdValue(String key) {
        synchronized (LOCK) {
            return mHdValues.get(key);
        }
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
        int callbackCount = 0;

        switch (dataType) {
            case RadioCommand.Type.INT:
                int intVal = msgBuf.getInt();

                if (DEBUG) Log.i(TAG, radioCmd + " value: " + intVal);

                setHdValue(radioCmd, intVal);

                callbackCount = mService.mRadioCallbacks.beginBroadcast();
                for (int i = 0; i < callbackCount; i++) {
                    try {
                        mService.mRadioCallbacks.getBroadcastItem(i)
                                .OnIntegerValueReceived(radioCmd, intVal);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }

                break;
            case RadioCommand.Type.BOOLEAN:
                int boolVal = msgBuf.getInt();
                boolean status;
                if (boolVal == 1) {
                    status = true;
                } else if (boolVal == 0) {
                    status = false;
                } else {
                    Log.i(TAG, "Invalid boolean value: " + boolVal);
                    return;
                }
                setHdValue(radioCmd, status);
                callbackCount = mService.mRadioCallbacks.beginBroadcast();
                for (int i = 0; i < callbackCount; i++) {
                    try {
                        mService.mRadioCallbacks.getBroadcastItem(i)
                                .OnBooleanValueReceived(radioCmd, status);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
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

                callbackCount = mService.mRadioCallbacks.beginBroadcast();
                for (int i = 0; i < callbackCount; i++) {
                    try {
                        mService.mRadioCallbacks.getBroadcastItem(i)
                                .OnStringValueReceived(radioCmd, msg);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }
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

                callbackCount = mService.mRadioCallbacks.beginBroadcast();
                for (int i = 0; i < callbackCount; i++) {
                    try {
                        mService.mRadioCallbacks.getBroadcastItem(i)
                                .OnTuneReply(radioCmd, tuneInfo.band, tuneInfo.frequency);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }
                break;
            case RadioCommand.Type.HDSONGINFO:
                HDSongInfo songInfo = new HDSongInfo();
                songInfo.subchannel = msgBuf.getInt();

                if (songInfo.subchannel == -1) {
                    return;
                }

                // TODO: do I need to get an empty integer here?

                stringBytes = new byte[msgBuf.remaining()];
                msgBuf.get(stringBytes);
                songInfo.description = new String(stringBytes);

                setHdValue(radioCmd, songInfo);

                Object subChannel = mHdValues.get("hd_sub_channel");
                if (subChannel == null || (int)subChannel == 0){
                    setHdValue("hd_sub_channel", songInfo.subchannel);
                }

                callbackCount = mService.mRadioCallbacks.beginBroadcast();
                for (int i = 0; i < callbackCount; i++) {
                    try {
                        mService.mRadioCallbacks.getBroadcastItem(i)
                                .OnHdSongInfoReceived(radioCmd, songInfo.description, songInfo.subchannel);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }
                break;
            case RadioCommand.Type.NONE:
                // This is seek, not sure we should do anything here
                break;
            default:
                Log.w(TAG, "Unknown type parsed");
        }
    }

    // TODO: This function should actually be implemented in the calling activity.  That activity
    //       Should be responsible for tracking data, doing it in the service means we need  to
    //       pass a map of values every time a callback is executed.  There are a few variables
    //       I need to track here (power, stream lock)
    private void setHdValue(String key, Object value) {
        synchronized (LOCK) {
            RadioCommand cmd = HDRadioDefs.getRadioCommand(key);
            if (cmd != null) {
                switch (key) {
                    case "hd_sub_channel":
                        int index = (int) value;
                        mHdValues.put("hd_title", mHdTitles.get(index));
                        mHdValues.put("hd_artist", mHdArtists.get(index));
                        break;
                    case "tune":
                        // reset values when we tune to a new channel
                        mHdValues.put("hd_sub_channel", 0);
                        mHdValues.put("hd_sub_channel_count", 0);
                        mHdValues.put("hd_active", false);
                        mHdValues.put("hd_stream_lock", false);
                        mHdValues.put("rds_enable", false);
                        mHdValues.put("rds_genre", "");
                        mHdValues.put("rds_program_service", "");
                        mHdValues.put("rds_radio_text", "");
                        mHdValues.put("hd_callsign", "");
                        mHdValues.put("hd_station_name", "");
                        mHdValues.put("hd_title", "");
                        mHdValues.put("hd_artist", "");
                        mHdArtists.clear();
                        mHdTitles.clear();
                        break;
                    case "hd_title":
                        HDSongInfo val = (HDSongInfo) value;
                        mHdTitles.put(val.subchannel, val.description);
                        break;
                    case "hd_artist":
                        HDSongInfo val2 = (HDSongInfo) value;
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
                }

                mHdValues.put(key, value);


            } else {
                Log.i(TAG, "Error setting HD value");
            }
        }
    }

    public void setSeekAll(boolean status) {
        mSeekAll = status;
    }

    public boolean getSeekAll() {
        return mSeekAll;
    }

    // TODO: change the extra from string back to object, as with IPC we have a better idea of
    //       what object is coming
    public byte[] buildRadioPacket(String command, String op, Object data) {

        byte[] dataPacket;
        byte lengthByte;
        byte checkByte;

        switch (op) {
            case "get":
                dataPacket = buildGetDataPacket(command);
                break;
            case "set":
                dataPacket = buildSetDataPacket(command, data);
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

    private byte[] buildSetDataPacket(String command, Object data) {
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
                if (!(data instanceof Boolean)) {
                    Log.i(TAG, "Invalid boolean data received for command: " + command);
                    return null;
                }

                boolean statusOn = (boolean) data;
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
                if (!(data instanceof Integer)) {
                    Log.i(TAG, "Invalid integer data received for command: " + command);
                    return null;
                }

                int val = (int) data;

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
                if (data instanceof String) {
                    String direction = (String)data;
                    if (!direction.equals("up") || !direction.equals("down")) {
                        Log.i(TAG, "Direction is not valid for tune command");
                        return null;
                    }
                    dataBuf.put(HDRadioDefs.getConstantBytes("zero"));     // pad with 8 zero bytes
                    dataBuf.put(HDRadioDefs.getConstantBytes("zero"));
                    dataBuf.put(HDRadioDefs.getConstantBytes(direction));
                }
                else if (data instanceof TuneInfo) {
                    TuneInfo info = (TuneInfo)data;
                    byte[] bandBytes = HDRadioDefs.getBandBytes(info.band.toLowerCase());
                    if (bandBytes == null) {
                        Log.i(TAG, "Band is not valid for tune command");
                        return null;
                    }
                    dataBuf.put(bandBytes);
                    dataBuf.putInt(info.frequency);

                } else {
                    // The data is incorrect
                    Log.i(TAG, "Extra data is not valid for tune command");
                    return null;
                }

                break;
            case "seek":
                if (!(data instanceof String)) {
                    Log.i(TAG, "Invalid String data received for command: " + command);
                    return null;
                }
                String direction = (String)data;
                if (!direction.equals("up") || !direction.equals("down")) {
                    Log.i(TAG, "Direction is not valid for tune command");
                    return null;
                }
                dataBuf.put(HDRadioDefs.getConstantBytes("seek_id"));
                dataBuf.put(HDRadioDefs.getConstantBytes("zero"));
                dataBuf.put(HDRadioDefs.getConstantBytes(direction));

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
