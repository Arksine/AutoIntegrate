package com.arksine.autointegrate.activities;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.arksine.autointegrate.MainService;
import com.arksine.autointegrate.R;
import com.arksine.autointegrate.dialogs.RadioSettingsDialog;
import com.arksine.autointegrate.interfaces.RadioControlCallback;
import com.arksine.autointegrate.interfaces.RadioControlInterface;
import com.arksine.autointegrate.radio.HDRadioValues;
import com.arksine.autointegrate.radio.RadioController;
import com.arksine.autointegrate.radio.RadioKey;
import com.arksine.autointegrate.radio.TextStreamAnimator;
import com.arksine.autointegrate.utilities.BackgroundThreadFactory;
import com.arksine.autointegrate.radio.TextSwapAnimator;
import com.arksine.autointegrate.utilities.DLog;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// TODO: is signal strength 0-3000?
public class RadioActivity extends AppCompatActivity {
    private static final String TAG = RadioActivity.class.getSimpleName();

    private SharedPreferences mRadioActivityPrefs;
    private ExecutorService EXECUTOR = Executors.newCachedThreadPool(new BackgroundThreadFactory());
    private Handler mHandler;
    private boolean mBound = false;
    private RadioControlInterface mRadioInterface = null;

    private HDRadioValues mRadioValues;


    private volatile boolean mIsPoweredOn = false;

    // TODO: probably don't need the 2 below vars, as they are already stored in HDRadioValues
    private volatile boolean mRdsEnabled = false;
    private volatile boolean mHdActive = false;

    private volatile boolean mIsRequestingSignal = false;
    
    private RadioSettingsDialog mRadioSettingsDialog;

    private TextSwapAnimator mTextSwapAnimator;
    private TextStreamAnimator mTextStreamAnimator;
    private int mFrequency = 879;
    private String mBand = "FM";

    private TextView mHdStatusText;
    private TextView mRadioFreqText;
    private TextView mRadioBandText;
    private TextView mRadioInfoText;
    private TextView mStreamingInfoText;
    private ToggleButton mPowerButton;
    private ToggleButton mMuteButton;
    private ToggleButton mSeekAllButton;
    private ToggleButton mBandButton;

    private RadioControlCallback mRadioCallback = new RadioControlCallback() {
        @Override
        public void OnRadioDataReceived(final RadioKey.Command key, final Object value) throws RemoteException {
            mRadioValues.setHdValue(key, value);

            RadioMessage radioMessage = new RadioMessage();
            radioMessage.key = key;
            radioMessage.value = value;
            Message msg = mHandler.obtainMessage();
            msg.obj = radioMessage;
            mHandler.sendMessage(msg);

        }

        @Override
        public void OnError() throws RemoteException {
            mRadioInterface = null;
            if (mIsPoweredOn) {
                runOnUiThread(mClearVarsRunnable);
            }

            // TODO: Display message saying there was a connection error and close app
        }

        @Override
        public void OnDisconnect() throws RemoteException {
            mRadioInterface = null;
            if (mIsPoweredOn) {
                runOnUiThread(mClearVarsRunnable);
            }
            // TODO: Display message saying that serial port was disconnected and close app
        }

        @Override
        public void OnPowerOn() throws RemoteException {
            mIsPoweredOn = true;

            //bset volume, bass, treble, tune
            EXECUTOR.execute(new Runnable() {
                @Override
                public void run() {
                    // TODO: something funny is happening here, might need more time between sending items to radio.
                    // It is getting set but not taking affect (seemed to work fine before)
                    mRadioInterface.setVolume(mRadioSettingsDialog
                            .getSeekBarProgress(RadioKey.Command.VOLUME));
                    mRadioInterface.setBass(mRadioSettingsDialog
                            .getSeekBarProgress(RadioKey.Command.BASS));
                    mRadioInterface.setTreble(mRadioSettingsDialog
                            .getSeekBarProgress(RadioKey.Command.TREBLE));

                    RadioController.TuneInfo tuneInfo = (RadioController.TuneInfo)mRadioValues
                            .getHdValue(RadioKey.Command.TUNE);
                    int subchannel = (int)mRadioValues.getHdValue(RadioKey.Command.HD_SUBCHANNEL);


                    mRadioInterface.tune(tuneInfo.band, tuneInfo.frequency, subchannel);
                }
            });

            if (mRadioInterface.getPowerStatus() && !mIsRequestingSignal) {
                EXECUTOR.execute(mRequestSignalRunnable);
            }
        }

        @Override
        public void OnPowerOff() throws RemoteException {
            runOnUiThread(mClearVarsRunnable);
        }
    };

    private ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            final MainService.LocalBinder binder = (MainService.LocalBinder) iBinder;

            binder.registerCallback(mRadioCallback);
            mRadioInterface = binder.getRadioInterface();

            if (mRadioInterface == null) {
                Log.i(TAG, "Radio Interface not available");
                Toast.makeText(RadioActivity.this, "Radio Interface not available",
                        Toast.LENGTH_SHORT).show();
            } else {
                EXECUTOR.execute(getInitialSettings);
            }

            mBound = true;

        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBound = false;
        }
    };

    /**
     * Check to see if the radio was powered on prior to binding to the service.
     */
    private Runnable getInitialSettings = new Runnable() {
        @Override
        public void run() {
            if (mRadioInterface != null) {
                // TODO: When I move hdvalues to its own class (or to this activity) I should
                // get persisted settings from there. I should request power status from the radio, and set
                // mute, volume, bass from persisted settings.

                // get power status from the interface, because we are unable to receive the
                // power command from the radio prior to binding to the service
                mIsPoweredOn = mRadioInterface.getPowerStatus();

                // get persistent seekall value
                final boolean seekAll = mRadioValues.getSeekAll();
                mRadioInterface.setSeekAll(seekAll);

                // TODO: I should mute persist?

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        // set views here
                        mPowerButton.setChecked(mIsPoweredOn);
                        mSeekAllButton.setChecked(seekAll);

                    }
                });

                // Request the status of a important radio values if the radio is already powered on
                if (mIsPoweredOn) {
                    mRadioInterface.requestUpdate(RadioKey.Command.MUTE);
                    mRadioInterface.requestUpdate(RadioKey.Command.VOLUME);
                    mRadioInterface.requestUpdate(RadioKey.Command.BASS);
                    mRadioInterface.requestUpdate(RadioKey.Command.TREBLE);
                    mRadioInterface.requestUpdate(RadioKey.Command.HD_ACTIVE);
                    mRadioInterface.requestUpdate(RadioKey.Command.RDS_ENABLED);


                    // Ask the radio to send fresh tune info
                    mRadioInterface.requestUpdate(RadioKey.Command.TUNE);
                    mRadioInterface.requestUpdate(RadioKey.Command.HD_SUBCHANNEL);

                    if (!mIsRequestingSignal) {
                        EXECUTOR.execute(mRequestSignalRunnable);
                    }
                } else {
                    runOnUiThread(mClearVarsRunnable);
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_radio);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        mRadioValues = new HDRadioValues(this);

        mHandler = new Handler(Looper.getMainLooper(), mHandlerCallback);
        mRadioActivityPrefs = this.getSharedPreferences("radio_activity_preferences",
                Context.MODE_PRIVATE);

        initViews();
        initAudioSettingsDialog();

        mTextSwapAnimator = new TextSwapAnimator(mRadioInfoText);
        mTextStreamAnimator = new TextStreamAnimator(mStreamingInfoText);
    }

    private void initViews() {
        mHdStatusText = (TextView)findViewById(R.id.txt_radio_hd_status);
        mRadioFreqText = (TextView)findViewById(R.id.txt_radio_frequency);
        mRadioBandText = (TextView)findViewById(R.id.txt_radio_band);
        mRadioInfoText = (TextView)findViewById(R.id.txt_radio_info);
        mStreamingInfoText = (TextView)findViewById(R.id.txt_streaming_info);
        mPowerButton = (ToggleButton)findViewById(R.id.btn_radio_power);
        mMuteButton = (ToggleButton)findViewById(R.id.btn_radio_mute);
        mSeekAllButton = (ToggleButton)findViewById(R.id.btn_radio_seekall);
        mBandButton = (ToggleButton)findViewById(R.id.btn_radio_band);
        ImageButton mTuneUpButton = (ImageButton) findViewById(R.id.btn_radio_tune_up);
        ImageButton mTuneDownButton = (ImageButton) findViewById(R.id.btn_radio_tune_down);
        ImageButton mSeekUpButton = (ImageButton) findViewById(R.id.btn_radio_seek_up);
        ImageButton mSeekDownButton = (ImageButton) findViewById(R.id.btn_radio_seek_down);

        Button audioSettings = (Button) findViewById(R.id.btn_radio_settings);
        Button volumeUp = (Button) findViewById(R.id.btn_vol_up);
        Button volumeDown = (Button) findViewById(R.id.btn_vol_down);

        mPowerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                DLog.v(TAG, "Power Clicked, interface is: " + (mRadioInterface != null));
                if (mRadioInterface != null) {
                    final boolean status = ((ToggleButton)view).isChecked();
                    EXECUTOR.execute(new Runnable() {
                        @Override
                        public void run() {
                            DLog.v(TAG, "Set power: " + status);
                            mRadioInterface.togglePower(status);
                        }
                    });

                }
            }
        });

        mMuteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mRadioInterface != null) {
                    final boolean status = ((ToggleButton)view).isChecked();
                    EXECUTOR.execute(new Runnable() {
                        @Override
                        public void run() {
                            DLog.v(TAG, "Set mute: " + status);
                            mRadioInterface.toggleMute(status);
                        }
                    });
                }
            }
        });

        mSeekAllButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mRadioInterface != null) {
                    final boolean status = ((ToggleButton) view).isChecked();
                    EXECUTOR.execute(new Runnable() {
                        @Override
                        public void run() {
                            mRadioInterface.setSeekAll(status);
                        }
                    });
                }
            }
        });

        mBandButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mRadioInterface != null) {
                    final boolean band = ((ToggleButton) view).isChecked();
                    DLog.v(TAG, "Switch Band to FM: " + band);
                    if (band) {
                        EXECUTOR.execute(new Runnable() {
                            @Override
                            public void run() {
                                // fm
                                final int frequency = mRadioActivityPrefs.getInt("pref_key_stored_fm_freq", 879);
                                final int subChannel = mRadioActivityPrefs.getInt("pref_key_stored_fm_subch", 0);

                                //save am channel
                                int prevFreq = ((RadioController.TuneInfo)mRadioValues
                                        .getHdValue(RadioKey.Command.TUNE)).frequency;
                                int prevSubCh = mHdActive ? (int)mRadioValues
                                        .getHdValue(RadioKey.Command.HD_SUBCHANNEL) : 0;
                                mRadioActivityPrefs.edit().putInt("pref_key_stored_am_freq", prevFreq)
                                        .putInt("pref_key_stored_am_subch", prevSubCh)
                                        .apply();


                                mRadioInterface.tune(RadioKey.Band.FM, frequency, subChannel);
                            }
                        });
                    } else {
                        // am
                        EXECUTOR.execute(new Runnable() {
                            @Override
                            public void run() {
                                final int frequency = mRadioActivityPrefs.getInt("pref_key_stored_am_freq", 900);
                                final int subChannel = mRadioActivityPrefs.getInt("pref_key_stored_am_subch", 0);

                                //save fm channel
                                int prevFreq = ((RadioController.TuneInfo)mRadioValues
                                        .getHdValue(RadioKey.Command.TUNE)).frequency;
                                int prevSubCh = mHdActive ? (int)mRadioValues
                                        .getHdValue(RadioKey.Command.HD_SUBCHANNEL) : 0;

                                mRadioActivityPrefs.edit().putInt("pref_key_stored_fm_freq", prevFreq)
                                        .putInt("pref_key_stored_fm_subch", prevSubCh)
                                        .apply();
                                mRadioInterface.tune(RadioKey.Band.AM, frequency, subChannel);
                            }
                        });
                    }
                }
            }
        });

        mTuneUpButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mRadioInterface != null) {
                    EXECUTOR.execute(new Runnable() {
                        @Override
                        public void run() {
                            mRadioInterface.tuneUp();
                        }
                    });
                }
            }
        });

        mTuneDownButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mRadioInterface != null) {
                    EXECUTOR.execute(new Runnable() {
                        @Override
                        public void run() {
                            mRadioInterface.tuneDown();
                        }
                    });
                }
            }
        });

        mSeekUpButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mRadioInterface != null) {
                    EXECUTOR.execute(new Runnable() {
                        @Override
                        public void run() {
                            mRadioInterface.seekUp();
                        }
                    });
                }
            }
        });

        mSeekDownButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mRadioInterface != null) {
                    EXECUTOR.execute(new Runnable() {
                        @Override
                        public void run() {
                            mRadioInterface.seekDown();
                        }
                    });
                }
            }
        });

        audioSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mRadioSettingsDialog.showDialog();
            }
        });

        volumeUp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mRadioInterface != null) {
                    EXECUTOR.execute(new Runnable() {
                        @Override
                        public void run() {
                            mRadioInterface.setVolumeUp();
                        }
                    });
                }
            }
        });

        volumeDown.setOnClickListener((new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mRadioInterface != null) {
                    EXECUTOR.execute(new Runnable() {
                        @Override
                        public void run() {
                            mRadioInterface.setVolumeDown();
                        }
                    });
                }
            }
        }));
    }

    private void initAudioSettingsDialog() {

        RadioSettingsDialog.SeekBarListener listener = new RadioSettingsDialog.SeekBarListener() {
            @Override
            public void OnSeekBarChanged(RadioKey.Command key, int value) {
                switch (key) {
                    case VOLUME:
                        if (mRadioInterface != null) {
                            final int vol = value;
                            EXECUTOR.execute(new Runnable() {
                                @Override
                                public void run() {
                                    mRadioInterface.setVolume(vol);
                                }
                            });

                        }
                        break;
                    case BASS:
                        if (mRadioInterface != null) {
                            final int bass = value;
                            EXECUTOR.execute(new Runnable() {
                                @Override
                                public void run() {
                                    mRadioInterface.setBass(bass);
                                }
                            });

                        }
                        break;
                    case TREBLE:
                        if (mRadioInterface != null) {
                            final int treble = value;
                            EXECUTOR.execute(new Runnable() {
                                @Override
                                public void run() {
                                    mRadioInterface.setTreble(treble);
                                }
                            });
                        }
                        break;
                    default:
                        DLog.v(TAG, "Invalid command, cannot set apply setting");
                }
            }
        };

        mRadioSettingsDialog = new RadioSettingsDialog(this, listener);

        // Set initial progress of bars
        final int volume = (int)mRadioValues.getHdValue(RadioKey.Command.VOLUME);
        final int bass =  (int)mRadioValues.getHdValue(RadioKey.Command.BASS);
        final int treble = (int)mRadioValues.getHdValue(RadioKey.Command.VOLUME);

        mRadioSettingsDialog.setSeekBarProgress(RadioKey.Command.VOLUME, volume);
        mRadioSettingsDialog.setSeekBarProgress(RadioKey.Command.BASS, bass);
        mRadioSettingsDialog.setSeekBarProgress(RadioKey.Command.TREBLE, treble);
    }


    @Override
    protected void onResume() {
        super.onResume();

        final ScrollView infoScrollView = (ScrollView)mRadioInfoText.getParent();
        ViewTreeObserver viewTreeObserver = infoScrollView.getViewTreeObserver();
        if (viewTreeObserver.isAlive()) {

            viewTreeObserver.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    infoScrollView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    mTextSwapAnimator.setScrollViewWidth(infoScrollView.getWidth());
                    mTextStreamAnimator.setScrollViewWidth((infoScrollView.getWidth()));
                    DLog.v(TAG, "View Tree Observer set scrollview width to : " +
                        infoScrollView.getWidth());
                }
            });
        } else {
            // use display metrics
            DisplayMetrics displayMetrics = new DisplayMetrics();
            getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
            int padding = Math.round(2 * getResources().getDimension(R.dimen.activity_horizontal_margin));
            int scrollViewWidth = displayMetrics.widthPixels - padding;
            mTextSwapAnimator.setScrollViewWidth(scrollViewWidth);
            mTextStreamAnimator.setScrollViewWidth(scrollViewWidth);
            DLog.v(TAG, "Display Metrics set width to:  " + scrollViewWidth);
        }

        Intent intent = new Intent(this, MainService.class);
        bindService(intent, mServiceConnection, Context.BIND_AUTO_CREATE);

    }

    @Override
    protected void onPause() {
        super.onPause();
        mRadioValues.savePersistentPrefs(this);
        mTextSwapAnimator.stopAnimation();

        if (mBound) {
            unbindService(mServiceConnection);
            mBound = false;        }

        // TODO: I could assign a future to the signal request runnable and shut it down
        mIsRequestingSignal = false;
    }

    class RadioMessage {
        RadioKey.Command key;
        Object value;
    }

    private Handler.Callback mHandlerCallback = new Handler.Callback() {
        @Override
        public boolean handleMessage(Message message) {
            //TODO: Much of this will change when I implement animated textview.  This is for initial testing

            // set hd value

            RadioMessage radioMessage = (RadioMessage)message.obj;
            String tmpFreq;
            switch (radioMessage.key) {
                case POWER:
                    // this variable isn't consistent. Power status doesn't seem to work
                    // when the radio is off (even when the serial port is connected)
                    break;
                case MUTE:
                    mMuteButton.setChecked((boolean)radioMessage.value);
                    break;
                case VOLUME:
                    mRadioSettingsDialog.setSeekBarProgress(RadioKey.Command.VOLUME, 
                            (int)radioMessage.value);
                    break;
                case BASS:
                    mRadioSettingsDialog.setSeekBarProgress(RadioKey.Command.BASS,
                            (int)radioMessage.value);
                    break;
                case TREBLE:
                    mRadioSettingsDialog.setSeekBarProgress(RadioKey.Command.TREBLE,
                            (int)radioMessage.value);
                    break;
                case HD_SUBCHANNEL:
                    if (mHdActive && (int)radioMessage.value > 0) {
                        // TODO: I think this is causing an error.  I am sending a null value
                        // To mTextSwapAnimator
                        String newFreq;
                        if (mBand.equals("FM")) {
                            newFreq = String.format(Locale.US, "%1$.1f-%2$d", (float)mFrequency/10, (int)radioMessage.value);
                        } else if (mBand.equals("AM")) {
                            newFreq = String.format(Locale.US, "%1$d-%2$d", mFrequency, (int)radioMessage.value);
                        } else {
                            // Unknown Frequency
                            break;
                        }


                        mRadioFreqText.setText(newFreq);
                        mTextSwapAnimator.setTextItem(RadioKey.Command.TUNE, newFreq + " " + mBand);

                        // Update info text with artist and title from current subchannel
                        mTextSwapAnimator.setTextItem(RadioKey.Command.HD_TITLE,
                                (String)mRadioValues.getHdValue(RadioKey.Command.HD_TITLE));
                        mTextSwapAnimator.setTextItem(RadioKey.Command.HD_ARTIST,
                                (String)mRadioValues.getHdValue(RadioKey.Command.HD_ARTIST));

                    } else {
                        if (mBand.equals("FM")) {
                            tmpFreq = String.format(Locale.US, "%1$.1f", (float) mFrequency / 10);
                        } else if (mBand.equals("AM")){
                            tmpFreq = String.valueOf(mFrequency);
                        } else {
                            // unknown freqency
                            break;
                        }
                        mRadioFreqText.setText(tmpFreq);
                        mTextSwapAnimator.setTextItem(RadioKey.Command.TUNE, tmpFreq + " " + mBand);

                    }
                    break;
                case TUNE:
                    mHandler.removeCallbacks(mPostTuneRunnable);
                    RadioController.TuneInfo info = (RadioController.TuneInfo) radioMessage.value;
                    mFrequency = info.frequency;
                    if (info.band == RadioKey.Band.FM) {
                        mBand = "FM";
                        tmpFreq = String.format(Locale.US, "%1$.1f", (float)mFrequency/10);
                        mBandButton.setChecked(true);
                    } else {
                        mBand = "AM";
                        tmpFreq = String.valueOf(info.frequency);
                        mBandButton.setChecked(false);
                    }
                    mTextStreamAnimator.clear();
                    mTextSwapAnimator.setTextItem(RadioKey.Command.TUNE, tmpFreq + " " + mBand);
                    mTextSwapAnimator.resetAnimator();
                    mRdsEnabled = false;
                    mHdActive = false;

                    mHdStatusText.setText("");
                    mRadioFreqText.setText(tmpFreq);
                    mRadioBandText.setText(info.band.toString());
                    mHandler.postDelayed(mPostTuneRunnable, 2000);
                    break;
                case SEEK:
                    RadioController.TuneInfo seekInfo = (RadioController.TuneInfo) radioMessage.value;
                    if (seekInfo.band == RadioKey.Band.FM) {
                        tmpFreq = String.format(Locale.US, "%1$.1f",
                                seekInfo.frequency/10f);
                    } else {
                        tmpFreq = String.valueOf(seekInfo.frequency);
                    }
                    mRadioFreqText.setText(tmpFreq);
                    break;
                case HD_ACTIVE:

                    // TODO: Show HD Icon if true, hide if false, the textview below is temporary
                    mHdActive = (boolean)radioMessage.value;
                    if (mHdActive) {
                        mHdStatusText.setText("HD");
                    } else {
                        mHdStatusText.setText("");
                    }
                    break;
                case HD_STREAM_LOCK:

                    break;
                case HD_TITLE:
                case HD_ARTIST:
                case HD_CALLSIGN:
                case RDS_RADIO_TEXT:
                case RDS_GENRE:
                    mTextSwapAnimator.setTextItem(radioMessage.key,
                            (String) radioMessage.value);
                    break;
                case RDS_PROGRAM_SERVICE:
                   mTextStreamAnimator.addStreamingText((String)radioMessage.value);
                    break;
                case RDS_ENABLED:
                    //TODO: show rds icon if true, hide if false
                    mRdsEnabled = (boolean)radioMessage.value;
                    if (mRdsEnabled) {
                        mHdStatusText.setText("RDS");
                    } else {
                        mHdStatusText.setText("");
                    }
                    break;
                default:
            }

            return true;
        }
    };

    private Runnable mPostTuneRunnable = new Runnable() {
        @Override
        public void run() {
            mRadioInterface.requestUpdate(RadioKey.Command.RDS_ENABLED);
        }
    };

    private Runnable mClearVarsRunnable = new Runnable() {
        @Override
        public void run() {
            // Clear text views, set infoview frequency to PowerOff
            mTextStreamAnimator.clear();
            mTextSwapAnimator.setTextItem(RadioKey.Command.TUNE, "Power Off");
            mTextSwapAnimator.resetAnimator();
            mHdStatusText.setText("");
            mRadioFreqText.setText("");
            mRadioBandText.setText("");
            mRdsEnabled = false;
            mHdActive = false;
            mIsPoweredOn = false;
        }
    };

    private Runnable mRequestSignalRunnable = new Runnable() {
        @Override
        public void run() {
            if (mIsRequestingSignal){
                // Don't execute no runnable if already requesting signal
                return;
            }
            mIsRequestingSignal = true;
            while (mRadioInterface != null && mIsPoweredOn && mIsRequestingSignal) {
                if (mHdActive) {
                    mRadioInterface.requestUpdate(RadioKey.Command.HD_SIGNAL_STRENGH);
                } else {
                    mRadioInterface.requestUpdate(RadioKey.Command.SIGNAL_STRENGTH);
                }

                try {
                    Thread.sleep(10000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            mIsRequestingSignal = false;
        }
    };



}
