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
 * Forwards information received from Micro controller to ButtonLearningActivity
 */

public class ControllerLearnHandler extends Handler {

    private static final String TAG = "ControllerLearnHandler";
    private Context mContext = null;

    ControllerLearnHandler(Looper looper, Context context) {
        super(looper);
        mContext = context;
        Log.i(TAG, "Controller is in Learning Mode.");
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

                // Local broadcast to learning activity, we only learn click and dimmer events
                if(ctrlMsg.command.equals("click") || ctrlMsg.command.equals("dimmer")) {
                    // TODO: direct intent to specific class?
                    Intent msgIntent = new Intent(mContext.getString(R.string.ACTION_CONTROLLER_LEARN_DATA));
                    msgIntent.putExtra("Command", ctrlMsg.command);
                    msgIntent.putExtra("Data", ctrlMsg.data);
                    LocalBroadcastManager.getInstance(mContext).sendBroadcast(msgIntent);
                }
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
