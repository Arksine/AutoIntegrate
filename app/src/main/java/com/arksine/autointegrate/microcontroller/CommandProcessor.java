package com.arksine.autointegrate.microcontroller;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.provider.Settings;
import android.util.ArrayMap;
import android.util.Log;
import android.view.KeyEvent;

import com.arksine.autointegrate.R;
import com.arksine.autointegrate.utilities.UtilityFunctions;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.List;

/**
 * Executes Android actions.  A class encapsulating a map is used rather than
 * as switch statement to allow the user to add custom commands
 */

public class CommandProcessor {
    private static String TAG = "CommandProcessor";

    // TODO: give user option to show Volume UI (AudioManager.FLAG_SHOW_UI or 0)

    private Context mContext;
    private List<ResistiveButton> mMappedButtons;

    private ArrayMap<String, ActionRunnable> mActions;

    private volatile boolean mIsHoldingBtn = false;
    private boolean mIsCameraEnabled = false;

    private AudioManager mAudioManger;
    private int mPrevVolume;

    // TODO: necessary if we want to allow user to create custom commands
    //private ArrayMap<String, Runnable> mCustomCommands;

    // The class below extends runnable so we can pass data to our command runnables
    private class ActionRunnable implements Runnable {
        protected String data;


        ActionRunnable() {
            this.data = "";
        }

        public void setData(String _data){
            this.data = _data;
        }

        @Override
        public void run() {}
    }


    CommandProcessor(Context context) {
        mContext = context;
        mAudioManger = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);

        UtilityFunctions.checkSettingsPermission(mContext);

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
        // TODO: holding on volume keys work with 100ms sleep, test 200ms

        // TODO: holding media keys not working, may need to adjust sleep time or add repeatcount.
        //       could also just lift and release each key for each repetition

        // Volume Keys
        mActions.put("Volume Up", new ActionRunnable() {
            @Override
            public void run() {
                Log.i(TAG, "Send Volume Up");
                do {
                    mAudioManger.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                            AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI);
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException e) {
                        Log.e(TAG, e.getMessage());
                    }

                } while (mIsHoldingBtn);
            }
        });
        mActions.put("Volume Down", new ActionRunnable() {
            @Override
            public void run() {
                Log.i(TAG, "Send Volume Down");
                do {
                    mAudioManger.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                            AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI);
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException e) {
                        Log.e(TAG, e.getMessage());
                    }
                } while (mIsHoldingBtn);
            }
        });
        mActions.put("Mute", new ActionRunnable() {
            @Override
            public void run() {
                Log.i(TAG, "Send Mute");

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
        });

        // Media Keys
        //TODO: play pause is going to need its own runnable, because we do not want it to repeat
        mActions.put("Play/Pause", new ActionRunnable() {
            public void run() {
                Log.i(TAG, "Send Media, Play/Pause");

                Intent mediaIntent = new Intent(Intent.ACTION_MEDIA_BUTTON);
                mediaIntent.putExtra(Intent.EXTRA_KEY_EVENT,
                        new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE));
                mContext.sendBroadcast(mediaIntent);

                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Log.e(TAG, e.getMessage());
                }

                mediaIntent = new Intent(Intent.ACTION_MEDIA_BUTTON);
                mediaIntent.putExtra(Intent.EXTRA_KEY_EVENT,
                        new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE));
                mContext.sendBroadcast(mediaIntent);

            }

        });
        mActions.put("Next",buildMediaRunnable(KeyEvent.KEYCODE_MEDIA_NEXT));
        mActions.put("Previous", buildMediaRunnable(KeyEvent.KEYCODE_MEDIA_PREVIOUS));
        mActions.put("Fast Forward", buildMediaRunnable(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD));
        mActions.put("Rewind", buildMediaRunnable(KeyEvent.KEYCODE_MEDIA_REWIND));

        // Custom events
        mActions.put("Reverse On", new ActionRunnable() {
            @Override
            public void run() {
                // TODO: launch integrated camera activity.  Need to check prefs to see if user
                //       wants to use integrated uvc camera app or outside app
                Log.i(TAG, "Send Launch Camera");
            }
        });
        mActions.put("Reverse Off", new ActionRunnable() {
            @Override
            public void run() {
                // TODO: Send intent to close integrated camera activity if integrated cam is enabled
                Log.i(TAG, "Send Close Camera");
            }
        });
        mActions.put("Toggle Camera", new ActionRunnable() {
            @Override
            public void run() {
                // TODO:
                Log.i(TAG, "Send Toggle Camera");
            }
        });
        mActions.put("Dimmer On", new ActionRunnable() {
            @Override
            public void run() {
                // Autobrightness on
                Settings.System.putInt(mContext.getContentResolver(),
                        Settings.System.SCREEN_BRIGHTNESS_MODE,
                        Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC);
                Log.i(TAG, "Auto Brightness on ");
            }
        });
        mActions.put("Dimmer Off", new ActionRunnable() {
            @Override
            public void run()  {
                // Autobrightness off
                Settings.System.putInt(mContext.getContentResolver(),
                        Settings.System.SCREEN_BRIGHTNESS_MODE,
                        Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
                Log.i(TAG, "Auto Brightness off ");
            }
        });
        mActions.put("Toggle AutoBrightness", new ActionRunnable() {
            @Override
            public void run() {
                try {
                    if (Settings.System.getInt(mContext.getContentResolver(),
                            Settings.System.SCREEN_BRIGHTNESS) ==
                            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL) {

                        Settings.System.putInt(mContext.getContentResolver(),
                                Settings.System.SCREEN_BRIGHTNESS_MODE,
                                Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC);
                    } else {

                        Settings.System.putInt(mContext.getContentResolver(),
                                Settings.System.SCREEN_BRIGHTNESS_MODE,
                                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
                    }
                } catch (Settings.SettingNotFoundException e) {
                    e.printStackTrace();
                }
            }
        });

        mActions.put("Application", new ActionRunnable() {
            @Override
            public void run() {
                Log.i(TAG, "Sending Application Intent");
                Intent appIntent = mContext.getPackageManager().getLaunchIntentForPackage(data);
                if (appIntent != null) {
                    appIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    mContext.startActivity(appIntent);
                } else {
                    Log.i(TAG, "Invalid application");
                }
            }
        });

        mActions.put("Tasker", new ActionRunnable() {
            @Override
            public void run() {
                // TODO: currently using tasker's external access api to execute.  Can create
                //       Locale/Tasker plugin that should also work with macrodroid.  
                Log.i(TAG, "Execute Tasker Task");

            }
        });


        // TODO: Should I have a dimmer action that responds to analog data?


    }

    private ActionRunnable buildMediaRunnable(final int keycode) {
        return new ActionRunnable() {
            @Override
            public void run() {
                Intent mediaIntent;

                do {
                    // send key down event
                    mediaIntent = new Intent(Intent.ACTION_MEDIA_BUTTON);
                    mediaIntent.putExtra(Intent.EXTRA_KEY_EVENT,
                            new KeyEvent(KeyEvent.ACTION_DOWN, keycode));
                    mContext.sendBroadcast(mediaIntent);

                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        Log.e(TAG, e.getMessage());
                    }

                    // send key up event
                    mediaIntent = new Intent(Intent.ACTION_MEDIA_BUTTON);
                    mediaIntent.putExtra(Intent.EXTRA_KEY_EVENT,
                            new KeyEvent(KeyEvent.ACTION_UP, keycode));
                    mContext.sendBroadcast(mediaIntent);

                    if (mIsHoldingBtn) {
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException e) {
                            Log.e(TAG, e.getMessage());
                        }
                    }

                } while (mIsHoldingBtn);

            }
        };

    }

    private ActionRunnable getButtonAction(String data, boolean isClickAction) {
        int id = Integer.parseInt(data);

        for (ResistiveButton btn : mMappedButtons) {

            int mappedId = btn.getId();
            int debounce = btn.getTolerance();

            if ((id >= (mappedId - debounce)) && (id <= (mappedId + debounce))) {
                // id is within tolerance, get command
                String action, type;
                if (isClickAction) {
                    action = btn.getClickAction();
                    type = btn.getClickType();
                } else {
                    action = btn.getHoldAction();
                    type = btn.getHoldType();
                }

                if (type.equals("Application") || type.equals("Tasker")) {
                    ActionRunnable actionRunnable = mActions.get(type);
                    actionRunnable.setData(btn.getClickAction());
                    return actionRunnable;
                } else {
                    return mActions.get(action);
                }
            }
        }

        Log.i(TAG, "Button is not mapped.");
        return null;
    }

    public void executeAction(ControllerMessage message) {

        ActionRunnable action = null;
        switch (message.command) {
            case "Click":
                mIsHoldingBtn = false;
                action = getButtonAction(message.data, true);
                break;
            case "Hold":
                mIsHoldingBtn = true;
                action = getButtonAction(message.data, false);
                break;
            case "Release":
                mIsHoldingBtn = false;
                break;
            case "Dimmer": // TODO:  allow for dimmer data to be an integer that relates to a brightness level
                action = mActions.get(message.command + " " + message.data);
                break;
            case "Reverse":
                action = mActions.get(message.command + " " + message.data);
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
