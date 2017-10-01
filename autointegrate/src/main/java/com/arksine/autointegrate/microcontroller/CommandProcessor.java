package com.arksine.autointegrate.microcontroller;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.v4.content.LocalBroadcastManager;
import android.util.ArrayMap;
import android.view.KeyEvent;

import com.arksine.autointegrate.AutoIntegrate;
import com.arksine.autointegrate.R;
import com.arksine.autointegrate.activities.BrightnessChangeActivity;
import com.arksine.autointegrate.interfaces.MCUControlInterface;
import com.arksine.autointegrate.utilities.RootManager;
import com.arksine.autointegrate.utilities.TaskerIntent;
import com.arksine.autointegrate.utilities.UtilityFunctions;
import com.arksine.autointegrate.microcontroller.MCUDefs.*;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import timber.log.Timber;

// TODO: Create a preference to show status toasts, then create an invisble activity that
// shows them if enabled

/**
 * Executes Android actions.  A class encapsulating a map is used rather than
 * as switch statement to allow the user to add custom commands
 */

public class CommandProcessor {

    public static class DimmerMode {
        private DimmerMode(){}

        public final static int NONE = 0;
        public final static int AUTOBRIGHT = 1;
        public final static int DIGITAL = 2;
        public final static int ANALOG = 3;
    }

    private interface BrightnessControl {
        void DimmerOff();
        void DimmerOn();
        void DimmerChange(int reading);
    }

    private interface ReverseExitListener {
        void OnReverseOff();
    }

    // The class below extends runnable so we can pass data to our command runnables
    private abstract class ActionRunnable implements Runnable {
        protected Object data;

        ActionRunnable() {
            this.data = "";
        }

        public void setData(Object _data){
            this.data = _data;
        }

        @Override
        abstract public void run();
    }

    public enum AudioSource {HD_RADIO, AUX}
    private AudioSource mCurrentSource = AudioSource.HD_RADIO;
    private boolean mRadioOn = false;  // TODO: this variable tracks the radio.  If the AudioSource
                                        // if HD Radio and the radio is on, capture audio focuse
                                        // and forward media keys to control the HD Radio

    // TODO: might want to make these a preference
    // Delay between repetitive media keys when holding (in ms)
    private final static int MEDIA_KEY_DELAY = 2000;
    // Delay between volume adjustments when holding (in ms)
    private final static int VOLUME_KEY_DELAY = 200;

    private Context mContext;
    private MicroControllerCom.McuEvents mMcuEvents;
    private MCUControlInterface mMcuControlInterface;
    private List<ResistiveButton> mMappedButtons;
    private boolean mBroadcastCustomCommands = false;
    private ArrayMap<String, ActionRunnable> mActions;
    private AtomicBoolean mIsHoldingBtn = new AtomicBoolean(false);
    private boolean mCameraIsOn = false;
    private Intent mCameraIntent = null;
    private AudioManager mAudioManger;
    private int mPrevVolume;
    private int mInitialBrightness = 1;
    private int mDimmerMode;
    private boolean mDimmerOn = false;
    private BrightnessControl mBrightnessControl;
    private ReverseExitListener mReverseExitListener = null;

    CommandProcessor(Context context, MicroControllerCom.McuEvents mcuEvents) {
        mContext = context;
        mMcuEvents = mcuEvents;
        mMcuControlInterface = AutoIntegrate.getMcuInterfaceRef().get();
        mAudioManger = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);

        mBroadcastCustomCommands = PreferenceManager.getDefaultSharedPreferences(mContext)
                .getBoolean("controller_pref_key_custom_commands", false);

        mCurrentSource = AudioSource.valueOf(PreferenceManager
                .getDefaultSharedPreferences(mContext)
                .getString("audio_pref_key_current_source", "HD_RADIO"));

        updateButtons();

        updateDimmer(); // initialize the dimmer interface

        updateReverseCommand();  // initialize reverse command

        mActions = new ArrayMap<>();
        populateBuiltInActions();
    }

    public void updateButtons() {
        // Get mapped buttons from GSON
        Gson gson = new Gson();
        SharedPreferences gsonFile = mContext.getSharedPreferences(
                mContext.getString(R.string.gson_button_list_file),
                Context.MODE_PRIVATE);
        String json = gsonFile.getString("ButtonList", "[]");
        Type collectionType = new TypeToken<List<ResistiveButton>>(){}.getType();
        mMappedButtons = gson.fromJson(json, collectionType);
    }

    public void updateButtons(List<ResistiveButton> buttonList) {
        mMappedButtons = buttonList;
    }

    public void updateDimmer() {
        final SharedPreferences defaultPrefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        mInitialBrightness = defaultPrefs.getInt("dimmer_pref_key_initial_brightness", 200);

        final int mode = defaultPrefs.getInt("dimmer_pref_key_mode", 0);
        final int highReading = defaultPrefs.getInt("dimmer_pref_key_high_reading", 1000);
        final int lowReading = defaultPrefs.getInt("dimmer_pref_key_low_reading", 100);
        final int highBrightness = defaultPrefs.getInt("dimmer_pref_key_high_brightness", 200);
        final int lowBrightness = defaultPrefs.getInt("dimmer_pref_key_low_brightness", 100);

        updateDimmer(mode, highReading, lowReading, highBrightness, lowBrightness);
    }

    public void updateDimmer(int mode, final int highReading, final int lowReading,
                             final int highBrightness, final int lowBrightness) {
        mDimmerMode = mode;

        BrightnessControl emptyBC = new BrightnessControl() {
            @Override
            public void DimmerOff() {}

            @Override
            public void DimmerOn() {}

            @Override
            public void DimmerChange(int reading) {}
        };

        switch (mDimmerMode) {
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
                            Timber.v("Auto Brightness off ");
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
                            Timber.v("Auto Brightness on ");
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
                    Timber.v("Auto Brightness off ");
                }

                if (highBrightness <= 0) {
                    Timber.i("Digital Dimmer Mode not calibated");
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
                            launchBrightnessChangeActivity(highBrightness);
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
                    Timber.v("Auto Brightness off ");
                }

                if ((highReading <= 0) || (lowReading <= 0) ||
                        (highBrightness <= 0) || (lowBrightness <= 0)) {
                    Timber.i("Analog Dimmer Mode not calibated");
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
                        Timber.d("Analog Calculated Brightness: %d", brightness);

                        launchBrightnessChangeActivity(brightness);
                    }
                };
                break;
            default:
                Timber.w("Invalid Dimmer Mode");
                // Dimmer is invalid, so control functions are empty
                mBrightnessControl = emptyBC;
        }
    }

    public void updateReverseCommand() {
        SharedPreferences globalPrefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        String camSetting = globalPrefs.getString("controller_pref_key_select_camera_app", "0");
        String appPkg = globalPrefs.getString("controller_pref_key_camera_ex_app", "0");
        updateReverseCommand(camSetting, appPkg);
    }


    public void updateReverseCommand(String camSetting, String appPackage) {

        switch (camSetting) {
            case "0":       // No App set
                mCameraIntent = null;
                break;
            case "1":       // Companion Cam Activity
                mCameraIntent = mContext.getPackageManager()
                        .getLaunchIntentForPackage("com.arksine.autocamera");
                if (mCameraIntent != null) {
                    Timber.v("Companion Camera Intent set");
                    mCameraIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    // Add extra to tell activity to allow autoshutdown
                    mCameraIntent.putExtra(mContext.getString(R.string.LAUNCH_CAMERA_EXTRA), true);
                    mReverseExitListener = new ReverseExitListener() {
                        @Override
                        public void OnReverseOff() {
                            Intent camExitIntent = new Intent(mContext.getString(R.string.ACTION_CLOSE_CAMERA));
                            mContext.sendBroadcast(camExitIntent);
                        }
                    };
                } else {
                    Timber.v("Companion Camera Package not installed");
                }
                break;
            case "2":       // Custom App
                Timber.d("Setting Camera App to package: %s", appPackage);
                mCameraIntent = mContext.getPackageManager().getLaunchIntentForPackage(appPackage);
                if (mCameraIntent != null) {
                    mCameraIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    // With a custom app we will exit with a back button press if Signature Permissions
                    // or Root access is available
                    if (UtilityFunctions.hasPermission(mContext, "android.permission.INJECT_EVENTS")) {
                        // System level permission granted
                        mReverseExitListener = new ReverseExitListener() {
                            @Override
                            public void OnReverseOff() {
                                Instrumentation inst = new Instrumentation();
                                inst.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
                            }
                        };
                    } else if (RootManager.isRootAvailable()) {
                        // Root granted
                        mReverseExitListener = new ReverseExitListener() {
                            @Override
                            public void OnReverseOff() {
                                String command = "input keyevent " + String.valueOf(KeyEvent.KEYCODE_BACK);
                                RootManager.runCommand(command);
                            }
                        };
                    } else {
                        mReverseExitListener = new ReverseExitListener() {
                            @Override
                            public void OnReverseOff() {
                                // Neither have signature nor root permissions, do nothing
                            }
                        };
                    }
                } else {
                    Timber.e("Error setting camera app");
                }
                break;

            default:        // Unknown command
                Timber.wtf("Unknown Camera Setting, this shouldnt happen");
        }
    }


    private boolean isAutoBrightnessOn() {
        int mode = -1;
        try {
            mode = Settings.System.getInt(mContext.getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS_MODE);
        } catch (Exception e) {
            Timber.e(e);
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
            Timber.e(e);
        }
    }

    private void launchBrightnessChangeActivity(int brightness) {
        Intent brightActivityIntent = new Intent(mContext,
                BrightnessChangeActivity.class);
        brightActivityIntent.putExtra("Brightness", brightness);
        brightActivityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
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
                Timber.v("Volume Up Command recd");
                do {
                    mAudioManger.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                            AudioManager.ADJUST_RAISE, volumeUiFlag);
                    try {
                        Thread.sleep(VOLUME_KEY_DELAY);
                    } catch (InterruptedException e) {
                        Timber.w(e);
                    }

                } while (mIsHoldingBtn.get());
            }
        });
        mActions.put("Volume Down", new ActionRunnable() {
            @Override
            public void run() {
                Timber.v("Volume Down Command recd");
                do {
                    mAudioManger.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                            AudioManager.ADJUST_LOWER, volumeUiFlag);
                    try {
                        Thread.sleep(VOLUME_KEY_DELAY);
                    } catch (InterruptedException e) {
                        Timber.w(e);
                    }
                } while (mIsHoldingBtn.get());
            }
        });
        mActions.put("Mute", new ActionRunnable() {
            @Override
            public void run() {
                Timber.v("Send Mute Command recd");

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
                Timber.v( "Media, Play/Pause Command recd");

                Intent mediaIntent = new Intent(Intent.ACTION_MEDIA_BUTTON);
                mediaIntent.putExtra(Intent.EXTRA_KEY_EVENT,
                        new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE));
                mContext.sendBroadcast(mediaIntent);

                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Timber.w(e);
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
        mActions.put("Reverse", new ActionRunnable() {
            @Override
            public void run() {
                // launch user set camera activity.
                if ((boolean)data) {
                    if (mCameraIntent != null) {
                        mCameraIsOn = true;
                        mContext.startActivity(mCameraIntent);
                        Timber.v("Launch Camera Command recd");
                    } else {
                        Timber.i("Camera app not set");
                    }
                } else {
                    if (mReverseExitListener != null) {
                        mCameraIsOn = false;
                        mReverseExitListener.OnReverseOff();
                        Timber.v("Close Camera Command recd");
                    }
                }
            }
        });
        mActions.put("Toggle Camera", new ActionRunnable() {
            @Override
            public void run() {
                if (mCameraIntent == null) {
                    // TODO: Show Invisible toast
                    Timber.i("Camera app not set");
                    return;
                }

                if (!mCameraIsOn) {
                    mCameraIsOn = true;
                    mContext.startActivity(mCameraIntent);
                } else if (mReverseExitListener != null) {
                    mCameraIsOn = false;
                    mReverseExitListener.OnReverseOff();
                }
                Timber.v("Toggle Camera Command recd");
            }
        });
        mActions.put("Dimmer", new ActionRunnable() {
            @Override
            public void run() {
                if ((boolean) data) {
                    mBrightnessControl.DimmerOn();
                } else {
                    mBrightnessControl.DimmerOff();
                }
            }
        });

        mActions.put("Dimmer Level", new ActionRunnable() {
            @Override
            public void run() {
                if (mDimmerMode == DimmerMode.ANALOG) {
                    mBrightnessControl.DimmerChange((int) data);
                } else {
                    Timber.w("Dimmer is set to digital mode, not expecting analog data");
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
                Timber.v("Sending Application Intent");
                Intent appIntent = mContext.getPackageManager().getLaunchIntentForPackage((String)this.data);
                if (appIntent != null) {
                    appIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    mContext.startActivity(appIntent);
                } else {
                    Timber.w("Invalid application, Cannot Launch");
                }
            }
        });

        mActions.put("Tasker", new ActionRunnable() {
            @Override
            public void run() {
                // TODO: currently using tasker's external access api to execute.  Can create
                //       Locale/Tasker plugin that should also work with macrodroid.
                Timber.v("Execute Tasker Task");
                if ( TaskerIntent.testStatus(mContext).equals(TaskerIntent.Status.OK) ) {
                    TaskerIntent i = new TaskerIntent((String)this.data);
                    mContext.sendBroadcast( i );
                }

            }
        });

        mActions.put("Toggle Audio Source", new ActionRunnable() {
            @Override
            public void run() {
                switch (mCurrentSource) {
                    case HD_RADIO:
                        mCurrentSource = AudioSource.AUX;
                        mMcuControlInterface.sendMcuCommand(McuOutputCommand.AUDIO_SOURCE_AUX,
                                null);
                        break;
                    case AUX:
                        mCurrentSource = AudioSource.HD_RADIO;
                        mMcuControlInterface.sendMcuCommand(McuOutputCommand.AUDIO_SOURCE_HD,
                                null);
                        break;
                }
            }
        });

        mActions.put("Set Audio Source", new ActionRunnable() {
            @Override
            public void run() {
                if (!data.equals(mCurrentSource.toString())) {
                    switch ((String)data) {
                        case "HD_RADIO":
                            mCurrentSource = AudioSource.HD_RADIO;
                            mMcuControlInterface.sendMcuCommand(McuOutputCommand.AUDIO_SOURCE_AUX,
                                    null);
                        case "AUX":
                            mCurrentSource = AudioSource.AUX;
                            mMcuControlInterface.sendMcuCommand(McuOutputCommand.AUDIO_SOURCE_HD,
                                    null);
                            break;
                        default:
                            Timber.w("Unknown Audio Source: %s", data);
                    }
                }
            }
        });

        // TODO:  add user created custom commands to send to MCU?
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
                        Timber.w(e);
                    }

                    // send key up event
                    mediaIntent = new Intent(Intent.ACTION_MEDIA_BUTTON);
                    mediaIntent.putExtra(Intent.EXTRA_KEY_EVENT,
                            new KeyEvent(KeyEvent.ACTION_UP, keycode));
                    mContext.sendBroadcast(mediaIntent);

                    if (mIsHoldingBtn.get()) {
                        try {
                            Thread.sleep(MEDIA_KEY_DELAY);

                        } catch (InterruptedException e) {
                            Timber.w(e);
                        }
                    }

                } while (mIsHoldingBtn.get());
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
                        Timber.w(e);
                    }

                } while (mIsHoldingBtn.get());

                // send key up event
                mediaIntent = new Intent(Intent.ACTION_MEDIA_BUTTON);
                mediaIntent.putExtra(Intent.EXTRA_KEY_EVENT,
                        new KeyEvent(KeyEvent.ACTION_UP, keycode));
                mContext.sendBroadcast(mediaIntent);

            }
        };
    }

    private ActionRunnable getButtonAction(Object data, boolean isClickAction) {
        int id = (int) data;

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

                if (type.equals("Application") || type.equals("Tasker")
                        || type.equals("Set Audio Source")) {
                    ActionRunnable actionRunnable = mActions.get(type);
                    actionRunnable.setData(action);
                    return actionRunnable;
                } else {
                    return mActions.get(action);
                }
            }
        }

        Timber.i("Button is not mapped for value: %d", id);
        return null;
    }

    public void executeAction(ControllerMessage message) {

        ActionRunnable action = null;
        switch (message.command) {
            case STARTED:
                // connection established, initialize
                Timber.v("MCU Started successfully");

                if (mDimmerMode == DimmerMode.ANALOG) {
                    mMcuControlInterface.sendMcuCommand(McuOutputCommand.SET_DIMMER_ANALOG, null);
                    Timber.v("Initial Dimmer Mode set to ANALOG");
                } else {
                    mMcuControlInterface.sendMcuCommand( McuOutputCommand.SET_DIMMER_DIGITAL, null);
                    Timber.v("Initial Dimmer Mode set to DIGITAL");
                }

                // set the current audio source for external devices
                if (mCurrentSource == AudioSource.AUX) {
                    mMcuControlInterface.sendMcuCommand(McuOutputCommand.AUDIO_SOURCE_AUX, null);
                    Timber.v("Initial Audio Source set to AUX");
                } else {
                    mMcuControlInterface.sendMcuCommand(McuOutputCommand.AUDIO_SOURCE_HD, null);
                    Timber.v("Initial AUDIO SOURCE set to HD RADIO");
                }

                // invoke Mcu OnStarted Callback with Id
                mMcuEvents.OnStarted((String)message.data);
                break;
            case IDENT:
                mMcuEvents.OnIdReceived((String)message.data);
                break;
            case CLICK:
                action = getButtonAction(message.data, true);
                if (action != null) {
                    mIsHoldingBtn.set(false);
                }
                break;
            case HOLD:
                action = getButtonAction(message.data, false);
                if (action != null) {
                    mIsHoldingBtn.set(true);
                }
                break;
            case RELEASE:
                mIsHoldingBtn.set(false);
                break;
            case DIMMER:
                action = mActions.get("Dimmer");
                action.setData(message.data);
                break;
            case DIMMER_LEVEL:
                action = mActions.get("Dimmer Level");
                action.setData(message.data);
                break;
            case REVERSE:
                action = mActions.get("Reverse");
                action.setData(message.data);
                break;
            case RADIO_STATUS:
                mMcuEvents.OnRadioStatusReceived((boolean)message.data);
                break;
            case RADIO_DATA:
                mMcuEvents.OnRadioDataReceived((byte[])message.data);
                break;
            case CUSTOM:
                if (mBroadcastCustomCommands) {
                    byte[] custom = (byte[]) message.data;

                    // invalid command received
                    if (custom == null || custom.length == 0) {
                        return;
                    }

                    Timber.d("Broacasting custom command: %s", message.command.toString());

                    // First byte is the command
                    Intent customIntent = new Intent(mContext.getString(R.string.ACTION_CUSTOM_DATA_RECIEVED));
                    customIntent.putExtra(mContext.getString(R.string.EXTRA_COMMAND), custom[0]);

                    // If there is extra data add that extra as well
                    if (custom.length > 1) {
                        customIntent.putExtra(mContext.getString(R.string.EXTRA_DATA),
                                Arrays.copyOfRange(custom, 1, (custom.length -1)));
                    }

                    mContext.sendBroadcast(customIntent);
                }
                break;
            default:
                Timber.i("Unknown command Received: %s", message.command.toString());
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
                .putString("audio_pref_key_current_source", mCurrentSource.toString())
                .apply();

    }

}
