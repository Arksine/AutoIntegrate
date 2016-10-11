package com.arksine.autointegrate.Activities;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.arksine.autointegrate.Arduino.ArduinoButton;
import com.arksine.autointegrate.R;

import java.util.Collections;
import java.util.List;

/**
 * RecyclerView adapter for the RecyclerView used in the ButtonLearningActivity
 */

public class LearnedButtonAdapter extends RecyclerView.Adapter<LearnedButtonAdapter.ViewHolder> {
    private Context mContext;
    private List<ArduinoButton> mButtonList;

    public class ViewHolder extends RecyclerView.ViewHolder
            implements View.OnClickListener {
        // TODO:  add more text views for each variable
        public TextView mButtonIdTextView;

        public ViewHolder(View view) {
            super(view);
            mButtonIdTextView = (TextView) view.findViewById(R.id.button_id);
        }

        public void bindButton(ArduinoButton button){
            this.mButtonIdTextView.setText(String.valueOf(button.getId()));
        }

        @Override
        public void onClick(View v) {
            // Clicked on item
            // TODO: Replace Toast below by launching button learning dialog
            Toast.makeText(mContext, "Clicked on position: " + getAdapterPosition(),
                    Toast.LENGTH_SHORT).show();
        }
    }

    public LearnedButtonAdapter(Context context, List<ArduinoButton> buttons) {
        mContext = context;
        mButtonList = buttons;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        // create a new view
        return new ViewHolder(LayoutInflater.from(mContext)
                .inflate(R.layout.list_item_button, parent, false));

    }

    // Replace the contents of a view (invoked by the layout manager)
    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        // - get element from your dataset at this position
        // - replace the contents of the view with that element
        holder.bindButton(mButtonList.get(position));

    }

    // Return the size of your dataset (invoked by the layout manager)
    @Override
    public int getItemCount() {
        return mButtonList.size();
    }

    public void remove(int position) {
        mButtonList.remove(position);
        notifyItemRemoved(position);
    }

    public void swap(int firstPosition, int secondPosition){
        Collections.swap(mButtonList, firstPosition, secondPosition);
        notifyItemMoved(firstPosition, secondPosition);
    }

    public void add(ArduinoButton button) {
        mButtonList.add(button);
        notifyItemInserted((mButtonList.size() - 1));
    }

    public List<ArduinoButton> getButtonList() {
        return mButtonList;
    }

}
