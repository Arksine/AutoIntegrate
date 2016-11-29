package com.arksine.autointegrate.dialogs;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.ViewAnimator;

import com.arksine.autointegrate.R;
import com.arksine.autointegrate.microcontroller.CommandProcessor.DimmerMode;
import com.arksine.autointegrate.utilities.UtilityFunctions;
import com.orhanobut.dialogplus.DialogPlus;
import com.orhanobut.dialogplus.ViewHolder;

// TODO: Either disable save button during the wizard, or replace it with Continue/Finish and navigate
//       actions that way

/**
 *  Handles Lifecycle of dimmer settings dialog
 */

public class DimmerCalibrationDialog {
    private static String TAG = "DimmerCalibrationDialog";

    private static class WizardPage {
        final static int NONE = 0;
        final static int PAGE_ONE = 1;
        final static int PAGE_TWO_DIGITAL = 2;
        final static int PAGE_TWO_ANALOG = 3;
        final static int PAGE_THREE = 4;
    }

    private int mCurrentPage = WizardPage.NONE;

    private class DimmerValues {
        int dimmerMode = DimmerMode.NONE;
        int highReading = -1;
        int highBrightness = -1;
        int lowReading = -1;
        int lowBrightness = -1;
    }
    private DimmerValues mDimmerVals;

    private Context mContext;
    private int mInitialBrightness = 1;
    private int mCurrentBrightness = 1;

    // Dialog vars
    private DialogPlus mDimmerDialog;
    private Spinner mDimmerControlTypeSpinner;
    private SeekBar mBrightnessBar;
    private ViewAnimator mDimmerViewAnimator;
    private TextView mTxtDimmerHighReading;
    private TextView mTxtDimmerHighBrightness;
    private TextView mTxtDimmerLowReading;
    private TextView mTxtDimmerLowBrightness;

    private HelpDialog mDimmerHelp;

    public DimmerCalibrationDialog(Context context) {
        mContext = context;
        mDimmerVals = new DimmerValues();
        mDimmerHelp = new HelpDialog(mContext, R.layout.dialog_help_dimmer);
        getStoredPrefs();       // retreived stored preferences
        buildDialog();
    }


    private void getStoredPrefs() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);

        mDimmerVals.dimmerMode = prefs.getInt("dimmer_pref_key_mode", DimmerMode.NONE);
        mDimmerVals.highReading = prefs.getInt("dimmer_pref_key_high_reading", -1);
        mDimmerVals.highBrightness = prefs.getInt("dimmer_pref_key_high_brightness", -1);
        mDimmerVals.lowReading = prefs.getInt("dimmer_pref_key_low_reading", -1);
        mDimmerVals.lowBrightness = prefs.getInt("dimmer_pref_key_low_brightness", -1);
    }

    private void writePrefs() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);

        // save prefs to defualt shared prefs
        prefs.edit().putInt("dimmer_pref_key_mode", mDimmerVals.dimmerMode)
                .putInt("dimmer_pref_key_high_reading", mDimmerVals.highReading)
                .putInt("dimmer_pref_key_high_brightness", mDimmerVals.highBrightness)
                .putInt("dimmer_pref_key_low_reading", mDimmerVals.lowReading)
                .putInt("dimmer_pref_key_low_brightness", mDimmerVals.lowBrightness)
                .apply();

    }

    private void updateDialogViews() {
        // Write values to textviews
        if (mDimmerVals.dimmerMode == DimmerMode.DIGITAL) {
            mTxtDimmerHighReading.setText(R.string.dimmer_dialog_label_on);
        } else if (mDimmerVals.highReading != -1) {
            mTxtDimmerHighReading.setText(String.valueOf(mDimmerVals.highReading));
        } else {
            mTxtDimmerHighReading.setText(mContext.getString(R.string.dimmer_dialog_text_not_set));
        }

        if (mDimmerVals.highBrightness != -1) {
            mTxtDimmerHighBrightness.setText(String.valueOf(mDimmerVals.highBrightness));
        } else {
            mTxtDimmerHighBrightness.setText(mContext.getString(R.string.dimmer_dialog_text_not_set));
        }

        if (mDimmerVals.lowReading != -1) {
            mTxtDimmerLowReading.setText(String.valueOf(mDimmerVals.lowReading));
        } else {
            mTxtDimmerLowReading.setText(mContext.getString(R.string.dimmer_dialog_text_not_set));
        }

        if (mDimmerVals.lowBrightness != -1) {
            mTxtDimmerLowBrightness.setText(String.valueOf(mDimmerVals.lowBrightness));
        } else {
            mTxtDimmerLowBrightness.setText(mContext.getString(R.string.dimmer_dialog_text_not_set));
        }
    }

    private void buildDialog() {
        mDimmerDialog = DialogPlus.newDialog(mContext)
                .setContentHolder(new ViewHolder(R.layout.dialog_dimmer_calibration))
                .setHeader(R.layout.dialog_header)
                .setFooter(R.layout.dialog_footer)
                .setContentWidth(ViewGroup.LayoutParams.WRAP_CONTENT)
                .setContentHeight(ViewGroup.LayoutParams.WRAP_CONTENT)
                .setGravity(Gravity.CENTER)
                .create();

        TextView mDialogTitle = (TextView) mDimmerDialog.findViewById(R.id.txt_dialog_title);
        mDialogTitle.setText(R.string.dimmer_dialog_title);

        mDimmerControlTypeSpinner = (Spinner) mDimmerDialog.findViewById(R.id.spn_dimmer_mode);
        mDimmerControlTypeSpinner.setSelection(mDimmerVals.dimmerMode);
        mDimmerControlTypeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                mDimmerVals.dimmerMode = position;
                setOverviewContent(position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        mTxtDimmerHighReading = (TextView) mDimmerDialog.findViewById(R.id.txt_high_reading);
        mTxtDimmerHighBrightness = (TextView) mDimmerDialog.findViewById(R.id.txt_high_brightness);
        mTxtDimmerLowReading = (TextView) mDimmerDialog.findViewById(R.id.txt_low_reading);
        mTxtDimmerLowBrightness = (TextView) mDimmerDialog.findViewById(R.id.txt_low_brightness);

        // Setup brightness bar
        mBrightnessBar = (SeekBar) mDimmerDialog.findViewById(R.id.seek_bar_brightness);
        mBrightnessBar.setVisibility(View.GONE);
        mBrightnessBar.setMax(255);
        mBrightnessBar.setKeyProgressIncrement(1);
        mBrightnessBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                mCurrentBrightness = progress;
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (mCurrentBrightness == 0) {
                    mCurrentBrightness = 1;
                }
                setScreenBrightness(mCurrentBrightness);

            }
        });

        mDimmerViewAnimator = (ViewAnimator) mDimmerDialog.findViewById(R.id.dimmer_view_animator);
        final Animation inAnimation = AnimationUtils.loadAnimation(mContext,
                android.R.anim.fade_in);
        final Animation outAnimation = AnimationUtils.loadAnimation(mContext,
                android.R.anim.fade_out);
        mDimmerViewAnimator.setInAnimation(inAnimation);
        mDimmerViewAnimator.setOutAnimation(outAnimation);
        setOverviewContent(mDimmerControlTypeSpinner.getSelectedItemPosition());


        Button startCalButton = (Button) mDimmerDialog.findViewById(R.id.btn_start_cal);
        startCalButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mCurrentPage = WizardPage.PAGE_ONE;

                switch (mDimmerVals.dimmerMode) {
                    case DimmerMode.DIGITAL:
                        setDigitalReadingTextView("", R.id.txt_dimmer_reading_digital);
                        break;
                    case DimmerMode.ANALOG:
                        setAnalogReadingTextView("0", R.id.txt_dimmer_reading_analog_high);
                        setAnalogReadingTextView("0", R.id.txt_dimmer_reading_analog_low);
                        break;
                    default:
                        Log.i(TAG, "Invalid dimmer mode selected");
                }

                mDimmerViewAnimator.showNext();

            }
        });

        Button pageOneBtn = (Button) mDimmerDialog.findViewById(R.id.btn_page_one);
        pageOneBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showBrightnessBar();

                switch (mDimmerVals.dimmerMode) {
                    case DimmerMode.DIGITAL:
                        mCurrentPage = WizardPage.PAGE_TWO_DIGITAL;
                        mDimmerViewAnimator.showNext();
                        break;
                    case DimmerMode.ANALOG:
                        mCurrentPage = WizardPage.PAGE_TWO_ANALOG;
                        mDimmerViewAnimator.setDisplayedChild(mCurrentPage);
                        break;
                }
            }
        });

        Button pageTwoDigitalButton = (Button) mDimmerDialog.findViewById(R.id.btn_page_two_digital);
        pageTwoDigitalButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Finish digital calibration
                mDimmerVals.highBrightness = mCurrentBrightness;
                updateDialogViews();
                mCurrentPage = WizardPage.NONE;
                mDimmerViewAnimator.setDisplayedChild(mCurrentPage);
                hideBrightnessBar();
            }
        });

        Button pageTwoAnalogButton = (Button) mDimmerDialog.findViewById(R.id.btn_page_two_analog);
        pageTwoAnalogButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mDimmerVals.highBrightness = mCurrentBrightness;
                mCurrentPage = WizardPage.PAGE_THREE;
                mDimmerViewAnimator.setDisplayedChild(mCurrentPage);
            }
        });

        Button pageThreeButton = (Button) mDimmerDialog.findViewById(R.id.btn_page_three_analog);
        pageThreeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mDimmerVals.lowBrightness = mCurrentBrightness;
                updateDialogViews();
                mCurrentPage = WizardPage.NONE;
                mDimmerViewAnimator.setDisplayedChild(mCurrentPage);
                hideBrightnessBar();
            }
        });


        Button saveButton = (Button) mDimmerDialog.findViewById(R.id.btn_dialog_save);
        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                writePrefs();
                mDimmerDialog.dismiss();
            }
        });

        Button cancelButton = (Button) mDimmerDialog.findViewById(R.id.btn_dialog_cancel);
        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mCurrentPage = WizardPage.NONE;
                getStoredPrefs();
                mDimmerControlTypeSpinner.setSelection(mDimmerVals.dimmerMode);
                updateDialogViews();
                mDimmerDialog.dismiss();
            }
        });

        ImageButton helpButton = (ImageButton) mDimmerDialog.findViewById(R.id.btn_help);
        helpButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mDimmerHelp.showHelpDialog(v);
            }
        });

    }

    private void setOverviewContent(int position) {

        TextView dimmerHighLabel = (TextView) mDimmerDialog.findViewById(R.id.label_dimmer_high_on);
        TextView dimmerLowLabel = (TextView) mDimmerDialog.findViewById(R.id.label_dimmer_low);

        updateDialogViews();

        // reset the wizard
        mBrightnessBar.setVisibility(View.GONE);
        mCurrentPage = WizardPage.NONE;
        mDimmerViewAnimator.setDisplayedChild(mCurrentPage);

        switch (position) {
            case DimmerMode.DIGITAL:
                mDimmerViewAnimator.setVisibility(View.VISIBLE);
                dimmerHighLabel.setText(mContext.getString(R.string.dimmer_dialog_on_label));
                dimmerLowLabel.setVisibility(View.GONE);
                mTxtDimmerLowReading.setVisibility(View.GONE);
                mTxtDimmerLowBrightness.setVisibility(View.GONE);
                break;
            case DimmerMode.ANALOG:
                mDimmerViewAnimator.setVisibility(View.VISIBLE);
                dimmerHighLabel.setText(mContext.getString(R.string.dimmer_dialog_high_label));
                dimmerLowLabel.setVisibility(View.VISIBLE);
                mTxtDimmerLowReading.setVisibility(View.VISIBLE);
                mTxtDimmerLowBrightness.setVisibility(View.VISIBLE);
                break;
            default:
                mDimmerViewAnimator.setVisibility(View.GONE);
        }
    }

    private void showBrightnessBar() {
        mBrightnessBar.setVisibility(View.VISIBLE);
        int mode = -1;
        try {
            mode = Settings.System.getInt(mContext.getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS_MODE);
        } catch (Exception e) {
            e.printStackTrace();
        }

        // change to manual mode if automatic is enabled
        if (mode == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC) {
            Settings.System.putInt(mContext.getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
        }

        // read current brightness
        try {
            mInitialBrightness = Settings.System.getInt(mContext.getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS);
        } catch (Exception e) {
            e.printStackTrace();
        }

        mBrightnessBar.setProgress(mInitialBrightness);
        mBrightnessBar.refreshDrawableState();
    }

    private void setScreenBrightness(int brightness) {
        Settings.System.putInt(mContext.getContentResolver(),
                Settings.System.SCREEN_BRIGHTNESS, brightness);

        Window window = ((Activity) mContext).getWindow();
        WindowManager.LayoutParams lp = window.getAttributes();
        lp.screenBrightness = (float) brightness / 255;
        window.setAttributes(lp);

    }

    private void hideBrightnessBar() {
        mBrightnessBar.setVisibility(View.GONE);
        setScreenBrightness(mInitialBrightness);
    }

    private void setAnalogReadingTextView(String reading, int labelResource) {
        TextView label = (TextView) mDimmerDialog.findViewById(labelResource);
        String txt = UtilityFunctions.addLeadingZeroes(reading, 5);
        txt = "[" + txt + "]";
        label.setText(txt);
    }

    private void setDigitalReadingTextView(String reading, int labelResource) {
        TextView label = (TextView) mDimmerDialog.findViewById(labelResource);
        label.setText(reading);
    }

    public void showDialog() {
        mDimmerDialog.show();
    }

    public boolean isDialogShowing() {
        return mDimmerDialog.isShowing();
    }

    public void setReading(String reading) {

        switch (mCurrentPage) {
            case WizardPage.PAGE_TWO_DIGITAL:
                mDimmerVals.highReading = -1;
                if (reading.equals("On") || reading.equals("Off")) {
                    setDigitalReadingTextView(reading, R.id.txt_dimmer_reading_digital);
                }
                break;
            case WizardPage.PAGE_TWO_ANALOG:
                if (!(reading.equals("On") || reading.equals("Off"))) {
                    mDimmerVals.highReading = Integer.parseInt(reading);
                    setAnalogReadingTextView(reading, R.id.txt_dimmer_reading_analog_high);
                }
                break;
            case WizardPage.PAGE_THREE:
                if (!(reading.equals("On") || reading.equals("Off"))) {
                    mDimmerVals.lowReading = Integer.parseInt(reading);
                    setAnalogReadingTextView(reading, R.id.txt_dimmer_reading_analog_low);
                }
                break;
            default:
        }
    }

}
