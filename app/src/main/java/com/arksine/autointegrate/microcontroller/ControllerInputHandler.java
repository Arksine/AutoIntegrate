package com.arksine.autointegrate.microcontroller;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.arksine.autointegrate.R;


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


    ControllerInputHandler(Looper looper, Context context, boolean isLearningMode) {
        super(looper);
        mContext = context;

        if (isLearningMode) {
            Log.i(TAG, "Controller is in Learning Mode.");
            mCommandProcessor = null;
            mProcessor = new MessageProcessor() {
                @Override
                public void ProcessMessage(ControllerMessage ctrlMsg) {
                    // Local broadcast to learning activity, we only learn click and dimmer events
                    if(ctrlMsg.command.equals("Click") || ctrlMsg.command.equals("Dimmer")) {
                        Intent msgIntent = new Intent(mContext.getString(R.string.ACTION_CONTROLLER_LEARN_DATA));
                        msgIntent.putExtra("Command", ctrlMsg.command);
                        msgIntent.putExtra("Data", ctrlMsg.data);
                        LocalBroadcastManager.getInstance(mContext).sendBroadcast(msgIntent);
                    }
                }
            };
        } else {
            Log.i(TAG, "Controller is in Execution Mode.");
            mCommandProcessor = new CommandProcessor(mContext);
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
        String message = (String) msg.obj;
        ControllerMessage ctrlMsg =  parseMessage(message);

        if (ctrlMsg != null) {
            if (ctrlMsg.command.equals("LOG")) {
                Log.i("Micro Controller", ctrlMsg.data);

            } else {
                Log.d(TAG, ctrlMsg.command + " " + ctrlMsg.data);
                mProcessor.ProcessMessage(ctrlMsg);
            }
        }
    }

    // Reads a message from the Micro Controller and parses it
    private ControllerMessage parseMessage(String msg) {

        ControllerMessage ctrlMsg;

        String[] tokens = msg.split(":");

        if (tokens.length == 2) {
            // command received or log message
            ctrlMsg = new ControllerMessage();

            ctrlMsg.command = tokens[0];
            ctrlMsg.data = tokens[1];
        } else {
            Log.e(TAG, "Issue parsing string, invalid data recd: " + msg);
            ctrlMsg = null;
        }
        return ctrlMsg;
    }

    public void close() {
        // if we are in execution mode we need to clean up the command processor
        if (mCommandProcessor != null) {
            mCommandProcessor.close();
        }
    }
}
