package com.arksine.autointegrate.utilities;

import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.helper.ItemTouchHelper;

import com.arksine.autointegrate.adapters.LearnedButtonAdapter;

/**
 * Helper class to implement swiping and swapping Items in the recycler view
 */

public class LearnedButtonTouchHelper extends ItemTouchHelper.SimpleCallback {
    private LearnedButtonAdapter mLearnedButtonAdapter;

    public LearnedButtonTouchHelper(LearnedButtonAdapter adapter){
        super(ItemTouchHelper.UP | ItemTouchHelper.DOWN, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT);
        this.mLearnedButtonAdapter = adapter;
    }

    @Override
    public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target) {
        mLearnedButtonAdapter.swap(viewHolder.getAdapterPosition(), target.getAdapterPosition());
        return true;
    }

    @Override
    public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {
        mLearnedButtonAdapter.remove(viewHolder.getAdapterPosition());
    }
}
