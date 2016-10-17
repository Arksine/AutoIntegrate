package com.arksine.autointegrate.microcontroller;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;


/**
 * Handler to parse incoming packets from the Micro Controller and process its events
 */

public class ControllerExecHandler extends Handler {

    private static final String TAG = "ControllerExecHandler";

    private Context mContext = null;
    private CommandProcessor mActionExector;


    private volatile boolean isHolding = false;




    ControllerExecHandler(Looper looper, Context context) {
        super(looper);
        mContext = context;
        mActionExector = new CommandProcessor(mContext);
        Log.i(TAG, "Controller is in Execution Mode.");
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
                mActionExector.executeAction(ctrlMsg);
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

        }
        else {
            Log.e(TAG, "Issue parsing string, invalid data recd: " + msg);
            ctrlMsg = null;
        }
        return ctrlMsg;
    }


}
