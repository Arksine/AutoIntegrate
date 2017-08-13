package com.arksine.autointegrate.activities;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.view.View;
import android.widget.Button;

import com.arksine.autointegrate.AutoIntegrate;
import com.arksine.autointegrate.adapters.LearnedButtonAdapter;
import com.arksine.autointegrate.dialogs.ButtonMapDialog;
import com.arksine.autointegrate.dialogs.DimmerCalibrationDialog;
import com.arksine.autointegrate.interfaces.McuLearnCallbacks;
import com.arksine.autointegrate.interfaces.ServiceControlInterface;
import com.arksine.autointegrate.microcontroller.ResistiveButton;
import com.arksine.autointegrate.R;
import com.arksine.autointegrate.utilities.DLog;
import com.arksine.autointegrate.utilities.LearnedButtonTouchHelper;
import com.arksine.autointegrate.utilities.UtilityFunctions;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.List;

public class ButtonLearningActivity extends AppCompatActivity {
    private static String TAG = "ButtonLearningActivity";

    private RecyclerView mButtonsRecyclerView;
    private LearnedButtonAdapter mAdapter;

    ButtonMapDialog mButtonMapDialog;
    DimmerCalibrationDialog mDimmerCalDialog;

    private final McuLearnCallbacks mcuEvents = new McuLearnCallbacks() {
        @Override
        public void onButtonClicked(int btnId) {
            String data = String.valueOf(btnId);
            if (mButtonMapDialog.isDialogShowing()) {
                // Format the data and set the controller reading in the dialog
                data = UtilityFunctions.addLeadingZeroes(data, 5);
                data = "[" + data + "]";
                mButtonMapDialog.setControllerReading(data);
            } else {
                Snackbar.make(findViewById(android.R.id.content),
                        "Click: " + data, Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        }

        @Override
        public void onDimmerToggled(boolean dimmerStatus) {
            String data = dimmerStatus ? "On" : "Off";

            Snackbar.make(findViewById(android.R.id.content),
                    "Dimmer: " + data, Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show();

            if (mDimmerCalDialog.isDialogShowing()) {
                // Todo: instead of set reading, create a function to toggle on/off
                mDimmerCalDialog.setReading(data);
            }
        }

        @Override
        public void onDimmerLevelChanged(int dimmerLevel) {
            String data = String.valueOf(dimmerLevel);
            if (mDimmerCalDialog.isDialogShowing()) {
                mDimmerCalDialog.setReading(data);
            }
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_button_learning);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        mDimmerCalDialog = new DimmerCalibrationDialog(this);
        Button showDimmerBtn = (Button) toolbar.findViewById(R.id.btn_dimmer_settings);
        showDimmerBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mDimmerCalDialog.showDialog();
            }
        });

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Add new mapped button
                mButtonMapDialog.showAddButtonDialog();
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
                mButtonMapDialog.showEditButtonDialog(currentItem, position);
            }
        };

        mAdapter = new LearnedButtonAdapter(this, cb, buttonList);
        mButtonsRecyclerView.setAdapter(mAdapter);

        mButtonMapDialog = new ButtonMapDialog(this, mAdapter);

        ItemTouchHelper.Callback callback = new LearnedButtonTouchHelper(mAdapter);
        ItemTouchHelper helper = new ItemTouchHelper(callback);
        helper.attachToRecyclerView(mButtonsRecyclerView);

    }

    @Override
    protected void onResume() {
        super.onResume();

        // Refresh the arduino connection in Learning mode
        ServiceControlInterface serviceControl = AutoIntegrate.getServiceControlInterface();
        serviceControl.refreshMcuConnection(true, mcuEvents);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // startServiceExecutionMode();
    }

    @Override
    protected void onPause() {
        super.onPause();
        DLog.v(TAG, "Paused");
        startServiceExecutionMode();
    }

    private void startServiceExecutionMode() {
        // Save the current list of buttons to shared preferences
        SharedPreferences gsonFile = getSharedPreferences(getString(R.string.gson_button_list_file),
                Context.MODE_PRIVATE);
        List<ResistiveButton> btnList = mAdapter.getButtonList();
        Gson gson = new Gson();
        String json = gson.toJson(btnList);
        gsonFile.edit().putString("ButtonList", json).apply();


        // send broadcast to toggle learning mode off
        // Refresh the arduino connection in Learning mode
        ServiceControlInterface serviceControl = AutoIntegrate.getServiceControlInterface();
        serviceControl.refreshMcuConnection(false, null);

    }

}

