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
import com.arksine.autointegrate.radio.RemoteRadioEvents;
import com.arksine.autointegrate.radio.TextStreamAnimator;
import com.arksine.autointegrate.utilities.BackgroundThreadFactory;
import com.arksine.autointegrate.radio.TextSwapAnimator;
import com.arksine.autointegrate.utilities.DLog;
import com.arksine.hdradiolib.HDSongInfo;
import com.arksine.hdradiolib.RadioController;
import com.arksine.hdradiolib.TuneInfo;
import com.arksine.hdradiolib.enums.RadioBand;
import com.arksine.hdradiolib.enums.RadioCommand;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

// TODO: is signal strength 0-3000?
public class RadioActivity extends AppCompatActivity {
    private static final String TAG = RadioActivity.class.getSimpleName();

    private SharedPreferences mRadioActivityPrefs;
    private ExecutorService EXECUTOR = Executors.newCachedThreadPool(new BackgroundThreadFactory());
    private Handler mUiHandler;
    private boolean mBound = false;
    private RadioController mRadioController = null;

    // Local radio tracking vars
    private AtomicBoolean mIsPoweredOn = new AtomicBoolean(false);
    private AtomicBoolean mIsExiting = new AtomicBoolean(false);
    private AtomicBoolean mRdsEnabled = new AtomicBoolean(false);
    private AtomicBoolean mHdActive = new AtomicBoolean(false);
    private volatile int mCurrentFrequency = 879;
    private volatile RadioBand mCurrentBand = RadioBand.FM;

    private RadioSettingsDialog mRadioSettingsDialog;
    private TextSwapAnimator mTextSwapAnimator;
    private TextStreamAnimator mTextStreamAnimator;

    private TextView mRadioStatusText;
    private TextView mRadioFreqText;
    private TextView mRadioInfoText;
    private TextView mStreamingInfoText;
    private ToggleButton mPowerButton;
    private ToggleButton mMuteButton;
    private ToggleButton mSeekAllButton;
    private ToggleButton mBandButton;

    private final RemoteRadioEvents mRadioEvents = new RemoteRadioEvents() {
        @Override
        public void onError() throws RemoteException {

            if (mIsPoweredOn.compareAndSet(true, false)) {
                mUiHandler.post(mClearViewsRunnable);
            }

            mUiHandler.post(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(RadioActivity.this, "Device Error, disconnecting",
                            Toast.LENGTH_SHORT).show();
                }
            });

            mRadioController = null;

            // TODO: I should pop up a dialog allowing the activity to restart / reattempt
            // to connect to radio controller.

        }

        @Override
        public void onClosed() throws RemoteException {
            if (mIsExiting.get()) {
                // Close the activity if the user exits
                RadioActivity.this.finish();
                return;
            }

            // if powered on, set the power status false and clear UI variables
            if (mIsPoweredOn.compareAndSet(true, false)) {
                mUiHandler.post(mClearViewsRunnable);
            }

            mRadioController = null;
        }

        @Override
        public void onPowerOn() throws RemoteException {
            mIsPoweredOn.set(true);
            togglePowerButton(true);
        }

        @Override
        public void onPowerOff() throws RemoteException {
            mIsPoweredOn.set(false);

            // enable the power button
            togglePowerButton(true);
            mUiHandler.post(mClearViewsRunnable);
        }

        @Override
        public void onRadioMute(final boolean muteStatus) throws RemoteException {
            mUiHandler.post(new Runnable() {
                @Override
                public void run() {
                    mMuteButton.setChecked(muteStatus);
                }
            });
        }

        @Override
        public void onRadioSignalStrength(final int signalStrength) throws RemoteException {
            // TODO: update signal meter
        }

        @Override
        public void onRadioTune(final TuneInfo tuneInfo) throws RemoteException {
            mCurrentFrequency = tuneInfo.getFrequency();
            mCurrentBand = tuneInfo.getBand();
            mHdActive.set(false);

            // Format the string depending on FM or AM
            final String tuneStr = (mCurrentBand == RadioBand.FM) ?
                    String.format(Locale.US, "%1$.1f FM", (float) mCurrentFrequency / 10) :
                    String.format(Locale.US, "%1$d AM", mCurrentFrequency);
            final boolean bandStatus = (mCurrentBand == RadioBand.FM);

            mUiHandler.post(new Runnable() {
                @Override
                public void run() {
                    mBandButton.setChecked(bandStatus);
                    mTextSwapAnimator.setTextItem(RadioCommand.TUNE, tuneStr);
                    mTextSwapAnimator.resetAnimator();
                    mRadioStatusText.setText("");
                    mRadioFreqText.setText(tuneStr);
                }
            });
        }

        @Override
        public void onRadioSeek(final TuneInfo seekInfo) throws RemoteException {
            // Format the string depending on FM or AM
            final String seekStr = (seekInfo.getBand() == RadioBand.FM) ?
                    String.format(Locale.US, "%1$.1f FM", seekInfo.getFrequency() / 10f) :
                    String.format(Locale.US, "%1$d AM", seekInfo.getFrequency());
            mUiHandler.post(new Runnable() {
                @Override
                public void run() {
                    mRadioFreqText.setText(seekStr);
                }
            });
        }

        @Override
        public void onRadioHdActive(final boolean hdActive) throws RemoteException {
            // TODO: Show HD Icon if true, hide if false, the textview below is temporary
            mHdActive.set(hdActive);

            mUiHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (hdActive) {
                        mRadioStatusText.setText("HD");
                    } else {
                        mRadioStatusText.setText("");
                    }
                }
            });
        }

        @Override
        public void onRadioHdStreamLock(final boolean hdStreamLock) throws RemoteException {
            // TODO:
        }

        @Override
        public void onRadioHdSignalStrength(final int hdSignalStrength) throws RemoteException {
            // TODO: update HD Signal meter
        }

        @Override
        public void onRadioHdSubchannel(final int subchannel) throws RemoteException {
            if (subchannel > 0) {
                mHdActive.set(true);

                // Format the HD string
                final String hdStr = (mCurrentBand == RadioBand.FM) ?
                        String.format(Locale.US, "%1$.1f FM HD%2$d",
                                (float)mCurrentFrequency/10, subchannel) :
                        String.format(Locale.US, "%1$d AM HD%2$d",
                                mCurrentFrequency, subchannel);

                mUiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mRadioFreqText.setText(hdStr);
                        mTextSwapAnimator.setTextItem(RadioCommand.TUNE, hdStr);

                        // Update info text with artist and title from current subchannel
                        mTextSwapAnimator.setTextItem(RadioCommand.HD_TITLE,
                                mRadioController.getHdTitle());
                        mTextSwapAnimator.setTextItem(RadioCommand.HD_ARTIST,
                                mRadioController.getHdArtist());
                    }
                });

            } else {
                mHdActive.set(false);

                // Format standard string
                final String stdStr = (mCurrentBand == RadioBand.FM) ?
                        String.format(Locale.US, "%1$.1f FM", (float) mCurrentFrequency / 10) :
                        String.format(Locale.US, "%1$d AM", mCurrentFrequency);

                mUiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mRadioFreqText.setText(stdStr);
                        mTextSwapAnimator.setTextItem(RadioCommand.TUNE, stdStr);
                    }
                });
            }
        }

        @Override
        public void onRadioHdSubchannelCount(final int subchannelCount) throws RemoteException {
            // TODO: display number of available subchannels? (this variable doesn't always seem consistent)
        }

        @Override
        public void onRadioHdTitle(final HDSongInfo hdTitle) throws RemoteException {

            mUiHandler.post(new Runnable() {
                @Override
                public void run() {
                    mTextSwapAnimator.setTextItem(RadioCommand.HD_TITLE, hdTitle.getInfo());
                }
            });

            //  request current subchannel if we don't have a subchannel set
            if (mRadioController.getHdSubchannel() < 1) {
                mRadioController.requestUpdate(RadioCommand.HD_SUBCHANNEL);
            }
        }

        @Override
        public void onRadioHdArtist(final HDSongInfo hdArtist) throws RemoteException {

            mUiHandler.post(new Runnable() {
                @Override
                public void run() {
                    mTextSwapAnimator.setTextItem(RadioCommand.HD_ARTIST, hdArtist.getInfo());
                }
            });

            //  request current subchannel if we don't have a subchannel set
            if (mRadioController.getHdSubchannel() < 1) {
                mRadioController.requestUpdate(RadioCommand.HD_SUBCHANNEL);
            }
        }

        @Override
        public void onRadioHdCallsign(final String hdCallsign) throws RemoteException {
            mUiHandler.post(new Runnable() {
                @Override
                public void run() {
                    mTextSwapAnimator.setTextItem(RadioCommand.HD_CALLSIGN, hdCallsign);
                }
            });
        }

        @Override
        public void onRadioHdStationName(final String hdStationName) throws RemoteException {
            // TODO: this is a String, I should probably do something with this
        }

        @Override
        public void onRadioRdsEnabled(final boolean rdsEnabled) throws RemoteException {
            mUiHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (rdsEnabled) {
                        mRadioStatusText.setText("RDS");
                    } else {
                        mRadioStatusText.setText("");
                    }
                }
            });
        }

        @Override
        public void onRadioRdsGenre(final String rdsGenre) throws RemoteException {
            mUiHandler.post(new Runnable() {
                @Override
                public void run() {
                    mTextSwapAnimator.setTextItem(RadioCommand.RDS_GENRE, rdsGenre);
                }
            });
        }

        @Override
        public void onRadioRdsProgramService(final String rdsProgramService) throws RemoteException {
            mUiHandler.post(new Runnable() {
                @Override
                public void run() {
                    mTextStreamAnimator.addStreamingText(rdsProgramService);
                }
            });
        }

        @Override
        public void onRadioRdsRadioText(final String rdsRadioText) throws RemoteException {
            mUiHandler.post(new Runnable() {
                @Override
                public void run() {
                    mTextSwapAnimator.setTextItem(RadioCommand.RDS_RADIO_TEXT, rdsRadioText);
                }
            });
        }

        @Override
        public void onRadioVolume(final int volume) throws RemoteException {
            mUiHandler.post(new Runnable() {
                @Override
                public void run() {
                    mRadioSettingsDialog.setSeekBarProgress(RadioCommand.VOLUME, volume);
                }
            });
        }

        @Override
        public void onRadioBass(final int bass) throws RemoteException {
            mUiHandler.post(new Runnable() {
                @Override
                public void run() {
                    mRadioSettingsDialog.setSeekBarProgress(RadioCommand.BASS, bass);
                }
            });
        }

        @Override
        public void onRadioTreble(final int treble) throws RemoteException {
            mUiHandler.post(new Runnable() {
                @Override
                public void run() {
                    mRadioSettingsDialog.setSeekBarProgress(RadioCommand.TREBLE, treble);
                }
            });
        }

        @Override
        public void onRadioCompression(final int compression) throws RemoteException {
            // TODO: not sure what this is, probably don't need to do anything with it
        }
    };

    private ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            final MainService.LocalBinder binder = (MainService.LocalBinder) iBinder;

            binder.registerRadioCallback(mRadioEvents);
            mRadioController = binder.getRadioInterface();

            if (mRadioController == null) {
                Log.i(TAG, "Radio Interface not available");
                mUiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(RadioActivity.this, "Radio Interface not available",
                                Toast.LENGTH_SHORT).show();
                    }
                });

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
            if (mRadioController != null) {

                // get power status from the interface, because we are unable to receive the
                // power command from the radio prior to binding to the service
                mIsPoweredOn.set(mRadioController.isPoweredOn());

                // get persistent seekall value
                final boolean seekAll = mRadioController.getSeekAll();

                mUiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        // set views here
                        mPowerButton.setChecked(mIsPoweredOn.get());
                        mSeekAllButton.setChecked(seekAll);

                    }
                });

                // Request the status of a important radio values if the radio is already powered on
                if (mIsPoweredOn.get()) {
                    mHdActive.set(mRadioController.getHdActive());
                    mRdsEnabled.set(mRadioController.getRdsEnabled());
                    final int subch = mRadioController.getHdSubchannel();
                    final TuneInfo info = mRadioController.getTune();
                    mCurrentFrequency = info.getFrequency();
                    mCurrentBand = info.getBand();

                    // Update UI based on current settings
                    mUiHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            mMuteButton.setChecked(mRadioController.getMute());
                            mRadioSettingsDialog.setSeekBarProgress(RadioCommand.VOLUME,
                                    mRadioController.getVolume());
                            mRadioSettingsDialog.setSeekBarProgress(RadioCommand.BASS,
                                    mRadioController.getBass());
                            mRadioSettingsDialog.setSeekBarProgress(RadioCommand.TREBLE,
                                    mRadioController.getTreble());

                            if (subch > 0) {
                                // Format the HD string
                                final String hdStr = (mCurrentBand == RadioBand.FM) ?
                                        String.format(Locale.US, "%1$.1f FM HD%2$d",
                                                (float)mCurrentFrequency/10, subch) :
                                        String.format(Locale.US, "%1$d AM HD%2$d",
                                                mCurrentFrequency, subch);

                                mRadioFreqText.setText(hdStr);
                                mTextSwapAnimator.setTextItem(RadioCommand.TUNE, hdStr);

                                // Update info text with artist and title from current subchannel
                                mTextSwapAnimator.setTextItem(RadioCommand.HD_TITLE,
                                        mRadioController.getHdTitle());
                                mTextSwapAnimator.setTextItem(RadioCommand.HD_ARTIST,
                                        mRadioController.getHdArtist());

                            } else {

                                // Format standard string
                                final String stdStr = (mCurrentBand == RadioBand.FM) ?
                                        String.format(Locale.US, "%1$.1f FM", (float) mCurrentFrequency / 10) :
                                        String.format(Locale.US, "%1$d AM", mCurrentFrequency);

                                mRadioFreqText.setText(stdStr);
                                mTextSwapAnimator.setTextItem(RadioCommand.TUNE, stdStr);
                            }
                        }
                    });

                } else {
                    runOnUiThread(mClearViewsRunnable);
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

        mUiHandler = new Handler(Looper.getMainLooper());
        mRadioActivityPrefs = this.getSharedPreferences("radio_activity_preferences",
                Context.MODE_PRIVATE);

        initViews();
        initAudioSettingsDialog();

        mTextSwapAnimator = new TextSwapAnimator(mRadioInfoText);
        mTextStreamAnimator = new TextStreamAnimator(mStreamingInfoText);
    }

    private void initViews() {
        mRadioStatusText = (TextView)findViewById(R.id.txt_radio_status);
        mRadioFreqText = (TextView)findViewById(R.id.txt_radio_frequency);
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
                //TODO: Disable power button for two seconds, then post delay a re-enable
                DLog.v(TAG, "Power Clicked, interface is: " + (mRadioController != null));
                if (mRadioController != null) {
                    final boolean status = ((ToggleButton)view).isChecked();

                    DLog.v(TAG, "Set power: " + status);
                    if (status) {
                        mRadioController.powerOn();
                    } else {
                        mRadioController.powerOff();
                    }

                }
            }
        });

        mMuteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mRadioController != null) {
                    final boolean status = ((ToggleButton)view).isChecked();

                    DLog.v(TAG, "Set mute: " + status);
                    if (status) {
                        mRadioController.muteOn();
                    } else {
                        mRadioController.muteOff();
                    }
                }

            }
        });

        mSeekAllButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mRadioController != null) {
                    final boolean status = ((ToggleButton) view).isChecked();
                    mRadioController.setSeekAll(status);
                }
            }
        });

        mBandButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mRadioController != null) {
                    final boolean band = ((ToggleButton) view).isChecked();
                    DLog.v(TAG, "Switch Band to FM: " + band);
                    if (band) {
                        // fm
                        final int frequency = mRadioActivityPrefs.getInt("pref_key_stored_fm_freq", 879);
                        final int subChannel = mRadioActivityPrefs.getInt("pref_key_stored_fm_subch", 0);

                        //save am channel
                        int prevFreq = mRadioController.getTune().getFrequency();
                        int prevSubCh = mHdActive.get() ? mRadioController.getHdSubchannel() : 0;
                        mRadioActivityPrefs.edit().putInt("pref_key_stored_am_freq", prevFreq)
                                .putInt("pref_key_stored_am_subch", prevSubCh)
                                .apply();

                        TuneInfo tuneInfo = new TuneInfo(RadioBand.FM, frequency, subChannel);

                        mRadioController.tune(tuneInfo);
                    } else {
                        // am
                        final int frequency = mRadioActivityPrefs.getInt("pref_key_stored_am_freq", 900);
                        final int subChannel = mRadioActivityPrefs.getInt("pref_key_stored_am_subch", 0);

                        //save fm channel
                        int prevFreq = mRadioController.getTune().getFrequency();
                        int prevSubCh = mHdActive.get() ? mRadioController.getHdSubchannel() : 0;

                        mRadioActivityPrefs.edit().putInt("pref_key_stored_fm_freq", prevFreq)
                                .putInt("pref_key_stored_fm_subch", prevSubCh)
                                .apply();

                        TuneInfo tuneInfo = new TuneInfo(RadioBand.AM, frequency, subChannel);
                        mRadioController.tune(tuneInfo);
                    }
                }
            }
        });

        mTuneUpButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mRadioController != null) {
                    // TODO: need to check if we have HD Stream lock.  If so, up increase
                    //       the subchannel count


                    EXECUTOR.execute(new Runnable() {
                        @Override
                        public void run() {
                            int curSc = mRadioController.getHdSubchannel();
                            int count = mRadioController.getHdSubchannelCount();

                            if (!mHdActive.get() || curSc >= count) {
                                // If not currently tuned to an HD Channel, or the channel is
                                // already at the maximum listed HD Channel, regular tune up
                                mRadioController.tuneUp();
                            } else {
                                curSc++;
                                mRadioController.setHdSubChannel(curSc);
                            }
                        }
                    });
                }
            }
        });

        mTuneDownButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mRadioController != null) {
                    EXECUTOR.execute(new Runnable() {
                        @Override
                        public void run() {

                            int curSc = mRadioController.getHdSubchannel();
                            if (curSc < 2) {
                                // tune down to the next channel, as we either arent on an HD channel
                                // or are on channel 1
                                mRadioController.tuneDown();
                            } else {
                                curSc--;
                                mRadioController.setHdSubChannel(curSc);
                            }

                        }
                    });

                }
            }
        });

        mSeekUpButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mRadioController != null) {
                    mRadioController.seekUp();
                }
            }
        });

        mSeekDownButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mRadioController != null) {
                    mRadioController.seekDown();
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
                if (mRadioController != null) {
                    mRadioController.setVolumeUp();
                }
            }
        });

        volumeDown.setOnClickListener((new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mRadioController != null) {
                    mRadioController.setVolumeDown();
                }
            }
        }));
    }

    private void initAudioSettingsDialog() {

        RadioSettingsDialog.SeekBarListener listener = new RadioSettingsDialog.SeekBarListener() {
            @Override
            public void OnSeekBarChanged(RadioCommand key, int value) {
                switch (key) {
                    case VOLUME:
                        if (mRadioController != null) {
                            mRadioController.setVolume(value);
                        }
                        break;
                    case BASS:
                        if (mRadioController != null) {
                            mRadioController.setBass(value);
                        }
                        break;
                    case TREBLE:
                        if (mRadioController != null) {
                            mRadioController.setTreble(value);
                        }
                        break;
                    default:
                        DLog.v(TAG, "Invalid command, cannot set apply setting");
                }
            }
        };

        mRadioSettingsDialog = new RadioSettingsDialog(this, listener);

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

        mRadioSettingsDialog.restoreValues();

        Intent intent = new Intent(this, MainService.class);
        bindService(intent, mServiceConnection, Context.BIND_AUTO_CREATE);

    }

    @Override
    protected void onPause() {
        super.onPause();
        mRadioSettingsDialog.persistValues();
        mTextSwapAnimator.stopAnimation();

        if (mBound) {
            unbindService(mServiceConnection);
            mBound = false;
        }
    }

    // Allows the power button to be toggled outside of the UI thread
    private void togglePowerButton(final boolean status) {
        this.mUiHandler.post(new Runnable() {
            @Override
            public void run() {
                mPowerButton.setEnabled(status);
                mPowerButton.setClickable(status);
            }
        });
    }

    private Runnable mClearViewsRunnable = new Runnable() {
        @Override
        public void run() {
            // Clear text views, set infoview frequency to PowerOff
            mTextStreamAnimator.clear();
            mTextSwapAnimator.setTextItem(RadioCommand.TUNE, "Power Off");
            mTextSwapAnimator.resetAnimator();
            mRadioStatusText.setText("");
            mRadioFreqText.setText("");
            mRdsEnabled.set(false);
            mHdActive.set(false);
            mIsPoweredOn.set(false);
        }
    };

}
