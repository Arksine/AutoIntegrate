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
import com.arksine.autointegrate.radio.RadioController;
import com.arksine.autointegrate.radio.RadioKey;
import com.arksine.autointegrate.radio.TextStreamAnimator;
import com.arksine.autointegrate.utilities.DLog;

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

    private ByteBuffer mMessageBuf;

    // TODO: rename "learning mode" to direct access mode if using a remote callback
    ControllerInputHandler(Looper looper, Context context, MCUControlInterface controlInterface,
                           boolean isLearningMode) {
        super(looper);
        mContext = context;

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
        mMessageBuf = ByteBuffer.wrap((byte[])msg.obj);
        mMessageBuf.order(ByteOrder.LITTLE_ENDIAN);
        ControllerMessage ctrlMsg =  parseMessage();

        if (ctrlMsg != null) {
            if (ctrlMsg.command == MCUDefs.MCUCommand.LOG) {
                Log.i("Micro Controller", (String) ctrlMsg.data);

            } else {
                DLog.v(TAG, ctrlMsg.command + " " + ctrlMsg.data);
                mProcessor.ProcessMessage(ctrlMsg);
            }
        }
    }

    // Reads a message from the Micro Controller and parses it
    private ControllerMessage parseMessage() {
        /**
         * TODO: My parsing wont work with binary data.  If my control chars are 0x02 and 0x03 then any byte (such as a control byte or part of an integer) that
         * contains those chars will get parsed incorrectly.
         */

        DLog.i(TAG, "MCU Packet Length: " + mMessageBuf.limit());

        // TODO: May want type NONE to be possible, and just receive a command without accompanying data.
        // In that case, only 2 bytes in the packet is possible, data type of none is possible.


        if(mMessageBuf.limit() < 3) {
            Log.e(TAG, "Invalid data packet, must at least be three bytes long");
            return null;
        }

        ControllerMessage ctrlMsg = new ControllerMessage();

        ctrlMsg.command = MCUDefs.MCUCommand.getMcuCommand(mMessageBuf.get());
        DLog.v(TAG, "MCU Command Recd: " + ctrlMsg.command.toString());
        if (ctrlMsg.command == MCUDefs.MCUCommand.NONE) {
            Log.e(TAG, "Invalid Command Received");
            return null;
        }

        ctrlMsg.msgType = MCUDefs.DataType.getDataType(mMessageBuf.get());
        DLog.v(TAG, "Data Type Recd: " + ctrlMsg.msgType.toString());
        if (ctrlMsg.msgType == MCUDefs.DataType.NONE) {
            Log.e(TAG, "Invalid Data Type Received");
            return null;
        }


        // if a radio command is received, we need
        if (ctrlMsg.command == MCUDefs.MCUCommand.RADIO) {
            // get radio command
            ctrlMsg.radioCmd = MCUDefs.RadioCommand.getRadioCommand(mMessageBuf.get());

            if (ctrlMsg.radioCmd == MCUDefs.RadioCommand.NONE) {
                Log.e(TAG, "Invalid Radio Command Received");
                return null;
            }

        } else {
            ctrlMsg.radioCmd = MCUDefs.RadioCommand.NONE;
        }


        if (!mMessageBuf.hasRemaining()) {
            Log.i(TAG, "Invalid Packet, end of buffer reached before data received");
            return null;
        }

        // TODO: Since the dimmer type can be string or Int, it might be best to just receive
        //       integers in string format and parse them later
        switch (ctrlMsg.msgType) {
            case SHORT:
                if (mMessageBuf.remaining() < 2) {
                    Log.i(TAG, "Invalid Short data size: " + mMessageBuf.remaining());
                    return null;
                }

                // since we are dealing with ints throughout we will cast it to int
                ctrlMsg.data = (int) mMessageBuf.getShort();

                break;
            case INT:
                if (mMessageBuf.remaining() < 4) {
                    Log.i(TAG, "Invalid Integer data size: " + mMessageBuf.remaining());
                    return null;
                }
                ctrlMsg.data = mMessageBuf.getInt();

                break;
            case STRING:
                byte[] strBytes = new byte[mMessageBuf.remaining()];
                mMessageBuf.get(strBytes);
                ctrlMsg.data = new String(strBytes);
                break;

            case BOOLEAN:
                ctrlMsg.data = mMessageBuf.get()!= 0;
                break;
            case TUNE_INFO:
                // TODO: frequency could be a short, as the frequency can not be above 1080. That
                // would make the rest of the packet 3 bytes long (1 for band, 2 for frequency)

                if (mMessageBuf.remaining() < 5) {
                    Log.i(TAG, "Invalid Tune Info data size: " + mMessageBuf.remaining());
                    return null;
                }

                RadioController.TuneInfo tuneInfo = new RadioController.TuneInfo();
                tuneInfo.subchannel = 0;
                byte bnd = mMessageBuf.get();
                if (bnd == 0) {
                    tuneInfo.band = RadioKey.Band.AM;
                } else if (bnd == 1) {
                    tuneInfo.band = RadioKey.Band.FM;
                } else {
                    Log.wtf(TAG, "Band byte received is invalid");
                    return null;
                }


                tuneInfo.frequency = mMessageBuf.getInt();
                ctrlMsg.data = tuneInfo;
                break;
            case HD_SONG_INFO:
                // TODO: this could be a byte, as the subchannel cannot be bigger than 10.  That would
                // make the minimum remaining bytes 2, assuming the string is only 1 byte long

                if (mMessageBuf.remaining() < 5) {
                    Log.i(TAG, "Invalid Song Info data size: " + mMessageBuf.remaining());
                    return null;
                }
                RadioController.HDSongInfo songInfo = new RadioController.HDSongInfo();


                songInfo.subchannel = mMessageBuf.getInt();

                byte[] songBytes = new byte[mMessageBuf.remaining()];
                mMessageBuf.get(songBytes);
                songInfo.description = new String(songBytes);
                ctrlMsg.data = songInfo;
                break;
            default:
                Log.e(TAG, "Invalid Data Type Received");
                return null;
        }

        if (mMessageBuf.hasRemaining()) {
            Log.w(TAG, "There is data remaining in the buffer, despite structured parsing");

        }
        return ctrlMsg;
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
