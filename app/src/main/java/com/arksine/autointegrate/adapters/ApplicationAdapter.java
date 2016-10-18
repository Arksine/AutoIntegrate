package com.arksine.autointegrate.adapters;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.support.annotation.NonNull;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.arksine.autointegrate.utilities.AppItem;
import com.arksine.autointegrate.utilities.UtilityFunctions;

/**
 *  Array Adapter necessary to add applications with their icons to a spinner
 */

public class ApplicationAdapter extends ArrayAdapter<AppItem> {

    public ApplicationAdapter (Context context) {
        super(context, android.R.layout.simple_spinner_item, UtilityFunctions.getAppItems());
        setDropDownViewResource(android.R.layout.simple_dropdown_item_1line);
    }


    @NonNull
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        TextView label = (TextView) super.getView(position, convertView, parent);

        AppItem appItem = getItem(position);
        if (appItem == null) {
            return super.getView(position, convertView, parent);
        }

        label.setText(appItem.getItemName());
        int size = (int)label.getTextSize();
        Drawable icon = appItem.getItemImage();
        icon.setBounds(0, 0, size, size);
        label.setCompoundDrawables(icon, null, null, null);
        label.setCompoundDrawablePadding(10);

        return label;
    }

    @Override
    public View getDropDownView(int position, View convertView, ViewGroup parent) {
        TextView label = (TextView) super.getDropDownView(position, convertView, parent);

        AppItem appItem = getItem(position);
        if (appItem == null) {
            return super.getDropDownView(position, convertView, parent);
        }

        label.setText(appItem.getItemName());
        label.setCompoundDrawablesWithIntrinsicBounds(appItem.getItemImage(), null, null, null);
        label.setCompoundDrawablePadding(10);

        return label;
    }


}

