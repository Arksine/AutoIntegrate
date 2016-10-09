package com.arksine.autointegrate.Activities;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;


import com.arksine.autointegrate.Arduino.ArduinoButton;
import com.arksine.autointegrate.Fragments.ButtonLearningDialogFragment;
import com.arksine.autointegrate.R;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.orhanobut.dialogplus.DialogPlus;
import com.orhanobut.dialogplus.DialogPlusBuilder;
import com.orhanobut.dialogplus.ViewHolder;

import java.lang.reflect.Type;
import java.util.List;

public class ButtonLearningActivity extends AppCompatActivity {

    private RecyclerView mButtonsRecyclerView;
    private LearnedButtonAdapter mAdapter;
    private DialogPlus buttonDialog;

    // TODO: add broadcast reciever to receive button press values


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_button_learning);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        buttonDialog = DialogPlus.newDialog(this)
                .setExpanded(true, 300)
                .setContentHolder(new ViewHolder(R.layout.dialog_button_learning))
                .setContentWidth(ViewGroup.LayoutParams.WRAP_CONTENT)
                .setGravity(Gravity.CENTER)
                .setCancelable(true)
                .create();

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // TODO:  Pop up a dialog to add a new button here

                // TODO: use a snackbar to show button values when not in a dialog
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();

                // TODO:  the code below is for testing
                ArduinoButton test = new ArduinoButton(10, 10, "hello", "world");
                mAdapter.add(test);
                buttonDialog.show();


            }
        });

        // Get current GSON list from shared preferences
        Gson gson = new Gson();
        SharedPreferences gsonFile = getSharedPreferences(getString(R.string.gson_button_list_file),
                Context.MODE_PRIVATE);
        String json = gsonFile.getString("List<ArduinoButton>", "[]");
        Type collectionType = new TypeToken<List<ArduinoButton>>(){}.getType();
        List<ArduinoButton> buttonList = gson.fromJson(json, collectionType);

        // set the recycler view
        mButtonsRecyclerView = (RecyclerView) findViewById(R.id.learned_buttons_recycler_view);
        mButtonsRecyclerView.setHasFixedSize(true);
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(this);
        linearLayoutManager.setOrientation(LinearLayoutManager.VERTICAL);
        mButtonsRecyclerView.setLayoutManager(linearLayoutManager);


        mAdapter = new LearnedButtonAdapter(this, buttonList);
        mButtonsRecyclerView.setAdapter(mAdapter);

        // TODO: add itemtouchhelper

        // TODO: make sure service is started and arduino is connected, send broadcast
        //       to put service into learning mode
    }

    // TODO: override onDestroy to save the current buttonlist to saved preferences and put
    //       service into command exection mode

}

