package com.arksine.autointegrate.utilities;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.graphics.Paint;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.widget.TextView;

import com.arksine.autointegrate.radio.RadioKey;

import java.util.ArrayList;

// TODO: RDS Program service seems to stream in, but its very unreliable. Data is mispelled, etc.
//       Regardless, it comes in so often that it confuses the animation. I need to queue it,
//       display the queue every so often (3 seconds?), then empty it


/**
 * Animates between a list of strings in a textview.  If the string is longer than the container,
 * it will scroll as long as the textview is contained in a scrollview
 */

public class TextInfoAnimator {
    private static final String TAG = TextInfoAnimator.class.getSimpleName();

    private Handler mAnimationHandler;
    private final ArrayList<String> mInfoItems = new ArrayList<>(4);
    private TextView mRadioInfoTextView;
    private int mIndex = 0;
    private int mCapacity = 1;          // should always have atleast
    private int mFadeDuration;
    private int mDelayDuration;
    private int mDisplayDuration;
    private int mScrollDuration;
    private boolean mAnimiationStarted = false;
    private boolean mWillScroll = false;

    private ObjectAnimator mFadeInAnimation;
    private ObjectAnimator mFadeOutAnimation;
    private ObjectAnimator mTextScrollAnimation;

    public TextInfoAnimator(TextView infoTextView) {
        this(infoTextView, 2000, 1000, 3000);

    }

    public TextInfoAnimator(TextView infoTextView, int fadeDuration, int delayBetweenItems,
                            int displayDuration) {

        mAnimationHandler = new Handler();
        mRadioInfoTextView = infoTextView;
        mFadeDuration = fadeDuration;
        mDelayDuration = delayBetweenItems;
        mDisplayDuration = displayDuration;
        mScrollDuration = 5000;  // This should be set programatically

        initializeArrayMap();
        initializeAnimations();
    }

    private void initializeArrayMap() {

        mInfoItems.add("87.9 FM");
        mInfoItems.add("");
        mInfoItems.add("");
        mInfoItems.add("");

    }

    private void initializeAnimations() {
        // Don't really need the delay and display animations, can just use a handler and post
        // the next animation via delay
        mFadeInAnimation = ObjectAnimator.ofFloat(mRadioInfoTextView, "alpha", 0f, 1f);
        mFadeInAnimation.setDuration(mFadeDuration);
        mFadeOutAnimation = ObjectAnimator.ofFloat(mRadioInfoTextView, "alpha", 1f, 0f);
        mFadeOutAnimation.setDuration(mFadeDuration);
        mTextScrollAnimation = ObjectAnimator.ofFloat(mRadioInfoTextView, "translationX", 0f,
                0f);
        mTextScrollAnimation.setInterpolator(new LinearInterpolator());
        mTextScrollAnimation.setDuration(mScrollDuration);



        mFadeInAnimation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                super.onAnimationStart(animation);

                DLog.i(TAG, "Fade In");
                DLog.i(TAG, "AnimationText: " + mRadioInfoTextView.getText());

                mRadioInfoTextView.setX(0);
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                if (mAnimiationStarted) {
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
                if (mAnimiationStarted) {
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

                    mRadioInfoTextView.setText(mInfoItems.get(mIndex));
                    checkTextWidth();

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
                if (mAnimiationStarted) {
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

    private void checkTextWidth() {
        Paint textPaint = mRadioInfoTextView.getPaint();
        String text = mRadioInfoTextView.getText().toString();
        int textWidth = Math.round(textPaint.measureText(text));
        ViewGroup.LayoutParams params = mRadioInfoTextView.getLayoutParams();
        params.width = textWidth;
        mRadioInfoTextView.setLayoutParams(params);


        View grandParent = (View) (mRadioInfoTextView.getParent().getParent());
        int mViewWidth = grandParent.getWidth() - grandParent.getPaddingStart() -
                grandParent.getPaddingEnd();

        DLog.i(TAG, "Total Width: " + mViewWidth + "\nText Width: " + textWidth);

        if (mViewWidth >= textWidth) {
            mWillScroll = false;
        } else {
            mWillScroll = true;
            float xTranslate = textWidth - mViewWidth;
            mTextScrollAnimation.setFloatValues(0f, -xTranslate);
            mTextScrollAnimation.setDuration((int)xTranslate*5);   // 5 ms per pixel
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
                break;

            default:
                Log.i(TAG, "Invalid Command Key");
                return;
        }

        if (mInfoItems.get(idx).equals("")) {
            mCapacity++;
        }

        mInfoItems.set(idx, item);

        if (mCapacity > 1 && !mAnimiationStarted) {
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
            mAnimiationStarted = true;
            mRadioInfoTextView.setText(mInfoItems.get(mIndex));
            mFadeInAnimation.start();

        } else {
            mRadioInfoTextView.setText(mInfoItems.get(0));
        }
    }

    public void stopAnimation() {
        mAnimiationStarted = false;
        mFadeOutAnimation.cancel();
        mFadeInAnimation.cancel();
        mTextScrollAnimation.cancel();
        mRadioInfoTextView.setText(mInfoItems.get(0));
    }

    public void clearInfo() {
        stopAnimation();
        mIndex = 0;
        mCapacity = 1;
        mInfoItems.set(1, "");
        mInfoItems.set(2, "");
        mInfoItems.set(3, "");
    }

}
