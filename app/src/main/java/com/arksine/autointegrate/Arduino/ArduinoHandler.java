package com.arksine.autointegrate.Arduino;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.widget.Toast;

import com.arksine.autointegrate.R;


/**
 * Class to parse incoming packets from the Arduino and process its events
 */

public class ArduinoHandler extends Handler {

    private static final String TAG = "ArduinoHandler";

    private Context mContext = null;

    private volatile boolean mLearningMode = false;

    // TODO: use gson to store and retrieve a list of LearnedButton objects frome shared prefs
   // private volatile List<ArduinoButton> mButtonList;

    private volatile boolean isHolding = false;

    // TODO: cread a broadcast receiver to listen for command to put in learning mode.  When
    //       toggling from learning mode to command execution mode, update the buttonset and
    //       dimmer set if necessary.  Use an extra on the received intent to tell us which one

    /**
     * Container for a message recieved from the arduino.  Packets are parsed into two parts, the
     * command received and its associated data
     */
    private class ArduinoMessage {
        public String command;
        public String data;
    }


    ArduinoHandler(Looper looper, Context context) {
        super(looper);
        mContext = context;
        // TODO: get prefrences specific to arduino events so I can build a map/array of commands
    }

    @Override
    public void handleMessage(Message msg) {
        String message = (String) msg.obj;
        ArduinoMessage ardMsg =  parseMessage(message);

        if (ardMsg != null) {
            if (ardMsg.command.equals("LOG")) {
                Log.i("Arduino", ardMsg.data);
                Toast.makeText(mContext, "Arduino Log Info, check logcat", Toast.LENGTH_SHORT).show();

            } else {
                Log.d(TAG, ardMsg.command + " " + ardMsg.data);

                if (mLearningMode) {
                    // Local broadcast to learning activity, we only learn click and dimmer events
                    if(ardMsg.command.equals("click") || ardMsg.command.equals("dimmer")) {
                        // TODO: direct intent to specific class?
                        Intent msgIntent = new Intent(mContext.getString(R.string.ACTION_ARDUINO_DATA_RECD));
                        msgIntent.putExtra("Command", ardMsg.command);
                        msgIntent.putExtra("Data", ardMsg.data);
                        LocalBroadcastManager.getInstance(mContext).sendBroadcast(msgIntent);
                    }
                } else {
                    // TODO: process/execute command.  We will need a new class for this.
                    switch (ardMsg.command) {
                        case "click":
                            processClick(ardMsg.data);
                            break;
                        case "hold":
                            break;
                        case "release":
                            break;
                        case "dimmer":
                            break;
                        case "reverse":
                            break;
                        default:
                    }
                }

            }
        }

    }

    // Reads a message from the arduino and parses it
    private ArduinoMessage parseMessage(String msg) {

        ArduinoMessage ardMsg;

        String[] tokens = msg.split(":");

        if (tokens.length == 2) {
            // command received or log message
            ardMsg = new ArduinoMessage();

            ardMsg.command = tokens[0];
            ardMsg.data = tokens[1];

        }
        else {
            Log.e(TAG, "Issue parsing string, invalid data recd: " + msg);
            ardMsg = null;
        }
        return ardMsg;
    }

    private void processClick(String clickId) {
        if (!isHolding) {

        } else {
            // Error, a click event came before a release event while holding
        }
    }

    private void processHold(String clickId) {
        // TODO: implement a runnable for this and run in another thread so we don't block
        //       other commands (like the release command!)
        if (!isHolding) {

        } else {
            // Error, a second hold event came before a release event
        }

    }

    private void processRelease(String clickId) {
        if(isHolding) {

        } else {
            // Error, release command received when not holding
        }
    }

}
