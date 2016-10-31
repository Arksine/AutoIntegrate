package com.arksine.autointegrate.microcontroller;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.util.ArrayMap;
import android.util.Log;
import android.view.KeyEvent;

import com.arksine.autointegrate.R;
import com.arksine.autointegrate.activities.BrightnessChangeActivity;
import com.arksine.autointegrate.utilities.TaskerIntent;
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

    // TODO: might want to make this a preference
    // Delay between repetitive media keys when holding
    private final static int MEDIA_KEY_DELAY = 2000;

    private Context mContext;
    private List<ResistiveButton> mMappedButtons;
    private boolean mCustomCommands = false;

    private ArrayMap<String, ActionRunnable> mActions;

    private volatile boolean mIsHoldingBtn = false;

    private boolean mIsCameraEnabled = false;
    private Intent mCameraIntent;

    private AudioManager mAudioManger;
    private int mPrevVolume;

    private int mInitialBrightness = 1;
    private boolean mDimmerOn = false;
    private interface BrightnessControl {
        void DimmerOff();
        void DimmerOn();
        void DimmerChange(int reading);
    }
    private BrightnessControl mBrightnessControl;


    public static class DimmerMode {
        private DimmerMode(){}

        public final static int NONE = 0;
        public final static int AUTOBRIGHT = 1;
        public final static int DIGITAL = 2;
        public final static int ANALOG = 3;
    }

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

        mCustomCommands = PreferenceManager.getDefaultSharedPreferences(mContext)
                .getBoolean("controller_pref_key_custom_commands", false);

        // Get mapped buttons from GSON
        Gson gson = new Gson();
        SharedPreferences gsonFile = mContext.getSharedPreferences(
                mContext.getString(R.string.gson_button_list_file),
                Context.MODE_PRIVATE);
        String json = gsonFile.getString("ButtonList", "[]");
        Type collectionType = new TypeToken<List<ResistiveButton>>(){}.getType();
        mMappedButtons = gson.fromJson(json, collectionType);

        initDimmer(); // initialize the dimmer interface

        mActions = new ArrayMap<>();
        populateBuiltInActions();
    }

    private void initDimmer() {
        final SharedPreferences defaultPrefs = PreferenceManager.getDefaultSharedPreferences(mContext);

        int dMode = defaultPrefs.getInt("dimmer_pref_key_mode", 0);
        mInitialBrightness = defaultPrefs.getInt("dimmer_pref_key_initial_brightness", 200);

        BrightnessControl emptyBC = new BrightnessControl() {
            @Override
            public void DimmerOff() {}

            @Override
            public void DimmerOn() {}

            @Override
            public void DimmerChange(int reading) {}
        };

        switch (dMode) {
            case DimmerMode.NONE:
                // Dimmer is not used, so control functions are empty
                mBrightnessControl = emptyBC;
                break;
            case DimmerMode.AUTOBRIGHT:
                mBrightnessControl = new BrightnessControl() {
                    @Override
                    public void DimmerOff() {
                        if (mDimmerOn) {
                            mDimmerOn = false;
                            Settings.System.putInt(mContext.getContentResolver(),
                                    Settings.System.SCREEN_BRIGHTNESS_MODE,
                                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
                            Log.i(TAG, "Auto Brightness off ");
                        }
                    }

                    @Override
                    public void DimmerOn() {
                        // Autobrightness on
                        if (!mDimmerOn) {
                            mDimmerOn = true;
                            Settings.System.putInt(mContext.getContentResolver(),
                                    Settings.System.SCREEN_BRIGHTNESS_MODE,
                                    Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC);
                            Log.i(TAG, "Auto Brightness on ");
                        }
                    }

                    @Override
                    public void DimmerChange(int reading) {}
                };
                break;
            case DimmerMode.DIGITAL:
                // We are in manual mode, make sure autobrightness is off
                if (isAutoBrightnessOn()) {
                    Settings.System.putInt(mContext.getContentResolver(),
                            Settings.System.SCREEN_BRIGHTNESS_MODE,
                            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
                    Log.i(TAG, "Auto Brightness off ");
                }

                final int onBrightness = defaultPrefs.getInt("dimmer_pref_key_high_brightness",100);
                if (onBrightness <= 0) {
                    Log.i(TAG, "Dimmer Mode Digital not calibated");
                    mBrightnessControl = emptyBC;
                    break;
                }

                mBrightnessControl = new BrightnessControl() {
                    @Override
                    public void DimmerOff() {
                        if (mDimmerOn) {
                            mDimmerOn = false;
                            launchBrightnessChangeActivity(mInitialBrightness);
                        }
                    }

                    @Override
                    public void DimmerOn() {
                        // Make sure that Dimmer hasn't already been toggled so we dont reset
                        // initial brightness
                        if (!mDimmerOn) {
                            mDimmerOn = true;
                            setInitialBrightness();
                            launchBrightnessChangeActivity(onBrightness);
                        }
                    }

                    @Override
                    public void DimmerChange(int reading) {}
                };
                break;
            case DimmerMode.ANALOG:
                // We are in manual mode, make sure autobrightness is off
                if (isAutoBrightnessOn()) {
                    Settings.System.putInt(mContext.getContentResolver(),
                            Settings.System.SCREEN_BRIGHTNESS_MODE,
                            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
                    Log.i(TAG, "Auto Brightness off ");
                }

                final int highReading = defaultPrefs.getInt("dimmer_pref_key_high_reading", 1000);
                final int lowReading = defaultPrefs.getInt("dimmer_pref_key_low_reading", 100);
                final int highBrightness = defaultPrefs.getInt("dimmer_pref_key_high_brightness", 200);
                final int lowBrightness = defaultPrefs.getInt("dimmer_pref_key_low_brightness", 100);

                if ((highReading <= 0) || (lowReading <= 0) ||
                        (highBrightness <= 0) || (lowBrightness <= 0)) {
                    Log.i(TAG, "Dimmer Mode Analog not calibated");
                    mBrightnessControl = emptyBC;
                    break;
                }

                final int readingDiff = highReading - lowReading;
                final int brightDiff = highBrightness - lowBrightness;

                mBrightnessControl = new BrightnessControl() {
                    @Override
                    public void DimmerOff() {
                        if (mDimmerOn) {
                            mDimmerOn = false;
                            launchBrightnessChangeActivity(mInitialBrightness);
                        }
                    }

                    @Override
                    public void DimmerOn() {
                        // Make sure that Dimmer hasn't already been toggled so we dont reset
                        // initial brightness
                        if (!mDimmerOn) {
                            mDimmerOn = true;
                            setInitialBrightness();
                        }
                    }

                    @Override
                    public void DimmerChange(int reading) {
                        int offsetReading = reading - lowReading;

                        // Make sure our reading falls in the correct range
                        if (offsetReading <= 0) {
                            offsetReading = 1;
                        } else if (offsetReading > readingDiff) {
                            offsetReading = readingDiff;
                        }
                        float readingCoef = (float) offsetReading / readingDiff;
                        int brightness = Math.round(readingCoef * brightDiff) + lowBrightness;
                        Log.d(TAG, "Calculated Brightness: " + brightness);

                        launchBrightnessChangeActivity(brightness);
                    }
                };
                break;
            default:
                Log.i(TAG, "Invalid Dimmer Mode");
                // Dimmer is invalid, so control functions are empty
                mBrightnessControl = emptyBC;
        }

    }

    private boolean isAutoBrightnessOn() {
        int mode = -1;
        try {
            mode = Settings.System.getInt(mContext.getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS_MODE);
        } catch (Exception e) {
            e.printStackTrace();
        }

        /* change to manual mode if automatic is enabled */
        return (mode == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC);
    }

    private void setInitialBrightness() {
        try {
            mInitialBrightness = Settings.System.getInt(mContext.getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS);

        } catch (Exception e) {
            mInitialBrightness = 200;
            e.printStackTrace();
        }
    }

    private void launchBrightnessChangeActivity(int brightness) {
        Intent brightActivityIntent = new Intent(mContext,
                BrightnessChangeActivity.class);
        brightActivityIntent.putExtra("Brightness", brightness);
        brightActivityIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(brightActivityIntent);
    }


    private void populateBuiltInActions() {


        boolean showVolumeUi = PreferenceManager.getDefaultSharedPreferences(mContext)
                .getBoolean("controller_pref_key_volume_ui", false);

        final int volumeUiFlag = showVolumeUi ? AudioManager.FLAG_SHOW_UI : 0;

        // Volume Keys
        mActions.put("Volume Up", new ActionRunnable() {
            @Override
            public void run() {
                Log.i(TAG, "Send Volume Up");
                do {
                    mAudioManger.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                            AudioManager.ADJUST_RAISE, volumeUiFlag);
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
                            AudioManager.ADJUST_LOWER, volumeUiFlag);
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
                            volumeUiFlag);
                } else {
                    mAudioManger.setStreamVolume(AudioManager.STREAM_MUSIC, mPrevVolume,
                            volumeUiFlag);
                }
            }
        });

        // Media Keys
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

        mActions.put("Next",buildSkipMediaRunnable(KeyEvent.KEYCODE_MEDIA_NEXT));
        mActions.put("Previous", buildSkipMediaRunnable(KeyEvent.KEYCODE_MEDIA_PREVIOUS));
        mActions.put("Fast Forward", buildSeekMediaRunnable(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD));
        mActions.put("Rewind", buildSeekMediaRunnable(KeyEvent.KEYCODE_MEDIA_REWIND));

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
                // TODO: Send intent to close integrated camera activity if integrated cam is enabled,
                //       Could programatically run a tasker task to hit the "back" button otherwise
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
        mActions.put("Dimmer", new ActionRunnable() {
            @Override
            public void run() {
                switch(data) {
                    case "On":
                        mBrightnessControl.DimmerOn();
                        break;
                    case "Off":
                        mBrightnessControl.DimmerOff();
                        break;
                    default:
                        int reading = Integer.parseInt(data);
                        mBrightnessControl.DimmerChange(reading);
                }
            }
        });
        mActions.put("Toggle Auto-Brightness", new ActionRunnable() {
            @Override
            public void run() {
                if (isAutoBrightnessOn()) {
                    Settings.System.putInt(mContext.getContentResolver(),
                            Settings.System.SCREEN_BRIGHTNESS_MODE,
                            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
                } else {
                    Settings.System.putInt(mContext.getContentResolver(),
                            Settings.System.SCREEN_BRIGHTNESS_MODE,
                            Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC);
                }
            }
        });

        mActions.put("Application", new ActionRunnable() {
            @Override
            public void run() {
                Log.i(TAG, "Sending Application Intent");
                Intent appIntent = mContext.getPackageManager().getLaunchIntentForPackage(this.data);
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
                if ( TaskerIntent.testStatus(mContext).equals(TaskerIntent.Status.OK) ) {
                    TaskerIntent i = new TaskerIntent(this.data);
                    mContext.sendBroadcast( i );
                }

            }
        });

    }


    private ActionRunnable buildSkipMediaRunnable(final int keycode) {
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
                            Thread.sleep(MEDIA_KEY_DELAY);

                        } catch (InterruptedException e) {
                            Log.e(TAG, e.getMessage());
                        }
                    }

                } while (mIsHoldingBtn);
            }
        };

    }

    private ActionRunnable buildSeekMediaRunnable(final int keycode) {
        return new ActionRunnable() {
            @Override
            public void run() {
                Intent mediaIntent;

                // send key down event
                mediaIntent = new Intent(Intent.ACTION_MEDIA_BUTTON);
                mediaIntent.putExtra(Intent.EXTRA_KEY_EVENT,
                        new KeyEvent(KeyEvent.ACTION_DOWN, keycode));
                mContext.sendBroadcast(mediaIntent);


                do {
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException e) {
                        Log.e(TAG, e.getMessage());
                    }

                } while (mIsHoldingBtn);

                // send key up event
                mediaIntent = new Intent(Intent.ACTION_MEDIA_BUTTON);
                mediaIntent.putExtra(Intent.EXTRA_KEY_EVENT,
                        new KeyEvent(KeyEvent.ACTION_UP, keycode));
                mContext.sendBroadcast(mediaIntent);

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
            case "Dimmer":
                action = mActions.get(message.command);
                action.setData(message.data);
                break;
            case "Reverse":
                action = mActions.get(message.command + " " + message.data);
                break;
            default:
                if (mCustomCommands) {
                    Log.i(TAG, "Broacasting custom command: " + message.command);
                    Intent customIntent = new Intent(mContext.getString(R.string.ACTION_DATA_RECIEVED));
                    customIntent.putExtra(mContext.getString(R.string.EXTRA_COMMAND), message.command);
                    customIntent.putExtra(mContext.getString(R.string.EXTRA_DATA), message.data);
                    mContext.sendBroadcast(customIntent);
                }
        }

        if (action != null) {
            Thread actionThread = new Thread(action);
            actionThread.start();
        }
    }

    public void close() {
        // Save Dimmer Brightness to shared prefs.  We need to do this so we can properly reset
        // the dimmer after shutdown.
        PreferenceManager.getDefaultSharedPreferences(mContext).edit()
                .putInt("dimmer_pref_key_initial_brightness", mInitialBrightness)
                .apply();
    }

}
