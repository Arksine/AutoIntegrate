package com.arksine.autointegrate.Fragments;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;


import com.arksine.autointegrate.R;

/**
 *
 */
public class ButtonLearningDialogFragment extends DialogFragment {

    // TODO: add broadcast receiver to listen for arduino button events

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        LayoutInflater inflater = getActivity().getLayoutInflater();

        // Inflate and set the layout for the dialog
        // Pass null as the parent view because its going in the dialog layout
        builder.setView(inflater.inflate(R.layout.dialog_button_learning, null))
                // Add action buttons
                .setPositiveButton(R.string.dialog_button_confirm,
                        new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        // TODO: Get data from dialog and
                        // add arduinobutton to the recyclerview adapter
                    }
                })
                .setNegativeButton(R.string.dialog_button_cancel,
                        new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        ButtonLearningDialogFragment.this.getDialog().cancel();
                    }
                });
        return builder.create();
    }
}
