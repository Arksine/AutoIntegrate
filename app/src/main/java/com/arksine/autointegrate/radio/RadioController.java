package com.arksine.autointegrate.radio;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.util.Log;
import android.util.SparseArray;

import com.arksine.autointegrate.MainService;
import com.arksine.autointegrate.utilities.DLog;
import com.arksine.autointegrate.utilities.UtilityFunctions;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;


/**
 * Handles parsing of Radio Packet Data recieved from the radio, as well as building packets
 * to be sent back to the radio.
 */

public class RadioController {

    private static final String TAG = "RadioController";

    private final Object WRITE_LOCK = new Object();

    private MainService mService;
    private volatile boolean mSeekAll = false;


    public static class TuneInfo {
        public RadioKey.Band band = RadioKey.Band.FM;
        public int frequency = 0;
    }

    // the below is used for both hdartist and hd song responses
    public static class HDSongInfo {
        public int subchannel = 0;
        public String description = "";
    }

    private SparseArray<String> mHdTitles = new SparseArray<>(5);
    private SparseArray<String> mHdArtists = new SparseArray<>(5);
    private final HashMap<RadioKey.Command, Object> mHdValues = new HashMap<>(26);


    public RadioController(MainService svc) {
        mService = svc;
        initHdValues();
    }


    private void initHdValues() {
        mHdValues.put(RadioKey.Command.POWER, false);
        mHdValues.put(RadioKey.Command.MUTE, false);
        mHdValues.put(RadioKey.Command.SIGNAL_STRENGTH, 0);
        mHdValues.put(RadioKey.Command.TUNE, new TuneInfo());
        mHdValues.put(RadioKey.Command.SEEK, 0);
        mHdValues.put(RadioKey.Command.HD_ACTIVE, false);
        mHdValues.put(RadioKey.Command.HD_STREAM_LOCK, false);
        mHdValues.put(RadioKey.Command.HD_SIGNAL_STRENGH, 0);
        mHdValues.put(RadioKey.Command.HD_SUBCHANNEL, 0);
        mHdValues.put(RadioKey.Command.HD_SUBCHANNEL_COUNT, 0);
        mHdValues.put(RadioKey.Command.HD_TUNER_ENABLED, true);
        mHdValues.put(RadioKey.Command.HD_TITLE, "");
        mHdValues.put(RadioKey.Command.HD_ARTIST, "");
        mHdValues.put(RadioKey.Command.HD_CALLSIGN, "");
        mHdValues.put(RadioKey.Command.HD_STATION_NAME, "");
        mHdValues.put(RadioKey.Command.HD_UNIQUE_ID, "");
        mHdValues.put(RadioKey.Command.HD_API_VERSION, "");
        mHdValues.put(RadioKey.Command.HD_HW_VERSION, "");
        mHdValues.put(RadioKey.Command.RDS_ENABLED, false);
        mHdValues.put(RadioKey.Command.RDS_GENRE, "");
        mHdValues.put(RadioKey.Command.RDS_PROGRAM_SERVICE, "");
        mHdValues.put(RadioKey.Command.RDS_RADIO_TEXT, "");
        mHdValues.put(RadioKey.Command.VOLUME, 0);
        mHdValues.put(RadioKey.Command.BASS, 0);
        mHdValues.put(RadioKey.Command.TREBLE, 0);
        mHdValues.put(RadioKey.Command.COMPRESSION, 0);
    }

    public Object getHdValue(RadioKey.Command key) {
        synchronized (WRITE_LOCK) {
            return mHdValues.get(key);
        }
    }


    public void parseDataPacket(byte[] data) {
        /**
         *  Here is what is known about the data packet
         * -Bytes 0 and 1 are the message command(IE: tune, power, etc)
         * -Bytes 2 and 3 are the message operation (get, set, reply)
         * -We are really only interested in replies, get and set I assume are just confirmations,
         * or are trying to set a mode in the HD Radio's Control device
         */

        DLog.v(TAG, "Data packet hex:\n" + UtilityFunctions.bytesToHex(data));

        ByteBuffer msgBuf = ByteBuffer.wrap(data);
        msgBuf.order(ByteOrder.LITTLE_ENDIAN);
        int messageCmd = msgBuf.getShort();
        int messageOp =  msgBuf.getShort();

        if (messageOp != HDRadioDefs.getOpValue(RadioKey.Operation.REPLY)) {
            Log.i(TAG, "Message is not a reply, discarding");
            return;
        }

        RadioCommand command = HDRadioDefs.getCommandFromValue(messageCmd);
        int intValue;

        if (command == null) {
            Log.w(TAG, "Command not found in ArrayMap");
            return;
        }

        RadioKey.Command radioCmd = command.getCommandKey();
        int dataType = command.getDataType();
        byte[] stringBytes;

        switch (dataType) {
            case RadioCommand.Type.INT:
                intValue = msgBuf.getInt();

                DLog.v(TAG, radioCmd + " value: " + intValue);

                setHdValue(radioCmd, intValue);

                break;
            case RadioCommand.Type.BOOLEAN:
                intValue = msgBuf.getInt();
                boolean status;
                if (intValue == 1) {
                    status = true;
                } else if (intValue == 0) {
                    status = false;
                } else {
                    Log.i(TAG, "Invalid boolean value: " + intValue);
                    return;
                }
                setHdValue(radioCmd, status);

                break;

            case RadioCommand.Type.STRING:
                // Strings are padded with four initial bytes, my guess is that it is the length of the string
                intValue = msgBuf.getInt();

                if (intValue != msgBuf.remaining()) {
                    Log.i(TAG, "String Length received does not match remaining bytes in buffer");
                    intValue = msgBuf.remaining();
                }

                String msg;
                if (intValue == 0) {
                    msg = "";
                } else {
                    stringBytes = new byte[intValue];
                    msgBuf.get(stringBytes);
                    msg = new String(stringBytes);
                }

                DLog.d(TAG, "First byte (length?): " + intValue +"\nConverted String: \n" + msg);
                setHdValue(radioCmd, msg);
                break;

            case RadioCommand.Type.TUNEINFO:
                TuneInfo tuneInfo = new TuneInfo();
                intValue = msgBuf.getInt();
                if (intValue == 0) {
                    tuneInfo.band = RadioKey.Band.AM;
                } else if (intValue == 1) {
                    tuneInfo.band = RadioKey.Band.FM;
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

                intValue = msgBuf.getInt();

                if (intValue != msgBuf.remaining()) {
                    Log.w(TAG, "String Length received does not match remaining bytes in buffer");
                    intValue = msgBuf.remaining();
                }

                if (intValue == 0) {
                    songInfo.description = "";
                } else {
                    stringBytes = new byte[intValue];
                    msgBuf.get(stringBytes);
                    songInfo.description = new String(stringBytes);
                }

                setHdValue(radioCmd, songInfo);

                Object subChannel = mHdValues.get(RadioKey.Command.HD_SUBCHANNEL);
                if (subChannel == null || (int)subChannel == 0){
                    setHdValue(RadioKey.Command.HD_SUBCHANNEL, songInfo.subchannel);
                }

                break;
            case RadioCommand.Type.NONE:

                break;
            default:
                Log.w(TAG, "Unknown type parsed");
        }
    }

    private void setHdValue(RadioKey.Command key, Object value) {
        synchronized (WRITE_LOCK) {
            switch (key) {
                case HD_SUBCHANNEL:
                    int index = (int) value;
                    mHdValues.put(RadioKey.Command.HD_TITLE, mHdTitles.get(index));
                    mHdValues.put(RadioKey.Command.HD_ARTIST, mHdArtists.get(index));
                    break;
                case TUNE:
                    // reset values when we tune to a new channel
                    mHdValues.put(RadioKey.Command.HD_SUBCHANNEL, 0);
                    mHdValues.put(RadioKey.Command.HD_SUBCHANNEL_COUNT, 0);
                    mHdValues.put(RadioKey.Command.HD_ACTIVE, false);
                    mHdValues.put(RadioKey.Command.HD_STREAM_LOCK, false);
                    mHdValues.put(RadioKey.Command.RDS_ENABLED, false);
                    mHdValues.put(RadioKey.Command.RDS_GENRE, "");
                    mHdValues.put(RadioKey.Command.RDS_PROGRAM_SERVICE, "");
                    mHdValues.put(RadioKey.Command.RDS_RADIO_TEXT, "");
                    mHdValues.put(RadioKey.Command.HD_CALLSIGN, "");
                    mHdValues.put(RadioKey.Command.HD_STATION_NAME, "");
                    mHdValues.put(RadioKey.Command.HD_TITLE, "");
                    mHdValues.put(RadioKey.Command.HD_ARTIST, "");
                    mHdArtists.clear();
                    mHdTitles.clear();
                    break;
                case HD_TITLE:
                    HDSongInfo val = (HDSongInfo) value;
                    mHdTitles.put(val.subchannel, val.description);
                    value = val.description;
                    break;
                case HD_ARTIST:
                    HDSongInfo val2 = (HDSongInfo) value;
                    mHdArtists.put(val2.subchannel, val2.description);
                    value = val2.description;
                    break;
                case SEEK:
                    value = ((TuneInfo)value).frequency;  // only send frequency

                default:
            }

            if (DLog.DEBUG) {
                if (value instanceof TuneInfo) {
                    DLog.i(TAG, "Stored " + key.toString() + ": "
                            + ((TuneInfo) value).frequency + " " + ((TuneInfo) value).band.toString());
                } else {
                    DLog.i(TAG, "Stored " + key.toString() + ": " + value);
                }
            }


            mHdValues.put(key, value);


        }

        // Execute callbacks for bound activities
        int cbCount = mService.mRadioCallbacks.beginBroadcast();
        for (int i = 0; i < cbCount; i++) {
            try {
                mService.mRadioCallbacks.getBroadcastItem(i).OnRadioDataReceived(key, value);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
        mService.mRadioCallbacks.finishBroadcast();
    }

    public void setSeekAll(boolean status) {
        mSeekAll = status;
    }

    public boolean getSeekAll() {
        return mSeekAll;
    }

    public byte[] buildRadioPacket(RadioKey.Command command, RadioKey.Operation op, Object data) {

        byte[] dataPacket;
        byte lengthByte;
        byte checkByte;

        switch (op) {
            case GET:
                dataPacket = buildGetDataPacket(command);
                break;
            case SET:
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

        DLog.i(TAG, "Hex Bytes Sent:\n" + UtilityFunctions.bytesToHex(bufferOut.toByteArray()));
        return bufferOut.toByteArray();
    }

    private byte[] buildGetDataPacket(RadioKey.Command key) {
        ByteBuffer dataBuf = ByteBuffer.allocate(4);
        dataBuf.order(ByteOrder.LITTLE_ENDIAN);

        byte[] commandCode = HDRadioDefs.getCommandBytes(key);
        byte[] operationCode = HDRadioDefs.getOpBytes(RadioKey.Operation.GET);

        if (commandCode == null || operationCode == null) {
            Log.w(TAG, "Invalid command or operation key, cannot build packet");
            return null;
        }

        dataBuf.put(commandCode);
        dataBuf.put(operationCode);
        return dataBuf.array();
    }

    private byte[] buildSetDataPacket(RadioKey.Command key, Object data) {
        ByteBuffer dataBuf = ByteBuffer.allocate(256);  // 256 is absolute maximum length of packet
        dataBuf.order(ByteOrder.LITTLE_ENDIAN);

        byte[] commandCode = HDRadioDefs.getCommandBytes(key);
        byte[] operationCode = HDRadioDefs.getOpBytes(RadioKey.Operation.SET);

        if (commandCode == null || operationCode == null) {
            Log.w(TAG, "Invalid command or operation key, cannot build packet");
            return null;
        }

        dataBuf.put(commandCode);
        dataBuf.put(operationCode);

        switch (key) {
            case POWER:           // set boolean item
            case MUTE:
                if (!(data instanceof Boolean)) {
                    Log.i(TAG, "Invalid boolean data received for command: " + key);
                    return null;
                }

                boolean statusOn = (boolean) data;
                if (statusOn) {
                    dataBuf.put(HDRadioDefs.getConstantBytes(RadioKey.Constant.ONE));
                } else {
                    dataBuf.put(HDRadioDefs.getConstantBytes(RadioKey.Constant.ZERO));
                }
                break;

            case VOLUME:          // set integer item
            case BASS:
            case TREBLE:
            case COMPRESSION:
            case HD_SUBCHANNEL:
                if (!(data instanceof Integer)) {
                    Log.i(TAG, "Invalid integer data received for command: " + key);
                    return null;
                }

                int val = (int) data;

                DLog.v(TAG, "Setting value for " + key + ": " + val);

                // Check to see if integer value is outside of range
                if (val > 90) {
                    val = 90;
                } else if (val < 0) {
                    val = 0;
                }

                dataBuf.putInt(val);
                break;

            case TUNE:
                if (data instanceof RadioKey.Constant) {
                    RadioKey.Constant direction = (RadioKey.Constant)data;
                    if (!(direction == RadioKey.Constant.UP || direction == RadioKey.Constant.DOWN)) {
                        Log.i(TAG, "Direction is not valid for tune command");
                        return null;
                    }
                    dataBuf.put(HDRadioDefs.getConstantBytes(RadioKey.Constant.ZERO));     // pad with 8 zero bytes
                    dataBuf.put(HDRadioDefs.getConstantBytes(RadioKey.Constant.ZERO));
                    dataBuf.put(HDRadioDefs.getConstantBytes(direction));
                }
                else if (data instanceof TuneInfo) {
                    TuneInfo info = (TuneInfo)data;
                    byte[] bandBytes = HDRadioDefs.getBandBytes(info.band);
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
            case SEEK:
                if (!(data instanceof RadioKey.Constant)) {
                    Log.i(TAG, "Invalid String data received for command: " + key);
                    return null;
                }

                RadioKey.Constant seekDir = (RadioKey.Constant)data;
                if (!(seekDir == RadioKey.Constant.UP || seekDir == RadioKey.Constant.DOWN)) {
                    Log.i(TAG, "Direction is not valid for tune command");
                    return null;
                }
                dataBuf.put(HDRadioDefs.getConstantBytes(RadioKey.Constant.SEEK_REQ_ID));
                dataBuf.put(HDRadioDefs.getConstantBytes(RadioKey.Constant.ZERO));
                dataBuf.put(HDRadioDefs.getConstantBytes(seekDir));

                if (mSeekAll) {
                    dataBuf.put(HDRadioDefs.getConstantBytes(RadioKey.Constant.SEEK_ALL_ID));
                } else {
                    dataBuf.put(HDRadioDefs.getConstantBytes(RadioKey.Constant.SEEK_HD_ONLY_ID));
                }

                dataBuf.put(HDRadioDefs.getConstantBytes(RadioKey.Constant.ZERO));
                break;
        }

        dataBuf.flip();
        byte[] returnBytes = new byte[dataBuf.limit()];
        dataBuf.get(returnBytes);

        return returnBytes;
    }

    public void close(Context context) {
        // Execute callbacks for bound activities
        int cbCount = mService.mRadioCallbacks.beginBroadcast();
        for (int i = 0; i < cbCount; i++) {
            try {
                mService.mRadioCallbacks.getBroadcastItem(i).OnClose();
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
        mService.mRadioCallbacks.finishBroadcast();

        // Persist changeable values
        SharedPreferences globalPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        TuneInfo tuneInfo = (TuneInfo)mHdValues.get(RadioKey.Command.TUNE);
        boolean hdActive = (boolean)mHdValues.get(RadioKey.Command.HD_ACTIVE);
        int subchannel = (hdActive && tuneInfo.band == RadioKey.Band.FM) ?
                (int)mHdValues.get(RadioKey.Command.HD_SUBCHANNEL) : 0;
        int volume = (int)mHdValues.get(RadioKey.Command.VOLUME);
        int bass = (int)mHdValues.get(RadioKey.Command.BASS);
        int treble = (int)mHdValues.get(RadioKey.Command.TREBLE);

        globalPrefs.edit()
                .putInt("radio_pref_key_frequency", tuneInfo.frequency)
                .putString("radio_pref_key_band", tuneInfo.band.toString())
                .putInt("radio_pref_key_subchannel", subchannel)
                .putInt("radio_pref_key_volume", volume)
                .putInt("radio_pref_key_bass", bass)
                .putInt("radio_pref_key_treble", treble)
                .apply();
    }
}
