package com.arksine.autointegrate.activities;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;


import com.arksine.autointegrate.microcontroller.ResistiveButton;
import com.arksine.autointegrate.R;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.orhanobut.dialogplus.DialogPlus;
import com.orhanobut.dialogplus.ViewHolder;

import java.lang.reflect.Type;
import java.util.List;

public class ButtonLearningActivity extends AppCompatActivity {

    private RecyclerView mButtonsRecyclerView;
    private LearnedButtonAdapter mAdapter;

    private boolean mEditMode = false;
    private int mEditPosition = 0;

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

    // broadcast reciever to receive button press values
    private BroadcastReceiver mButtonReciever = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(getString(R.string.ACTION_CONTROLLER_LEARN_DATA))) {
                String command = intent.getStringExtra("Command");
                if (command.equals("click")) {
                    String data = intent.getStringExtra("Data");
                    if (mButtonDialog.isShowing()) {
                        // Format the data and set the controller reading in the dialog
                        int targetLength = 5 - data.length();
                        for (int i = 0; i < targetLength; i++) {
                            data = "0" + data;
                        }

                        data = "[" + data + "]";
                        mControllerReading.setText(data);
                    } else {
                        Snackbar.make(findViewById(android.R.id.content),
                                "Click: " + data, Snackbar.LENGTH_LONG)
                                .setAction("Action", null).show();
                    }
                }
            }
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_button_learning);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        buildDialog();

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // We are adding a button, not editing one
                mEditMode = false;
                mDialogTitle.setText(getString(R.string.dialog_title_add));
                mControllerReading.setText(getString(R.string.dialog_controller_reading_default));
                mDebounceSpinner.setSelection(0);
                mDebounceMultiplier.setChecked(false);
                mClickActionTypeSpinner.setSelection(0);
                mClickActionSpinner.setSelection(0);
                mHoldActionTypeSpinner.setSelection(0);
                mHoldActionSpinner.setSelection(0);
                mButtonDialog.show();


            }
        });

        // Get current GSON list from shared preferences
        Gson gson = new Gson();
        SharedPreferences gsonFile = getSharedPreferences(getString(R.string.gson_button_list_file),
                Context.MODE_PRIVATE);
        String json = gsonFile.getString("ButtonList", "[]");
        Type collectionType = new TypeToken<List<ResistiveButton>>(){}.getType();
        List<ResistiveButton> buttonList = gson.fromJson(json, collectionType);

        // set the recycler view
        mButtonsRecyclerView = (RecyclerView) findViewById(R.id.learned_buttons_recycler_view);
        mButtonsRecyclerView.setHasFixedSize(true);
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(this);
        linearLayoutManager.setOrientation(LinearLayoutManager.VERTICAL);
        mButtonsRecyclerView.setLayoutManager(linearLayoutManager);

        // Create a callback so we can launch a Dialog for each item
        LearnedButtonAdapter.ItemClickCallback cb = new LearnedButtonAdapter.ItemClickCallback() {
            @Override
            public void OnItemClick(ResistiveButton currentItem, int position) {
                // We are editing a current button
                mEditMode = true;
                mEditPosition = position;
                mDialogTitle.setText(getString(R.string.dialog_title_edit));

                mControllerReading.setText(currentItem.getIdAsString());
                mDebounceSpinner.setSelection(findSpinnerIndex(mDebounceSpinner,
                        String.valueOf(currentItem.getTolerance())));
                mDebounceMultiplier.setChecked(currentItem.isMultiplied());
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
        };

        mAdapter = new LearnedButtonAdapter(this, cb, buttonList);
        mButtonsRecyclerView.setAdapter(mAdapter);

        ItemTouchHelper.Callback callback = new LearnedButtonTouchHelper(mAdapter);
        ItemTouchHelper helper = new ItemTouchHelper(callback);
        helper.attachToRecyclerView(mButtonsRecyclerView);

        IntentFilter filter = new IntentFilter(getString(R.string.ACTION_CONTROLLER_LEARN_DATA));
        LocalBroadcastManager.getInstance(this).registerReceiver(mButtonReciever, filter);

        // Refresh the arduino connection in Learning mode
        Intent refreshControllerIntent =
                new Intent(getString(R.string.ACTION_REFRESH_CONTROLLER_CONNECTION));
        refreshControllerIntent.putExtra("LearningMode", true);
        LocalBroadcastManager.getInstance(this).sendBroadcast(refreshControllerIntent);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Save the current list of buttons to shared preferences
        SharedPreferences gsonFile = getSharedPreferences(getString(R.string.gson_button_list_file),
                Context.MODE_PRIVATE);
        List<ResistiveButton> btnList = mAdapter.getButtonList();
        Gson gson = new Gson();
        String json = gson.toJson(btnList);
        gsonFile.edit().putString("ButtonList", json).apply();


        // send broadcast to toggle learning mode off
        // Refresh the arduino connection in Learning mode
        Intent refreshControllerIntent =
                new Intent(getString(R.string.ACTION_REFRESH_CONTROLLER_CONNECTION));
        refreshControllerIntent.putExtra("LearningMode", false);
        LocalBroadcastManager.getInstance(this).sendBroadcast(refreshControllerIntent);

        LocalBroadcastManager.getInstance(this).unregisterReceiver(mButtonReciever);
    }

    private void buildDialog() {
        mButtonDialog = DialogPlus.newDialog(this)
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
                resBtn.setClickAction((String)mClickActionSpinner.getSelectedItem());
                resBtn.setHoldType((String)mHoldActionTypeSpinner.getSelectedItem());
                resBtn.setHoldAction((String)mHoldActionSpinner.getSelectedItem());

                if (mEditMode) {
                    mAdapter.editItem(resBtn, mEditPosition);
                } else {
                    mAdapter.add(resBtn);
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
                actionAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item,
                    getResources().getStringArray(R.array.dialog_spinner_empty));
                actionAdapter.setDropDownViewResource(android.R.layout.simple_dropdown_item_1line);
                actionSpinner.setAdapter(actionAdapter);

                actionParams.weight = 0;
                actionTypeParams.weight = 2;
                actionSpinner.setLayoutParams(actionParams);
                typeSpinner.setLayoutParams(actionTypeParams);
                break;
            case 1:     // Action Type: Volume
                actionSpinner.setVisibility(View.VISIBLE);
                actionAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item,
                        getResources().getStringArray(R.array.dialog_action_volume));
                actionAdapter.setDropDownViewResource(android.R.layout.simple_dropdown_item_1line);
                actionSpinner.setAdapter(actionAdapter);

                actionSpinner.setLayoutParams(actionParams);
                typeSpinner.setLayoutParams(actionTypeParams);
                break;
            case 2:     // Action Type: Media
                actionSpinner.setVisibility(View.VISIBLE);
                actionAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item,
                        getResources().getStringArray(R.array.dialog_action_media));
                actionAdapter.setDropDownViewResource(android.R.layout.simple_dropdown_item_1line);
                actionSpinner.setAdapter(actionAdapter);

                actionSpinner.setLayoutParams(actionParams);
                typeSpinner.setLayoutParams(actionTypeParams);
                break;
            case 3:     // Action Type: Integrated
                actionSpinner.setVisibility(View.VISIBLE);
                actionAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item,
                        getResources().getStringArray(R.array.dialog_action_integrated));
                actionAdapter.setDropDownViewResource(android.R.layout.simple_dropdown_item_1line);
                actionSpinner.setAdapter(actionAdapter);

                actionSpinner.setLayoutParams(actionParams);
                typeSpinner.setLayoutParams(actionTypeParams);
                break;
            case 4:     // Action Type: Application
                actionSpinner.setVisibility(View.VISIBLE);
                actionSpinner.setLayoutParams(actionParams);
                typeSpinner.setLayoutParams(actionTypeParams);
                // TODO: get list of apps.  Alternatively just use tasker to launch apps
                break;
            case 5:     // Action Type: Tasker
                actionSpinner.setVisibility(View.VISIBLE);
                actionSpinner.setLayoutParams(actionParams);
                typeSpinner.setLayoutParams(actionTypeParams);
                // TODO: enumerate custom tasks
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

}

