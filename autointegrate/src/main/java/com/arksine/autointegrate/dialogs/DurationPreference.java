package com.arksine.autointegrate.dialogs;

import android.content.Context;
import android.content.res.TypedArray;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.view.View;
import android.widget.NumberPicker;

import com.arksine.autointegrate.R;

import java.util.Calendar;
import java.util.GregorianCalendar;

/**
 * Custom Preference to select a timeout
 */

public class DurationPreference extends DialogPreference {
    private Calendar calendar;
    private NumberPicker minutesPicker;
    private NumberPicker secondsPicker;

    public DurationPreference(Context context) {
        this(context, null);
    }

    public DurationPreference(Context context, AttributeSet attrs) {
        this(context, attrs, android.R.attr.dialogPreferenceStyle);
    }

    public DurationPreference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        setPositiveButtonText(context.getString(android.R.string.ok));
        setNegativeButtonText(context.getString(android.R.string.cancel));
        calendar = new GregorianCalendar();
    }

    @Override
    protected View onCreateDialogView() {
        View view = View.inflate(getContext(), R.layout.duration_preference, null);

        minutesPicker = (NumberPicker) view.findViewById(R.id.minutes_picker);
        minutesPicker.setMinValue(0);
        minutesPicker.setMaxValue(60);

        secondsPicker = (NumberPicker) view.findViewById(R.id.seconds_picker);
        secondsPicker.setMinValue(0);
        secondsPicker.setMaxValue(59);

        return view;
    }

    @SuppressWarnings("deprecation")
    @Override
    protected void onBindDialogView(View view) {
        super.onBindDialogView(view);
        minutesPicker.setValue(calendar.get(Calendar.MINUTE));
        secondsPicker.setValue(calendar.get(Calendar.SECOND));
    }

    @SuppressWarnings("deprecation")
    @Override
    protected void onDialogClosed(boolean positiveResult) {
        super.onDialogClosed(positiveResult);

        if (positiveResult) {
            calendar.set(Calendar.MINUTE, minutesPicker.getValue());
            calendar.set(Calendar.SECOND, secondsPicker.getValue());

            setSummary(getSummary());

            if (callChangeListener(calendar.getTimeInMillis())) {
                persistLong(calendar.getTimeInMillis());
                notifyChanged();
            }
        }
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        return (a.getString(index));
    }

    @Override
    protected void onSetInitialValue(boolean restorePersistedValue, Object defaultValue) {
        if (restorePersistedValue) {
            calendar.setTimeInMillis(getPersistedLong(0));
        } else {
            if (defaultValue == null) {
                calendar.setTimeInMillis(0);
            } else {
                calendar.setTimeInMillis(Long.parseLong((String) defaultValue));
            }
            persistLong(calendar.getTimeInMillis());
        }
        setSummary(getSummary());
    }

    @Override
    public CharSequence getSummary() {
        if (calendar == null)
            return null;

        return calendar.get(Calendar.MINUTE) + " Minutes : "
                + calendar.get(Calendar.SECOND) + " Seconds";
    }

}
