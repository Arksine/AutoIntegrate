package com.arksine.autointegrate.microcontroller;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import com.arksine.autointegrate.interfaces.McuLearnCallbacks;
import com.arksine.autointegrate.utilities.DLog;
import com.arksine.autointegrate.microcontroller.MCUDefs.*;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Handler to parse incoming packets from the Micro Controller and process its events
 */

public class ControllerInputHandler extends Handler {

    private static final String TAG = "ControllerInputHandler";

    private Context mContext = null;
    private CommandProcessor mCommandProcessor;
    private MicroControllerCom.McuEvents mMcuEvents = null;
    private McuLearnCallbacks mMcuLearnCallbacks = null;

    private interface InputMode {
        void ProcessInput(ControllerMessage ctrlMsg);
    }
    private final InputMode mLearningMode = new InputMode() {
        @Override
        public void ProcessInput(ControllerMessage ctrlMsg) {
            if (mMcuLearnCallbacks != null) {
                switch (ctrlMsg.command) {
                    case STARTED:
                        // invoke Mcu OnStarted Callback with Id
                        Log.i(TAG, "Successfully started in Learning mode");
                        mMcuEvents.OnStarted((String)ctrlMsg.data);
                        break;
                    case CLICK:
                        mMcuLearnCallbacks.onButtonClicked((int)ctrlMsg.data);
                        break;
                    case DIMMER:
                        mMcuLearnCallbacks.onDimmerToggled((boolean)ctrlMsg.data);
                        break;
                    case DIMMER_LEVEL:
                        mMcuLearnCallbacks.onDimmerLevelChanged((int)ctrlMsg.data);
                        break;
                    default:
                        Log.i(TAG, "Incorrect data type for calibration received");
                }
            } else {
                Log.w(TAG, "Error, device in learning mode but no callbacks are set");
            }

        }
    };

    private final InputMode mExecutionMode = new InputMode() {
        @Override
        public void ProcessInput(ControllerMessage ctrlMsg) {
            mCommandProcessor.executeAction(ctrlMsg);
        }
    };

    private InputMode mInputMode;

    private ByteBuffer mReceivedBuffer = ByteBuffer.allocate(512);
    private boolean mIsLengthByte = false;
    private boolean mIsEscapedByte = false;
    private boolean mIsValidPacket = false;
    private int mPacketLength = 0;
    private int mChecksum = 0;

    // TODO: rename "learning mode" to direct access mode if using a remote callback
    ControllerInputHandler(Looper looper, Context context, MicroControllerCom.McuEvents mcuEvents,
                           boolean isLearningMode, McuLearnCallbacks cbs) {
        super(looper);
        mContext = context;
        mMcuEvents = mcuEvents;
        mReceivedBuffer.order(ByteOrder.LITTLE_ENDIAN);
        mCommandProcessor = new CommandProcessor(mContext, mcuEvents);
        this.mMcuLearnCallbacks = cbs;
        this.setMode(isLearningMode);
    }

    @Override
    public void handleMessage(Message msg) {
        parseBytes((byte[])msg.obj);
    }

    private void parseBytes(byte[] data) {
        for (byte b : data) {
            if (b == (byte) 0xF1) {
                mIsValidPacket = true;
                mIsEscapedByte = false;
                mIsLengthByte = true;

                mReceivedBuffer.clear();
                mPacketLength = 0;
                mChecksum = 0xF1;
            } else if (!mIsValidPacket) {
                DLog.i(TAG, "Invalid byte received: " + b);
            } else if (b == (byte) 0x1A && !mIsEscapedByte) {
                mIsEscapedByte = true;
            } else {
                if (mIsEscapedByte) {
                    mIsEscapedByte = false;
                    if (b == (byte) 0x20) {
                        // 0xF1 is escaped as 0x20
                        b = (byte) 0xF1;
                    }
                    // Note: 0x1A is escaped as 0x1A, so we don't need to reset the current byte
                }

                if (mIsLengthByte) {
                    mIsLengthByte = false;
                    mPacketLength = b & (0xFF);
                    mChecksum += mPacketLength;
                } else if (mReceivedBuffer.position() == mPacketLength) {
                    // This is the checksum byte

                    // Checksum is all bytes added up (not counting header and escape bytes) mod 256
                    if ((mChecksum % 256) == (b & 0xFF)) {
                        mReceivedBuffer.flip();
                        parsePacket();

                    } else {
                        Log.i(TAG, "Invalid checksum, discarding packet");
                    }

                    // The next byte received must be 0xF1, regardless of what happened here
                    mIsValidPacket = false;
                } else {
                    // Add byte to packet buffer
                    mReceivedBuffer.put(b);
                    mChecksum += (b & 0xFF);
                }
            }
        }
    }

    // Reads a message from the Micro Controller and parses it
    private boolean parsePacket() {

        DLog.i(TAG, "MCU Packet Length: " + mReceivedBuffer.limit());

        if(mReceivedBuffer.limit() < 2) {
            Log.e(TAG, "Invalid data packet, must at least be 2 bytes long");
            return false;
        }

        ControllerMessage ctrlMsg = new ControllerMessage();

        ctrlMsg.command = McuInputCommand.getCommandFromByte(mReceivedBuffer.get());
        DLog.v(TAG, "MCU Command Recd: " + ctrlMsg.command.toString());
        if (ctrlMsg.command == McuInputCommand.NONE) {
            Log.e(TAG, "Invalid Command Received");
            return false;
        } else if (ctrlMsg.command == McuInputCommand.RADIO_DATA) {
            byte[] radioBytes = new byte[mReceivedBuffer.remaining()];
            mReceivedBuffer.get(radioBytes);
            mMcuEvents.OnRadioDataReceived(radioBytes);
            return true;
        }

        if (!mReceivedBuffer.hasRemaining()) {
            Log.i(TAG, "Invalid Packet, end of buffer reached before data received");
            //  TODO: In the future it may be possible to receive a command that has no payload.
            //  In that case, we would need to send it here and not return false
            return false;
        }

        switch (ctrlMsg.command.getDataType()) {
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
            case BYTE_ARRAY:
                byte[] arrayBytes = new byte[mReceivedBuffer.remaining()];
                mReceivedBuffer.get(arrayBytes);
                ctrlMsg.data = arrayBytes;
                break;
            default:
                Log.e(TAG, "Invalid Data Type Received: " + ctrlMsg.command.getDataType().toString());
                return false;
        }

        if (mReceivedBuffer.hasRemaining()) {
            Log.w(TAG, "There is data remaining in the buffer after structured parsing");

        }


        // send the parsed message for processing
        if (ctrlMsg.command == McuInputCommand.LOG) {
            Log.i("Micro Controller", (String) ctrlMsg.data);

        } else {
            DLog.v(TAG, ctrlMsg.command + " " + ctrlMsg.data);
            mInputMode.ProcessInput(ctrlMsg);
        }

        return true;
    }

    void setMode(boolean isLearningMode) {
        if (isLearningMode) {
            DLog.v(TAG, "Controller is in Learning Mode.");
            mInputMode = mLearningMode;
        } else {
            DLog.v(TAG, "Controller is in Execution Mode.");
            mInputMode = mExecutionMode;
        }
    }


    public void close() {
        // if we are in execution mode we need to clean up the command processor
        if (mCommandProcessor != null) {
            mCommandProcessor.close();
        }
    }
}
