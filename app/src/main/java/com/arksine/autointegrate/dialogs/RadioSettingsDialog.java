package com.arksine.autointegrate.dialogs;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;

import com.arksine.autointegrate.R;
import com.arksine.autointegrate.utilities.DLog;
import com.arksine.hdradiolib.enums.RadioCommand;
import com.orhanobut.dialogplus.DialogPlus;
import com.orhanobut.dialogplus.ViewHolder;

/**
 * Dialog to control HD Radio Volume, Bass, and Treble
 */

public class RadioSettingsDialog {
    private static final String TAG = RadioSettingsDialog.class.getSimpleName();

    public interface SeekBarListener {
        void OnSeekBarChanged(RadioCommand key, int value);
    }

    private Context mContext;
    private SeekBarListener mSeekBarListener;

    private DialogPlus mDialog;
    private SeekBar mVolumeSeekbar;
    private SeekBar mBassSeekbar;
    private SeekBar mTrebleSeekbar;

    public RadioSettingsDialog(Context context, SeekBarListener listener) {
        mContext = context;
        mSeekBarListener = listener;

        buildDialog();
    }

    private void buildDialog() {
        mDialog = DialogPlus.newDialog(mContext)
                .setContentHolder(new ViewHolder(R.layout.dialog_radio_settings))
                .setHeader(R.layout.dialog_header)
                .setFooter(R.layout.dialog_footer_one_button)
                .setContentWidth(ViewGroup.LayoutParams.WRAP_CONTENT)
                .setContentHeight(ViewGroup.LayoutParams.WRAP_CONTENT)
                .setGravity(Gravity.CENTER)
                .create();

        mVolumeSeekbar = (SeekBar) mDialog.findViewById(R.id.seekbar_radio_volume);
        mBassSeekbar = (SeekBar) mDialog.findViewById(R.id.seekbar_radio_bass);
        mTrebleSeekbar = (SeekBar) mDialog.findViewById(R.id.seekbar_radio_treble);


        mVolumeSeekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {}

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                mSeekBarListener.OnSeekBarChanged(RadioCommand.VOLUME, seekBar.getProgress());
            }
        });

        mBassSeekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {}

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                mSeekBarListener.OnSeekBarChanged(RadioCommand.BASS, seekBar.getProgress());
            }
        });

        mTrebleSeekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {}

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                mSeekBarListener.OnSeekBarChanged(RadioCommand.TREBLE, seekBar.getProgress());
            }
        });

        TextView title = (TextView) mDialog.findViewById(R.id.txt_dialog_title);
        title.setText(mContext.getString(R.string.dialog_radio_audio_settings));

        Button okButton = (Button) mDialog.findViewById(R.id.btn_dialog_ok);
        okButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mDialog.dismiss();
            }
        });

        Button resetButton = (Button) mDialog.findViewById(R.id.btn_radio_reset_default);
        resetButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mVolumeSeekbar.setProgress(75);
                mBassSeekbar.setProgress(15);
                mTrebleSeekbar.setProgress(15);
                mSeekBarListener.OnSeekBarChanged(RadioCommand.VOLUME, 75);
                mSeekBarListener.OnSeekBarChanged(RadioCommand.BASS, 15);
                mSeekBarListener.OnSeekBarChanged(RadioCommand.TREBLE, 15);
            }
        });




        // TODO: The header has a help button, don't really need it for this. Should make a new header
        // layout with title only
    }

    public void showDialog() {
        mDialog.show();
    }

    public void setSeekBarProgress(RadioCommand key, int progress) {
        switch (key) {
            case VOLUME:
                mVolumeSeekbar.setProgress(progress);
                break;
            case BASS:
                mBassSeekbar.setProgress(progress);
                break;
            case TREBLE:
                mTrebleSeekbar.setProgress(progress);
                break;
            default:
                DLog.v(TAG, "Invalid command, cannot set seekbar progress");
        }
    }

    public int getSeekBarProgress(RadioCommand key) {
        switch (key) {
            case VOLUME:
                return mVolumeSeekbar.getProgress();
            case BASS:
                return mBassSeekbar.getProgress();
            case TREBLE:
                return mTrebleSeekbar.getProgress();
            default:
                DLog.v(TAG, "Invalid command, cannot set seekbar progress");
                return 0;
        }
    }

    public void restoreValues() {
        SharedPreferences globalPrefs = PreferenceManager.getDefaultSharedPreferences(mContext);

        int volume = globalPrefs.getInt("radio_pref_key_volume", 50);
        int bass = globalPrefs.getInt("radio_pref_key_bass", 15);
        int treble = globalPrefs.getInt("radio_pref_key_treble", 15);

        mVolumeSeekbar.setProgress(volume);
        mBassSeekbar.setProgress(bass);
        mTrebleSeekbar.setProgress(treble);
    }

    public void persistValues() {
        PreferenceManager.getDefaultSharedPreferences(mContext).edit()
                .putInt("radio_pref_key_volume", mVolumeSeekbar.getProgress())
                .putInt("radio_pref_key_bass", mBassSeekbar.getProgress())
                .putInt("radio_pref_key_treble", mTrebleSeekbar.getProgress())
                .apply();
    }
}
