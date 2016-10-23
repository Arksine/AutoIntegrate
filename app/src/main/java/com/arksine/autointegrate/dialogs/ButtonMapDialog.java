package com.arksine.autointegrate.dialogs;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import com.arksine.autointegrate.R;
import com.arksine.autointegrate.adapters.ApplicationAdapter;
import com.arksine.autointegrate.adapters.LearnedButtonAdapter;
import com.arksine.autointegrate.microcontroller.ResistiveButton;
import com.arksine.autointegrate.utilities.AppItem;
import com.orhanobut.dialogplus.DialogPlus;
import com.orhanobut.dialogplus.ViewHolder;

import java.util.ArrayList;

/**
 * Handles lifecycle of the Button Mapping Dialog
 */

public class ButtonMapDialog {

    private static String TAG = "ButtonMapDialog";
    private Context mContext;
    private LearnedButtonAdapter mLearnedButtonAdapter;

    // Dialog Vars
    private DialogPlus mButtonDialog;
    private TextView mDialogTitle;
    private TextView mControllerReading;
    private Spinner mDebounceSpinner;
    private Spinner mClickActionSpinner;
    private Spinner mClickActionTypeSpinner;
    private Spinner mHoldActionSpinner;
    private Spinner mHoldActionTypeSpinner;
    private CheckBox mDebounceMultiplier;

    private ArrayList<String> mTaskerTasks;

    private boolean mEditMode = false;
    private int mEditPosition = 0;

    public ButtonMapDialog(Context context, LearnedButtonAdapter adapter) {
        mContext = context;
        mLearnedButtonAdapter = adapter;
        buildDialog();
        enumerateTasks();
    }

    private void buildDialog() {
        mButtonDialog = DialogPlus.newDialog(mContext)
                .setContentHolder(new ViewHolder(R.layout.dialog_button_learning))
                .setHeader(R.layout.dialog_button_learning_header)
                .setFooter(R.layout.dialog_button_learning_footer)
                //.setExpanded(true)
                .setContentWidth(ViewGroup.LayoutParams.WRAP_CONTENT)
                .setContentHeight(ViewGroup.LayoutParams.WRAP_CONTENT)
                .setGravity(Gravity.CENTER)
                //.setCancelable(true)
                .create();

        mDialogTitle = (TextView)mButtonDialog.findViewById(R.id.txt_dialog_title);
        mControllerReading = (TextView)mButtonDialog.findViewById(R.id.txt_controller_reading);
        mDebounceSpinner = (Spinner) mButtonDialog.findViewById(R.id.spn_debounce);
        mClickActionSpinner = (Spinner) mButtonDialog.findViewById(R.id.spn_click_action);
        mClickActionTypeSpinner = (Spinner) mButtonDialog.findViewById(R.id.spn_click_action_type);
        mHoldActionSpinner = (Spinner) mButtonDialog.findViewById(R.id.spn_hold_action);
        mHoldActionTypeSpinner = (Spinner) mButtonDialog.findViewById(R.id.spn_hold_action_type);
        mDebounceMultiplier = (CheckBox) mButtonDialog.findViewById(R.id.chk_debounce_multiplier);

        Button mDialogSaveBtn = (Button) mButtonDialog.findViewById(R.id.btn_dialog_save);
        Button mDialogCancelBtn = (Button) mButtonDialog.findViewById(R.id.btn_dialog_cancel);
        ImageButton mDialogHelpBtn = (ImageButton) mButtonDialog.findViewById(R.id.btn_help);

        mDialogSaveBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ResistiveButton resBtn = new ResistiveButton();

                String ident = mControllerReading.getText().toString();
                int numId = Integer.parseInt(ident.substring(1, ident.length() - 1));
                resBtn.setId(numId);
                resBtn.setTolerance(Integer.parseInt((String)mDebounceSpinner.getSelectedItem()));
                resBtn.setMultiplied(mDebounceMultiplier.isChecked());


                resBtn.setClickType((String)mClickActionTypeSpinner.getSelectedItem());
                if (resBtn.getClickType().equals("Application")) {
                    String packageName = ((AppItem) mClickActionSpinner.getSelectedItem())
                            .getPackageName();
                    resBtn.setClickAction(packageName);
                } else {
                    resBtn.setClickAction((String) mClickActionSpinner.getSelectedItem());
                }

                resBtn.setHoldType((String)mHoldActionTypeSpinner.getSelectedItem());
                if (resBtn.getHoldType().equals("Application")) {
                    String packageName = ((AppItem) mHoldActionSpinner.getSelectedItem())
                            .getPackageName();
                    resBtn.setHoldAction(packageName);
                } else {
                    resBtn.setHoldAction((String) mHoldActionSpinner.getSelectedItem());
                }

                if (mEditMode) {
                    mLearnedButtonAdapter.editItem(resBtn, mEditPosition);
                } else {
                    mLearnedButtonAdapter.add(resBtn);
                }

                mButtonDialog.dismiss();
            }
        });

        mDialogCancelBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mButtonDialog.dismiss();
            }
        });

        mDialogHelpBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // TODO: launch help popup
            }
        });

        mDebounceMultiplier.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setDebounceSpinnerAdapter(isChecked);
            }
        });

        // Setup action spinners
        mClickActionTypeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                setActionSpinner(true, position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        mHoldActionTypeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                setActionSpinner(false, position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

    }

    public void showAddButtonDialog() {
        // We are adding a button, not editing one
        mEditMode = false;
        mDialogTitle.setText(mContext.getString(R.string.dialog_title_add));
        mControllerReading.setText(mContext.getString(R.string.dialog_controller_reading_default));
        mDebounceMultiplier.setChecked(false);
        mDebounceSpinner.setSelection(0);
        mClickActionTypeSpinner.setSelection(0);
        mClickActionSpinner.setSelection(0);
        mHoldActionTypeSpinner.setSelection(0);
        mHoldActionSpinner.setSelection(0);
        mButtonDialog.show();
    }

    public void showEditButtonDialog(ResistiveButton currentItem, int position) {
        mEditMode = true;
        mEditPosition = position;
        mDialogTitle.setText(mContext.getString(R.string.dialog_title_edit));

        mControllerReading.setText(currentItem.getIdAsString());
        mDebounceMultiplier.setChecked(currentItem.isMultiplied());
        mDebounceSpinner.setSelection(findSpinnerIndex(mDebounceSpinner,
                String.valueOf(currentItem.getTolerance())));

        mClickActionTypeSpinner.setSelection(findSpinnerIndex(mClickActionTypeSpinner,
                currentItem.getClickType()));
        mClickActionSpinner.setSelection(findSpinnerIndex(mClickActionSpinner,
                currentItem.getClickAction()));
        mHoldActionTypeSpinner.setSelection(findSpinnerIndex(mHoldActionTypeSpinner,
                currentItem.getHoldType()));
        mHoldActionSpinner.setSelection(findSpinnerIndex(mHoldActionSpinner,
                currentItem.getHoldAction()));

        mButtonDialog.show();
    }

    public boolean isDialogShowing() {
        return mButtonDialog.isShowing();
    }

    public void setControllerReading(String reading) {
        mControllerReading.setText(reading);
    }

    private int findSpinnerIndex(Spinner spinner, String value) {
        int index = 0;
        for (int i = 0; i < spinner.getCount(); i++) {
            if (spinner.getItemAtPosition(i).equals(value)) {
                index = i;
                break;
            }
        }
        return index;
    }

    private void setDebounceSpinnerAdapter(boolean isMultiplied) {
        ArrayAdapter<String> debounceAdapter;

        // get the current position so we can reset it
        int index = mDebounceSpinner.getSelectedItemPosition();

        if (isMultiplied) {
            debounceAdapter = new ArrayAdapter<>(mContext, android.R.layout.simple_spinner_item,
                    mContext.getResources().getStringArray(R.array.dialog_spinner_debounce_multiplied));
        } else {
            debounceAdapter = new ArrayAdapter<>(mContext, android.R.layout.simple_spinner_item,
                    mContext.getResources().getStringArray(R.array.dialog_spinner_debounce));
        }

        debounceAdapter.setDropDownViewResource(android.R.layout.simple_dropdown_item_1line);
        mDebounceSpinner.setAdapter(debounceAdapter);
        mDebounceSpinner.setSelection(index);
    }

    /**
     * Populate an action spinner based on its type
     * @param isClickAction - determines if the action spinner to be populated is click or hold
     * @param pos - position of the actionType spinner that was selected
     */
    private void setActionSpinner(boolean isClickAction, int pos) {
        Spinner actionSpinner, typeSpinner;

        if (isClickAction) {
            actionSpinner = mClickActionSpinner;
            typeSpinner = mClickActionTypeSpinner;
        } else {
            actionSpinner = mHoldActionSpinner;
            typeSpinner = mHoldActionTypeSpinner;
        }

        ArrayAdapter<String> actionAdapter;
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        LinearLayout.LayoutParams actionTypeParams = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        actionParams.weight = 1;
        actionTypeParams.weight = 1;


        switch (pos) {
            case 0:     // Action Type: None
                actionSpinner.setVisibility(View.GONE);
                actionAdapter = new ArrayAdapter<>(mContext, android.R.layout.simple_spinner_item,
                        mContext.getResources().getStringArray(R.array.dialog_spinner_empty));
                actionAdapter.setDropDownViewResource(android.R.layout.simple_dropdown_item_1line);
                actionSpinner.setAdapter(actionAdapter);

                actionParams.weight = 0;
                actionTypeParams.weight = 2;
                actionSpinner.setLayoutParams(actionParams);
                typeSpinner.setLayoutParams(actionTypeParams);
                break;
            case 1:     // Action Type: Volume
                actionSpinner.setVisibility(View.VISIBLE);
                actionAdapter = new ArrayAdapter<>(mContext, android.R.layout.simple_spinner_item,
                        mContext.getResources().getStringArray(R.array.dialog_action_volume));
                actionAdapter.setDropDownViewResource(android.R.layout.simple_dropdown_item_1line);
                actionSpinner.setAdapter(actionAdapter);

                actionSpinner.setLayoutParams(actionParams);
                typeSpinner.setLayoutParams(actionTypeParams);
                break;
            case 2:     // Action Type: Media
                actionSpinner.setVisibility(View.VISIBLE);
                actionAdapter = new ArrayAdapter<>(mContext, android.R.layout.simple_spinner_item,
                        mContext.getResources().getStringArray(R.array.dialog_action_media));
                actionAdapter.setDropDownViewResource(android.R.layout.simple_dropdown_item_1line);
                actionSpinner.setAdapter(actionAdapter);

                actionSpinner.setLayoutParams(actionParams);
                typeSpinner.setLayoutParams(actionTypeParams);
                break;
            case 3:     // Action Type: Integrated
                actionSpinner.setVisibility(View.VISIBLE);
                actionAdapter = new ArrayAdapter<>(mContext, android.R.layout.simple_spinner_item,
                        mContext.getResources().getStringArray(R.array.dialog_action_integrated));
                actionAdapter.setDropDownViewResource(android.R.layout.simple_dropdown_item_1line);
                actionSpinner.setAdapter(actionAdapter);

                actionSpinner.setLayoutParams(actionParams);
                typeSpinner.setLayoutParams(actionTypeParams);
                break;
            case 4:     // Action Type: Application
                actionSpinner.setVisibility(View.VISIBLE);
                ApplicationAdapter appAdapter = new ApplicationAdapter(mContext);
                actionSpinner.setAdapter(appAdapter);

                actionSpinner.setLayoutParams(actionParams);
                typeSpinner.setLayoutParams(actionTypeParams);
                break;
            case 5:     // Action Type: Tasker
                actionSpinner.setVisibility(View.VISIBLE);
                if (mTaskerTasks != null) {
                    actionAdapter = new ArrayAdapter<>(mContext, android.R.layout.simple_spinner_item,
                            mTaskerTasks);
                } else {
                    actionAdapter = new ArrayAdapter<>(mContext, android.R.layout.simple_spinner_item,
                            mContext.getResources().getStringArray(R.array.dialog_spinner_empty));
                }
                actionSpinner.setAdapter(actionAdapter);

                actionSpinner.setLayoutParams(actionParams);
                typeSpinner.setLayoutParams(actionTypeParams);
                break;
            default:
                actionSpinner.setVisibility(View.GONE);
                actionParams.weight = 0;
                actionTypeParams.weight = 2;
                actionSpinner.setLayoutParams(actionParams);
                typeSpinner.setLayoutParams(actionTypeParams);
                break;
        }
    }

    private void enumerateTasks() {
        Cursor cursor = mContext.getContentResolver()
                .query(Uri.parse( "content://net.dinglisch.android.tasker/tasks" ),
                        null, null, null, null );

        if (cursor != null) {
            mTaskerTasks = new ArrayList<>();
            int nameColumn = cursor.getColumnIndex("name");

            while (cursor.moveToNext()) {
                mTaskerTasks.add(cursor.getString(nameColumn));
            }

            cursor.close();
        }
    }
}
