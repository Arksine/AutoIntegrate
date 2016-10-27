package com.arksine.autointegrate.dialogs;

import android.content.Context;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.PopupWindow;

import com.arksine.autointegrate.R;

/**
 * Displays a popup window showing help to the user
 */

public class HelpDialog {

    private Context mContext;
    private PopupWindow mWindow;
    private View mContentView;
    private LayoutInflater mInflater;

    public HelpDialog(Context context, int layoutResource) {
        this.mContext = context;
        mWindow = new PopupWindow(mContext);
        mInflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mContentView = mInflater.inflate(layoutResource, null);

        Button dismissBtn = (Button) mContentView.findViewById(R.id.btn_help_dismiss);
        dismissBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mWindow.dismiss();
            }
        });
    }

    public void showHelpDialog(View anchor) {
        mWindow.setWidth(ViewGroup.LayoutParams.WRAP_CONTENT);
        mWindow.setHeight(ViewGroup.LayoutParams.WRAP_CONTENT);

        mWindow.setAnimationStyle(android.R.style.Animation_Dialog);
        mWindow.setOutsideTouchable(false);
        mWindow.setTouchable(true);
        mWindow.setFocusable(true);
        mWindow.setBackgroundDrawable(new BitmapDrawable());
        mWindow.setContentView(mContentView);

        int screenPosition[] = new int[2];
        anchor.getLocationOnScreen(screenPosition);
        Rect anchorRect = new Rect(screenPosition[0], screenPosition[1],
                screenPosition[0] + anchor.getWidth(), screenPosition[1] + anchor.getHeight());

        mContentView.measure(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);

        int contentViewWidth = mContentView.getMeasuredWidth();
        int contentViewHeight = mContentView.getMeasuredHeight();
        int winPosX = anchorRect.left - contentViewWidth;
        int winPosY = anchorRect.top + anchorRect.height() / 4;

        mWindow.showAtLocation(anchor, Gravity.NO_GRAVITY, winPosX, winPosY);
    }
}
