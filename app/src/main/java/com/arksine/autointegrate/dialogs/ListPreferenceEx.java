package com.arksine.autointegrate.dialogs;

import android.content.Context;
import android.preference.ListPreference;
import android.preference.Preference;
import android.util.AttributeSet;

/**
 * Extended list preference that listens for item clicks
 */

public class ListPreferenceEx extends ListPreference {
    public interface ListItemClickListener {
        public void onListItemClick(Preference preference, String value);
    }

    private ListItemClickListener mListItemClickListener;

    public ListPreferenceEx(Context context) {
        super(context);
    }

    public ListPreferenceEx(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setOnListItemClickListener(ListItemClickListener listener) {
        mListItemClickListener = listener;
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        super.onDialogClosed(positiveResult);

        if (positiveResult && getEntryValues() != null && mListItemClickListener != null) {
            String value = getValue();
            mListItemClickListener.onListItemClick(this, value);
        }
    }
}
