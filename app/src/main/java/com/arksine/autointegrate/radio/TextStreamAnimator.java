package com.arksine.autointegrate.radio;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.graphics.Paint;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.widget.TextView;

import com.arksine.autointegrate.utilities.DLog;

/**
 * Appends streaming text received to a TextView using a scroll/translate animation
 */

public class TextStreamAnimator {
    private static final String TAG = TextStreamAnimator.class.getSimpleName();

    private TextView mTextView;
    private int mScrollViewWidth;
    private int mScrollDuration;
    private ObjectAnimator mTextScrollAnimation;

    public TextStreamAnimator(TextView textView) {
        mTextView = textView;
        mScrollDuration = 1000;
        mScrollViewWidth = 1000;

        initAnimation();
    }

    public void setScrollViewWidth(int width) {
        mScrollViewWidth = width;
    }

    private void initAnimation() {
        mTextView.setGravity(Gravity.END);
        ViewGroup.LayoutParams params = mTextView.getLayoutParams();
        params.width = mScrollViewWidth;
        mTextView.setLayoutParams(params);

        mTextScrollAnimation = ObjectAnimator.ofFloat(mTextView, "translationX", 0f,
                0f);
        mTextScrollAnimation.setInterpolator(new LinearInterpolator());
        mTextScrollAnimation.setDuration(mScrollDuration);

        mTextScrollAnimation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                super.onAnimationStart(animation);
                DLog.i(TAG, "Current RDS Streaming Text:\n" + mTextView.getText());
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                // If text is longer than the view, we need to start chopping it off.
                removeExcessText();
                DLog.v(TAG, "Current X: " + mTextView.getX() +
                        "\nCurrent Translate X: " + mTextView.getTranslationX());
            }
        });
    }

    private void removeExcessText() {
        Paint textPaint = mTextView.getPaint();
        CharSequence text = mTextView.getText();
        int textWidth = Math.round(textPaint.measureText(text.toString()));

        // If the amount of text twice the view width, cut off 1/4 of it
        if (textWidth >= 2*mScrollViewWidth) {
            int end = text.length();
            int start = Math.round(end / 4f);
            mTextView.setText(text.subSequence(start, end));
        }
    }

    public void addStreamingText(String incoming) {
        Paint textPaint = mTextView.getPaint();
        float incomingWidth = textPaint.measureText(incoming);
        float currentWidth = textPaint.measureText(mTextView.getText().toString());
        float translateStart;
        float translateEnd;
        if (mScrollViewWidth <= currentWidth + incomingWidth) {
            ViewGroup.LayoutParams params = mTextView.getLayoutParams();
            params.width = Math.round(currentWidth + incomingWidth);
            mTextView.setLayoutParams(params);
            translateEnd = mScrollViewWidth - (currentWidth + incomingWidth);
            mTextView.setX(translateEnd);
        } else {
            translateEnd = 0f;
        }
        translateStart = translateEnd + incomingWidth;

        mTextView.append(incoming);
        mTextView.setTranslationX(translateStart);
        mTextScrollAnimation.setFloatValues(translateStart, translateEnd);
        mTextScrollAnimation.setDuration(Math.round(incomingWidth) * 3);  // 3ms per pixel
        mTextScrollAnimation.start();
    }

    public void clear() {
        // TODO: implement
    }
}
