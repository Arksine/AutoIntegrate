package com.arksine.autointegrate.radio;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.graphics.Paint;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.widget.TextView;

import timber.log.Timber;


/**
 * Appends streaming text received to a TextView using a scroll/translate animation
 */

public class TextStreamAnimator {

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
                Timber.d("Current RDS Streaming Text:\n%s", mTextView.getText());
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                // If text is longer than the view, we need to start chopping it off.
                removeExcessText();
                Timber.d("Current X: %d\nCurrent Translate X: %d",
                        mTextView.getX(), mTextView.getTranslationX());
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
        mTextView.setText("");
        ViewGroup.LayoutParams params = mTextView.getLayoutParams();
        params.width = mScrollViewWidth;
        mTextView.setLayoutParams(params);
        mTextView.setTranslationX(0f);
    }
}
