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
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.arksine.autointegrate.MainService;
import com.arksine.autointegrate.R;
import com.arksine.autointegrate.dialogs.RadioSettingsDialog;
import com.arksine.autointegrate.interfaces.RadioControlCallback;
import com.arksine.autointegrate.interfaces.RadioControlInterface;
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

    private volatile boolean mRdsEnabled = false;
    private volatile boolean mHdActive = false;
    private volatile boolean mIsRequestingSignal = false;
    
    private RadioSettingsDialog mRadioSettingsDialog;

    private TextSwapAnimator mTextSwapAnimator;
    private TextStreamAnimator mTextStreamAnimator;
    private String mFrequency = "87.9";
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
        public void OnRadioDataReceived(RadioKey.Command key, Object value) throws RemoteException {
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
            // TODO: send toast that there was an error?
            runOnUiThread(mPowerOffRunnable);
        }

        @Override
        public void OnPowerOff() throws RemoteException {
            runOnUiThread(mPowerOffRunnable);
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

    private Runnable getInitialSettings = new Runnable() {
        @Override
        public void run() {
            if (mRadioInterface != null) {
                final boolean power = mRadioInterface.getPowerStatus();
                final boolean mute = (boolean)mRadioInterface.getHdValue(RadioKey.Command.MUTE);
                final boolean seekAll = mRadioInterface.getSeekAll();
                mHdActive = (boolean)mRadioInterface.getHdValue(RadioKey.Command.HD_ACTIVE);
                mRdsEnabled = (boolean)mRadioInterface.getHdValue(RadioKey.Command.RDS_ENABLED);
                final int volume = (int)mRadioInterface.getHdValue(RadioKey.Command.VOLUME);
                final int bass =  (int)mRadioInterface.getHdValue(RadioKey.Command.BASS);
                final int treble = (int)mRadioInterface.getHdValue(RadioKey.Command.VOLUME);

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        // set views here
                        mPowerButton.setChecked(power);
                        mMuteButton.setChecked(mute);
                        mSeekAllButton.setChecked(seekAll);
                        mRadioSettingsDialog.setSeekBarProgress(RadioKey.Command.VOLUME, volume);
                        mRadioSettingsDialog.setSeekBarProgress(RadioKey.Command.BASS, bass);
                        mRadioSettingsDialog.setSeekBarProgress(RadioKey.Command.TREBLE, treble);
                    }
                });

                if (power) {
                    // Ask the radio to send fresh tune info
                    mRadioInterface.requestUpdate(RadioKey.Command.TUNE);

                    if (mHdActive) {
                        mRadioInterface.requestUpdate(RadioKey.Command.HD_SUBCHANNEL);  // subchannel updates artist and title
                        mRadioInterface.requestUpdate(RadioKey.Command.HD_CALLSIGN);
                    } else if (mRdsEnabled) {
                        mRadioInterface.requestUpdate(RadioKey.Command.RDS_RADIO_TEXT);
                        mRadioInterface.requestUpdate(RadioKey.Command.RDS_GENRE);
                        mRadioInterface.requestUpdate(RadioKey.Command.RDS_PROGRAM_SERVICE);
                    }

                    if (!mIsRequestingSignal) {
                        EXECUTOR.execute(mRequestSignalRunnable);
                    }
                } else {
                    runOnUiThread(mPowerOffRunnable);
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

        mHandler = new Handler(Looper.getMainLooper(), mHandlerCallback);
        mRadioActivityPrefs = this.getSharedPreferences("radio_activity_preferences",
                Context.MODE_PRIVATE);

        initViews();
        
        RadioSettingsDialog.SeekBarListener listener = new RadioSettingsDialog.SeekBarListener() {
            @Override
            public void OnSeekBarChanged(RadioKey.Command key, int value) {
                switch (key) {
                    case VOLUME:
                        if (mRadioInterface != null) {
                            mRadioInterface.setVolume(value);
                        }
                        break;
                    case BASS:
                        if (mRadioInterface != null) {
                            mRadioInterface.setBass(value);
                        }
                        break;
                    case TREBLE:
                        if (mRadioInterface != null) {
                            mRadioInterface.setTreble(value);
                        }
                        break;
                    default:
                        DLog.v(TAG, "Invalid command, cannot set apply setting");
                }
            }
        };
        
        mRadioSettingsDialog = new RadioSettingsDialog(this, listener);

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
        Button mTuneUpButton = (Button) findViewById(R.id.btn_radio_tune_up);
        Button mTuneDownButton = (Button) findViewById(R.id.btn_radio_tune_down);
        Button mSeekUpButton = (Button) findViewById(R.id.btn_radio_seek_up);
        Button mSeekDownButton = (Button) findViewById(R.id.btn_radio_seek_down);
        Button audioSettings = (Button) findViewById(R.id.btn_radio_settings);

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
                                int prevFreq = ((RadioController.TuneInfo)mRadioInterface
                                        .getHdValue(RadioKey.Command.TUNE)).frequency;
                                mRadioActivityPrefs.edit().putInt("pref_key_stored_am_freq", prevFreq).apply();

                                mRadioInterface.tune(RadioKey.Band.FM, frequency, subChannel);
                            }
                        });
                    } else {
                        // am
                        EXECUTOR.execute(new Runnable() {
                            @Override
                            public void run() {
                                final int frequency = mRadioActivityPrefs.getInt("pref_key_stored_am_freq", 900);

                                //save fm channel
                                int prevFreq = ((RadioController.TuneInfo)mRadioInterface
                                        .getHdValue(RadioKey.Command.TUNE)).frequency;
                                int prevSubCh = mHdActive ? (int)mRadioInterface.getHdValue(RadioKey.Command.HD_SUBCHANNEL) : 0;

                                mRadioActivityPrefs.edit().putInt("pref_key_stored_fm_freq", prevFreq)
                                        .putInt("pref_key_stored_fm_subch", prevSubCh)
                                        .apply();
                                mRadioInterface.tune(RadioKey.Band.AM, frequency, 0);
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
            RadioMessage radioMessage = (RadioMessage)message.obj;
            switch (radioMessage.key) {
                case POWER:
                    // Because the power value is ALWAYS true, query the radio interface for power
                    // status.  If its on launch runnable
                    if (mRadioInterface.getPowerStatus() && !mIsRequestingSignal) {
                        EXECUTOR.execute(mRequestSignalRunnable);
                    }
                    break;
                case MUTE:
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
                        String newFreq = mFrequency + "-" + String.valueOf((int)radioMessage.value);
                        mRadioFreqText.setText(newFreq);
                        mTextSwapAnimator.setTextItem(RadioKey.Command.TUNE, newFreq + " " + mBand);

                        // Update info text with artist and title from current subchannel
                        mTextSwapAnimator.setTextItem(RadioKey.Command.HD_TITLE,
                                (String)mRadioInterface.getHdValue(RadioKey.Command.HD_TITLE));
                        mTextSwapAnimator.setTextItem(RadioKey.Command.HD_ARTIST,
                                (String)mRadioInterface.getHdValue(RadioKey.Command.HD_ARTIST));

                    } else {
                        mRadioFreqText.setText(mFrequency);
                        mTextSwapAnimator.setTextItem(RadioKey.Command.TUNE, mFrequency + " " + mBand);

                    }
                    break;
                case TUNE:
                    mHandler.removeCallbacks(mPostTuneRunnable);

                    RadioController.TuneInfo info = (RadioController.TuneInfo) radioMessage.value;
                    if (info.band == RadioKey.Band.FM) {
                        mBand = "FM";
                        mFrequency = String.format(Locale.US, "%1$.1f", (float)info.frequency/10);
                        mBandButton.setChecked(true);
                    } else {
                        mBand = "AM";
                        mFrequency = String.valueOf(info.frequency);
                        mBandButton.setChecked(false);
                    }
                    mTextStreamAnimator.clear();
                    mTextSwapAnimator.setTextItem(RadioKey.Command.TUNE, mFrequency + " " + mBand);
                    mTextSwapAnimator.resetAnimator();
                    mRdsEnabled = false;
                    mHdActive = false;
                    // TODO: mHDStatus text is temporary
                    mHdStatusText.setText("");
                    mRadioFreqText.setText(mFrequency);
                    mRadioBandText.setText(info.band.toString());
                    mHandler.postDelayed(mPostTuneRunnable, 2000);
                    break;
                case SEEK:
                    String tmpFreq;
                    if (mBandButton.isChecked()) {
                        tmpFreq = String.format(Locale.US, "%1$.1f",
                                ((int)radioMessage.value)/10f);
                    } else {
                        tmpFreq = String.valueOf((int)radioMessage.value);
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

    private Runnable mPowerOffRunnable = new Runnable() {
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
        }
    };

    private Runnable mRequestSignalRunnable = new Runnable() {
        @Override
        public void run() {
            mIsRequestingSignal = true;
            while (mRadioInterface != null && mRadioInterface.getPowerStatus()
                    && mIsRequestingSignal) {
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
