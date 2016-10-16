package com.arksine.autointegrate.microcontroller;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.os.Build;
import android.util.ArrayMap;
import android.util.Log;
import android.view.KeyEvent;

import com.arksine.autointegrate.R;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.List;

/**
 * Executes Android actions.  A class encapsulating a map is used rather than
 * as switch statement to allow the user to add custom commands
 */

public class ActionExecutor {
    private static String TAG = "ActionExecutor";

    // TODO: give user option to show Volume UI (AudioManager.FLAG_SHOW_UI or 0)

    private Context mContext;
    private List<ResistiveButton> mMappedButtons;

    private ArrayMap<String, Runnable> mActions;
    private volatile boolean mIsHolding = false;

    private AudioManager mAudioManger;
    private int mPrevVolume;

    /*  TODO: necessary if we want to allow user to create custom commands
    private ArrayMap<String, Runnable> mCustomCommands;
    // The class below extends runnable so we can pass data to our command runnables
    private class CommandRunnable implements Runnable {
        private String data;

        CommandRunnable() {
            this.data = "";
        }

        public void setData(String _data){
            this.data = _data;
        }

        @Override
        public void run() {}
    }*/


    ActionExecutor(Context context) {
        mContext = context;
        mAudioManger = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);

        // Get mapped buttons from GSON
        Gson gson = new Gson();
        SharedPreferences gsonFile = mContext.getSharedPreferences(
                mContext.getString(R.string.gson_button_list_file),
                Context.MODE_PRIVATE);
        String json = gsonFile.getString("ButtonList", "[]");
        Type collectionType = new TypeToken<List<ResistiveButton>>(){}.getType();
        mMappedButtons = gson.fromJson(json, collectionType);

        mActions = new ArrayMap<>();
        populateBuiltInActions();
    }

    // TODO: Allow user to create custom command, or just broadcast it as intent?
    /*
    private void addCustomCommand() {

        // TODO: custom commands should be added in prefs.  We need a command name, and some idea
        // of what the command should do based on the data it provides.
        mCustomCommands.put("test", new CommandRunnable() {
            @Override
            public void run() {

            }
        });
    }*/

    private void populateBuiltInActions() {

        // Volume Keys
        mActions.put("Volume Up", new Runnable() {
            @Override
            public void run() {
                if (mIsHolding) {
                    while (mIsHolding) {
                        mAudioManger.adjustVolume(AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI);
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException e) {
                            Log.e(TAG, e.getMessage());
                        }
                    }
                }
                else {
                    mAudioManger.adjustVolume(AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI);
                }
            }
        });
        mActions.put("Volume Down", new Runnable() {
            @Override
            public void run() {
                if (mIsHolding) {
                    while (mIsHolding) {
                        mAudioManger.adjustVolume(AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI);
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException e) {
                            Log.e(TAG, e.getMessage());
                        }
                    }
                }
                else {
                    mAudioManger.adjustVolume(AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI);
                }

            }
        });
        mActions.put("Mute", new Runnable() {
            @Override
            public void run() {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    mAudioManger.adjustVolume(AudioManager.ADJUST_TOGGLE_MUTE,
                            AudioManager.FLAG_SHOW_UI);
                } else {
                    int vol = mAudioManger.getStreamVolume(AudioManager.STREAM_MUSIC);
                    if (vol > 0) {
                        mPrevVolume = vol;
                        mAudioManger.setStreamVolume(AudioManager.STREAM_MUSIC, 0,
                                AudioManager.FLAG_SHOW_UI);
                    } else {
                        mAudioManger.setStreamVolume(AudioManager.STREAM_MUSIC, mPrevVolume,
                                AudioManager.FLAG_SHOW_UI);
                    }
                }
            }
        });

        // Media Keys
        mActions.put("Play/Pause", new Runnable() {
            @Override
            public void run() {
                Intent mediaIntent = new Intent(Intent.ACTION_MEDIA_BUTTON);
                mediaIntent.putExtra(Intent.EXTRA_KEY_EVENT,
                        new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE));
                mContext.sendBroadcast(mediaIntent);
            }
        });
        mActions.put("Next", new Runnable() {
            @Override
            public void run() {
                Intent mediaIntent = new Intent(Intent.ACTION_MEDIA_BUTTON);
                mediaIntent.putExtra(Intent.EXTRA_KEY_EVENT,
                        new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_NEXT));
                mContext.sendBroadcast(mediaIntent);
            }
        });
        mActions.put("Previous", new Runnable() {
            @Override
            public void run() {
                Intent mediaIntent = new Intent(Intent.ACTION_MEDIA_BUTTON);
                mediaIntent.putExtra(Intent.EXTRA_KEY_EVENT,
                        new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PREVIOUS));
                mContext.sendBroadcast(mediaIntent);
            }
        });
        mActions.put("Fast Forward", new Runnable() {
            @Override
            public void run() {
                Intent mediaIntent = new Intent(Intent.ACTION_MEDIA_BUTTON);
                mediaIntent.putExtra(Intent.EXTRA_KEY_EVENT,
                        new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD));
                mContext.sendBroadcast(mediaIntent);
            }
        });
        mActions.put("Rewind", new Runnable() {
            @Override
            public void run() {
                Intent mediaIntent = new Intent(Intent.ACTION_MEDIA_BUTTON);
                mediaIntent.putExtra(Intent.EXTRA_KEY_EVENT,
                        new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_REWIND));
                mContext.sendBroadcast(mediaIntent);
            }
        });

        // Custom events
        mActions.put("Launch Camera", new Runnable() {
            @Override
            public void run() {
                // TODO: launch integrated camera activity.  Need to check prefs to see if user
                //       wants to use integrated uvc camera app or outside app
            }
        });
        mActions.put("Close Camera", new Runnable() {
            @Override
            public void run() {
                // TODO: Send intent to close integrated camera activity if integrated cam is enabled
            }
        });
        mActions.put("Dimmer On", new Runnable() {
            @Override
            public void run() {
                // TODO: Get preference to determine what should be done with brightness when
                //       Dimmer is on
            }
        });
        mActions.put("Dimmer Off", new Runnable() {
            @Override
            public void run()  {
                // TODO: Get preference to determine what should be done with brightness when
                //       Dimmer is off
            }
        });
        // TODO: add toggle camera and toggle dimmer?

                // TODO: Add user custom events, launch application, tasker / macrodroid

    }

    private Runnable getButtonAction(String data, boolean isClickAction) {
        int id = Integer.parseInt(data);

        for (ResistiveButton btn : mMappedButtons) {

            int debounce = btn.getTolerance();
            if (btn.isMultiplied())
                debounce = debounce * 10;

            if ((id >= (btn.getId() - debounce)) && (id <= (btn.getId() + debounce))) {
                // id is within tolerance, get command
                if (isClickAction) {
                    return mActions.get(btn.getClickAction());
                } else {
                    return mActions.get(btn.getHoldAction());
                }
            }
        }

        Log.i(TAG, "Button is not mapped.");
        return null;
    }

    public void executeAction(ControllerMessage message) {

        Runnable action = null;
        switch (message.command) {
            case "click":
                action = getButtonAction(message.data, true);
                break;
            case "hold":
                mIsHolding = true;
                action = getButtonAction(message.data, false);
                break;
            case "release":
                mIsHolding = false;
                break;
            case "dimmer":
                if (message.data.equals("on")) {
                    action = mActions.get("Dimmer On");
                } else {
                    action = mActions.get("Dimmer Off");
                }
                break;
            case "reverse":
                if (message.data.equals("on")) {
                    action = mActions.get("Launch Camera");
                } else {
                    action = mActions.get("Close Camera");
                }
                break;
            default:
                Log.i(TAG, "Unknown command: " + message.command);
                // TODO: get custom action here if we choose to implement them
        }

        if (action != null) {
            Thread actionThread = new Thread(action);
            actionThread.start();
        }
    }

}
