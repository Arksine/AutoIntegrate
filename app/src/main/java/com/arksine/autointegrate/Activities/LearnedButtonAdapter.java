package com.arksine.autointegrate.activities;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.arksine.autointegrate.microcontroller.ResistiveButton;
import com.arksine.autointegrate.R;

import java.util.Collections;
import java.util.List;

/**
 * RecyclerView adapter for the RecyclerView used in the ButtonLearningActivity
 */

public class LearnedButtonAdapter extends RecyclerView.Adapter<LearnedButtonAdapter.ViewHolder> {

    private static String TAG = "LearnedButtonAdapter";

    private Context mContext;
    private List<ResistiveButton> mButtonList;

    interface ItemClickCallback {
        void OnItemClick(ResistiveButton currentItem, int position);
    }

    private ItemClickCallback mItemClickCallback;

    class ViewHolder extends RecyclerView.ViewHolder {
        // TODO:  add an icon?
        TextView mButtonValue;
        TextView mDebounceValue;
        TextView mClickAction;
        TextView mHoldAction;

        ViewHolder(View view) {
            super(view);
            mButtonValue = (TextView) view.findViewById(R.id.txt_button_value);
            mDebounceValue = (TextView) view.findViewById(R.id.txt_debounce_value);
            mClickAction = (TextView) view.findViewById(R.id.txt_click_action);
            mHoldAction = (TextView) view.findViewById(R.id.txt_hold_action);
        }

        void bindButton(ResistiveButton button){
            String id = button.getIdAsString();
            int debounce = button.getTolerance();
            String click = button.getClickType();
            String hold = button.getHoldType();

            if (!click.equals("None")) {
                click += " - " + button.getClickAction();
            }

            if (!hold.equals("None")) {
                hold += " - " + button.getHoldAction();
            }

            if (button.isMultiplied()) {
                debounce = debounce * 10;
            }

            this.mButtonValue.setText(id);
            this.mDebounceValue.setText(String.valueOf(debounce));
            this.mClickAction.setText(click);
            this.mHoldAction.setText(hold);

        }

    }

    public LearnedButtonAdapter(Context context, ItemClickCallback cb,
                                List<ResistiveButton> buttons) {
        mContext = context;
        mItemClickCallback = cb;
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
    public void onBindViewHolder(final ViewHolder holder, int position) {
        // - replace the contents of the view with that element
        holder.bindButton(mButtonList.get(position));
        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int pos = holder.getAdapterPosition();
                mItemClickCallback.OnItemClick(mButtonList.get(pos), pos);

                Log.d(TAG, "Clicked on position: " + pos);
            }
        });

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

    public void add(ResistiveButton button) {
        mButtonList.add(button);
        notifyItemInserted((mButtonList.size() - 1));
    }

    public void editItem(ResistiveButton button, int position) {
        ResistiveButton current = mButtonList.get(position);
        current.assign(button);
        notifyItemChanged(position);
    }


    public List<ResistiveButton> getButtonList() {
        return mButtonList;
    }

}
