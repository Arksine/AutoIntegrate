package com.arksine.autointegrate.microcontroller;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.arksine.autointegrate.R;
import com.arksine.autointegrate.interfaces.MCUControlInterface;
import com.arksine.autointegrate.utilities.DLog;
import com.arksine.hdradiolib.HDSongInfo;
import com.arksine.hdradiolib.TuneInfo;
import com.arksine.hdradiolib.enums.RadioBand;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Handler to parse incoming packets from the Micro Controller and process its events
 */

public class ControllerInputHandler extends Handler {

    private static final String TAG = "ControllerInputHandler";

    private Context mContext = null;
    private CommandProcessor mCommandProcessor;

    private interface MessageProcessor {
        void ProcessMessage(ControllerMessage ctrlMsg);
    }
    private MessageProcessor mProcessor;

    private ByteBuffer mReceivedBuffer = ByteBuffer.allocate(512);
    private boolean mIsLengthByte = false;
    private boolean mIsEscapedByte = false;
    private boolean mIsValidPacket = false;
    private int mPacketLength = 0;
    private int mChecksum = 0;

    // TODO: rename "learning mode" to direct access mode if using a remote callback
    ControllerInputHandler(Looper looper, Context context, MCUControlInterface controlInterface,
                           boolean isLearningMode) {
        super(looper);
        mContext = context;
        mReceivedBuffer.order(ByteOrder.LITTLE_ENDIAN);

        if (isLearningMode) {
            DLog.v(TAG, "Controller is in Learning Mode.");
            mCommandProcessor = null;
            mProcessor = new MessageProcessor() {
                @Override
                public void ProcessMessage(ControllerMessage ctrlMsg) {
                    // TODO: Instead of broadcasting an intent I can use a RemoteCallback to send data back to bound activity
                    // Local broadcast to learning activity, we only learn click and dimmer events
                    if(ctrlMsg.command == MCUDefs.MCUCommand.CLICK || ctrlMsg.command == MCUDefs.MCUCommand.DIMMER) {
                        Intent msgIntent = new Intent(mContext.getString(R.string.ACTION_CONTROLLER_LEARN_DATA));
                        msgIntent.putExtra("Command", ctrlMsg.command.toString());
                        if (ctrlMsg.msgType == MCUDefs.DataType.INT) {
                            msgIntent.putExtra("Data", String.valueOf((int)ctrlMsg.data));
                        } else if (ctrlMsg.msgType == MCUDefs.DataType.BOOLEAN){
                            String bData =(boolean) ctrlMsg.data ? "On" : "Off";
                            msgIntent.putExtra("Data", bData);
                        } else {
                            // incorrect data type
                            Log.i(TAG, "Incorrect data type for calibration received");
                            return;
                        }

                        LocalBroadcastManager.getInstance(mContext).sendBroadcast(msgIntent);
                    }
                }
            };
        } else {
            DLog.v(TAG, "Controller is in Execution Mode.");
            mCommandProcessor = new CommandProcessor(mContext, controlInterface);
            mProcessor = new MessageProcessor() {
                @Override
                public void ProcessMessage(ControllerMessage ctrlMsg) {
                    mCommandProcessor.executeAction(ctrlMsg);
                }
            };
        }
    }

    @Override
    public void handleMessage(Message msg) {
        parseIncomingBytes((byte[])msg.obj);
    }

    private void parseIncomingBytes(byte[] data) {
        // TODO: I should include the header in the checksum calculation (need to do it in sketch as well)
        // add the incoming bytes to a buffer
        for (byte ch : data) {
            if (ch == (byte) 0xF1) {
                mIsValidPacket = true;
                mIsEscapedByte = false;
                mIsLengthByte = true;

                mReceivedBuffer.clear();
                mPacketLength = 0;
                mChecksum = 0xF1;
            } else if (!mIsValidPacket) {
                DLog.i(TAG, "Invalid byte received: " + ch);
            } else if (ch == (byte)0x1B && !mIsEscapedByte) {
                mIsEscapedByte = true;
            } else {
                if (mIsEscapedByte) {
                    mIsEscapedByte = false;
                    if (ch == (byte)0x20) {
                        // 0xF1 is escaped as 0x20
                        ch = (byte) 0xF1;
                    }
                    // Note: 0x1B is escaped as 0x1B, so we don't need to reset the current byte
                }

                if (mIsLengthByte) {
                    mIsLengthByte = false;
                    mPacketLength = ch & (0xFF);
                    mChecksum += mPacketLength;
                } else if (mReceivedBuffer.position() == mPacketLength) {
                    // This is the checksum byte

                    // Checksum is all bytes added up (not counting header and escape bytes) mod 256
                    if ((mChecksum % 256) == (ch & 0xFF)) {
                        mReceivedBuffer.flip();
                        parsePacket();

                    } else {
                        Log.i(TAG, "Invalid checksum, discarding packet");
                    }

                    // The next byte received must be 0xF1, regardless of what happened here
                    mIsValidPacket = false;
                } else {
                    // Add byte to packet buffer
                    mReceivedBuffer.put(ch);
                    mChecksum += (ch & 0xFF);
                }
            }
        }
    }

    // Reads a message from the Micro Controller and parses it
    private boolean parsePacket() {

        DLog.i(TAG, "MCU Packet Length: " + mReceivedBuffer.limit());

        // TODO: May want type NONE to be possible, and just receive a command without accompanying data.
        // In that case, only 2 bytes in the packet is possible, data type of none is possible.


        if(mReceivedBuffer.limit() < 3) {
            Log.e(TAG, "Invalid data packet, must at least be three bytes long");
            return false;
        }

        ControllerMessage ctrlMsg = new ControllerMessage();

        ctrlMsg.command = MCUDefs.MCUCommand.getMcuCommand(mReceivedBuffer.get());
        DLog.v(TAG, "MCU Command Recd: " + ctrlMsg.command.toString());
        if (ctrlMsg.command == MCUDefs.MCUCommand.NONE) {
            Log.e(TAG, "Invalid Command Received");
            return false;
        }

        ctrlMsg.msgType = MCUDefs.DataType.getDataType(mReceivedBuffer.get());
        DLog.v(TAG, "Data Type Recd: " + ctrlMsg.msgType.toString());
        if (ctrlMsg.msgType == MCUDefs.DataType.NONE) {
            Log.e(TAG, "Invalid Data Type Received");
            return false;
        }


        // if a radio command is received, we need
        if (ctrlMsg.command == MCUDefs.MCUCommand.RADIO) {
            // get radio command
            ctrlMsg.radioCmd = MCUDefs.RadioCommand.getRadioCommand(mReceivedBuffer.get());

            if (ctrlMsg.radioCmd == MCUDefs.RadioCommand.NONE) {
                Log.e(TAG, "Invalid Radio Command Received");
                return false;
            }

        } else {
            ctrlMsg.radioCmd = MCUDefs.RadioCommand.NONE;
        }


        if (!mReceivedBuffer.hasRemaining()) {
            Log.i(TAG, "Invalid Packet, end of buffer reached before data received");
            return false;
        }



        // TODO: Since the dimmer type can be string or Int, it might be best to just receive
        //       integers in string format and parse them later
        switch (ctrlMsg.msgType) {
            case SHORT:
                if (mReceivedBuffer.remaining() < 2) {
                    Log.i(TAG, "Invalid Short data size: " + mReceivedBuffer.remaining());
                    return false;
                }

                // since we are dealing with ints throughout we will cast it to int
                ctrlMsg.data = (int) mReceivedBuffer.getShort();

                break;
            case INT:
                if (mReceivedBuffer.remaining() == 2) {
                    // 8-bit MCU integer is two bytes
                    ctrlMsg.data = mReceivedBuffer.getShort();
                    DLog.v(TAG, "8-bit MCU Integer received");
                } else if (mReceivedBuffer.remaining() >= 4) {
                    // 32-bit AVR integer is 4 bytes
                    ctrlMsg.data = mReceivedBuffer.getInt();
                    DLog.v(TAG, "32-bit MCU Integer received");
                } else {
                    Log.i(TAG, "Invalid Integer data size: " + mReceivedBuffer.remaining());
                    return false;
                }

                break;
            case STRING:
                byte[] strBytes = new byte[mReceivedBuffer.remaining()];
                mReceivedBuffer.get(strBytes);
                ctrlMsg.data = new String(strBytes);
                break;

            case BOOLEAN:
                ctrlMsg.data = mReceivedBuffer.get()!= 0;
                break;
            case TUNE_INFO:
                // TODO: frequency could be a short, as the frequency can not be above 1080. That
                // would make the rest of the packet 3 bytes long (1 for band, 2 for frequency)

                if (mReceivedBuffer.remaining() < 5) {
                    Log.i(TAG, "Invalid Tune Info data size: " + mReceivedBuffer.remaining());
                    return false;
                }

                RadioBand band;
                byte bnd = mReceivedBuffer.get();
                if (bnd == 0) {
                    band = RadioBand.AM;
                } else if (bnd == 1) {
                    band = RadioBand.FM;
                } else {
                    Log.wtf(TAG, "Band byte received is invalid");
                    return false;
                }
                int frequency = mReceivedBuffer.getInt();

                ctrlMsg.data = new TuneInfo(band, frequency, 0);
                break;
            case HD_SONG_INFO:
                // TODO: this could be a byte, as the subchannel cannot be bigger than 10.  That would
                // make the minimum remaining bytes 2, assuming the string is only 1 byte long

                if (mReceivedBuffer.remaining() < 5) {
                    Log.i(TAG, "Invalid Song Info data size: " + mReceivedBuffer.remaining());
                    return false;
                }

                int subchannel = mReceivedBuffer.getInt();
                byte[] songBytes = new byte[mReceivedBuffer.remaining()];
                mReceivedBuffer.get(songBytes);
                String info = new String(songBytes);

                ctrlMsg.data = new HDSongInfo(info, subchannel);
                break;
            default:
                Log.e(TAG, "Invalid Data Type Received");
                return false;
        }

        if (mReceivedBuffer.hasRemaining()) {
            Log.w(TAG, "There is data remaining in the buffer, despite structured parsing");

        }


        // send the parsed message for processing
        if (ctrlMsg.command == MCUDefs.MCUCommand.LOG) {
            Log.i("Micro Controller", (String) ctrlMsg.data);

        } else {
            DLog.v(TAG, ctrlMsg.command + " " + ctrlMsg.data);
            mProcessor.ProcessMessage(ctrlMsg);
        }

        return true;
    }

    public void setMode(boolean isLearningMode) {
        // TODO:
    }


    public void close() {
        // if we are in execution mode we need to clean up the command processor
        if (mCommandProcessor != null) {
            mCommandProcessor.close();
        }
    }
}
