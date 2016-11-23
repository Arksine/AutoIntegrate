package com.arksine.autointegrate.radio;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.graphics.Paint;
import android.os.Handler;
import android.util.Log;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.widget.TextView;

import com.arksine.autointegrate.utilities.DLog;

import java.util.ArrayList;

// TODO: Have two modes, one for HD Radio/Regular RDS, and one for Streaming RDS.  Wouldn't be
//       bad if the streaming RDS version parsed words between current and previous

/**
 * Animates (Swaps) between a list of strings in a textview.  If the string is longer than the container,
 * it will scroll as long as the textview is contained in a scrollview
 */

public class TextSwapAnimator {
    private static final String TAG = TextSwapAnimator.class.getSimpleName();


    private Handler mAnimationHandler;
    private final ArrayList<String> mInfoItems = new ArrayList<>(4);
    private TextView mTextView;
    private String mCurrentString;
    private int mScrollViewWidth;

    private int mIndex = 0;
    private int mCapacity = 1;          // should always have atleast
    private int mFadeDuration;
    private int mDelayDuration;
    private int mDisplayDuration;
    private int mScrollDuration;
    private boolean mAnimationStarted = false;
    private boolean mWillScroll = false;

    private ObjectAnimator mFadeInAnimation;
    private ObjectAnimator mFadeOutAnimation;
    private ObjectAnimator mTextScrollAnimation;

    public TextSwapAnimator(TextView infoTextView) {
        this(infoTextView, 2000, 1000, 3000);

    }

    public TextSwapAnimator(TextView infoTextView, int fadeDuration, int delayBetweenItems,
                            int displayDuration) {

        mAnimationHandler = new Handler();
        mTextView = infoTextView;
        mFadeDuration = fadeDuration;
        mDelayDuration = delayBetweenItems;
        mDisplayDuration = displayDuration;
        mScrollDuration = 5000;  // This should be set programatically
        mScrollViewWidth = 1000;  // just a default width that should change

        initializeArrayMap();
        initAnimation();
    }

    public void setScrollViewWidth(int width) {
        mScrollViewWidth = width;
    }

    private void initializeArrayMap() {
        mInfoItems.add("87.9 FM");
        mInfoItems.add("");
        mInfoItems.add("");
        mInfoItems.add("");
    }

    private void initAnimation() {
        mTextView.setGravity(Gravity.CENTER);

        mFadeInAnimation = ObjectAnimator.ofFloat(mTextView, "alpha", 0f, 1f);
        mFadeInAnimation.setDuration(mFadeDuration);
        mFadeOutAnimation = ObjectAnimator.ofFloat(mTextView, "alpha", 1f, 0f);
        mFadeOutAnimation.setDuration(mFadeDuration);
        mTextScrollAnimation = ObjectAnimator.ofFloat(mTextView, "translationX", 0f, 0f);
        mTextScrollAnimation.setInterpolator(new LinearInterpolator());
        mTextScrollAnimation.setDuration(mScrollDuration);

        mFadeInAnimation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                super.onAnimationStart(animation);

                DLog.i(TAG, "Fade In");
                DLog.i(TAG, "AnimationText: " + mTextView.getText());

                mTextView.setTranslationX(0f);
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                if (mAnimationStarted) {
                    if (mWillScroll) {
                        mTextScrollAnimation.start();
                    } else {
                        // Keep the text view visible for the display duration
                        mAnimationHandler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                mFadeOutAnimation.start();
                            }
                        }, mDisplayDuration);

                    }
                }
            }
        });

        mFadeOutAnimation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                super.onAnimationStart(animation);
                DLog.i(TAG, "Fade Out");

            }

            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                if (mAnimationStarted) {
                    mIndex++;

                    while (mIndex < mCapacity) {
                        if (!(mInfoItems.get(mIndex).equals(""))) {
                            break;
                        }
                        mIndex++;
                    }

                    // If we iterate to the end of the list, reset the index to 0 (which is never null)
                    if (mIndex >= mCapacity) {
                        mIndex = 0;
                    }

                    DLog.v(TAG, "Current Index: " + mIndex);
                    DLog.v(TAG, "Current Capacity: " + mCapacity);

                    mCurrentString = mInfoItems.get(mIndex);
                    mTextView.setText(mCurrentString);
                    setupScrollAnimation();

                    mFadeInAnimation.start();
                }
            }
        });

        mTextScrollAnimation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                super.onAnimationStart(animation);
                DLog.i(TAG, "Text Scroll");
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                if (mAnimationStarted) {
                    mAnimationHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            mFadeOutAnimation.start();
                        }
                    }, mDisplayDuration);
                }
            }
        });
    }

    private void setupScrollAnimation() {
        Paint textPaint = mTextView.getPaint();
        String text = mTextView.getText().toString();
        int textWidth = Math.round(textPaint.measureText(text));
        ViewGroup.LayoutParams params = mTextView.getLayoutParams();


        DLog.i(TAG, "Total Width: " + mScrollViewWidth + "\nText Width: " + textWidth);

        if (mScrollViewWidth >= textWidth) {
            // text width is smaller than the view size, no need to animate
            params.width = mScrollViewWidth;
            mTextView.setLayoutParams(params);
            mWillScroll = false;
        } else {

            params.width = textWidth;
            mTextView.setLayoutParams(params);
            mWillScroll = true;
            float xTranslate = textWidth - mScrollViewWidth;
            mTextScrollAnimation.setFloatValues(0f, -xTranslate);
            mTextScrollAnimation.setDuration((int) xTranslate * 5);   // 5 ms per pixel

        }
    }

    public void setTextItem(RadioKey.Command key, String item) {

        int idx;
        switch (key) {
            case TUNE:
                idx = 0;
                break;
            case HD_TITLE:
                idx = 1;
                break;
            case HD_ARTIST:
                idx = 2;
                break;
            case HD_CALLSIGN:
                idx = 3;
                break;
            case RDS_RADIO_TEXT:
                idx = 1;
                break;
            case RDS_GENRE:
                idx = 2;
                break;
            case RDS_PROGRAM_SERVICE:
                idx = 3;
                item = mInfoItems.get(idx) + item;  // we append the item to the current string for program service
                break;
            default:
                DLog.i(TAG, "Invalid Command Key");
                return;
        }

        if (mInfoItems.get(idx).equals("") && !item.equals("")) {
            mCapacity++;
        }

        mInfoItems.set(idx, item);

        if (mCapacity > 1 && !mAnimationStarted) {
            startAnimation();
        }
    }

    public void clearTextItem(RadioKey.Command key) {

        int idx;
        switch (key) {
            case TUNE:
                Log.i(TAG, "Frequency should not be cleared");
                return;
            case HD_TITLE:
                idx = 1;
                break;

            case HD_ARTIST:
                idx = 2;
                break;

            case HD_CALLSIGN:
                idx = 3;
                break;

            case RDS_RADIO_TEXT:
                idx = 1;
                break;

            case RDS_GENRE:
                idx = 2;
                break;
            case RDS_PROGRAM_SERVICE:
                idx = 3;
                break;
            default:
                Log.i(TAG, "Invalid Command Key");
                return;
        }

        if (!(mInfoItems.get(idx).equals(""))) {
            mInfoItems.set(idx, "");
            mCapacity--;

            if (mCapacity <= 1) {
                stopAnimation();
            }
        }
    }

    public void startAnimation() {
        if (mCapacity > 1) {
            DLog.i(TAG, "Animation Started");
            mAnimationStarted = true;
            mCurrentString = mInfoItems.get(mIndex);
            mTextView.setText(mCurrentString);
            mFadeOutAnimation.start();
        } else {
            mCurrentString = mInfoItems.get(0);
            mTextView.setText(mCurrentString);
        }
    }

    public void stopAnimation() {
        mAnimationStarted = false;
        mFadeOutAnimation.cancel();
        mFadeInAnimation.cancel();
        mTextScrollAnimation.cancel();
        mTextView.setAlpha(1f);
        mTextView.setTranslationX(0f);
    }

    public void resetAnimator() {
        stopAnimation();
        mIndex = 0;
        mCapacity = 1;
        mInfoItems.set(1, "");
        mInfoItems.set(2, "");
        mInfoItems.set(3, "");
        mCurrentString = mInfoItems.get(0);
        mTextView.setText(mCurrentString);
        ViewGroup.LayoutParams params = mTextView.getLayoutParams();
        params.width = mScrollViewWidth;
        mTextView.setLayoutParams(params);
    }
}
