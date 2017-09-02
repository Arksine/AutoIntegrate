package com.arksine.autointegrate.power;

import android.content.Context;
import android.media.AudioManager;

import timber.log.Timber;

/**
 * Manages retrieval and abandonment of audio focus.
 */

public class AudioFocusManager {

    private AudioFocusManager(){}

    private static AudioManager.OnAudioFocusChangeListener audioFocusChangeListener
            = new AudioManager.OnAudioFocusChangeListener() {
        @Override
        public void onAudioFocusChange(int i) {
            switch (i) {
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                    Timber.v("Audio Focus Lost, Transient Can Duck");
                    break;
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                    Timber.v("Audio Focus Lost, Transient");
                    break;
                case AudioManager.AUDIOFOCUS_LOSS:
                    Timber.v("Audio Focus Lost");
                    break;

                case AudioManager.AUDIOFOCUS_GAIN:
                    Timber.v("Audio Focus Gained");
                    break;
                default: break;
            }
        }
    };

    public static boolean requestAudioFocus(Context context) {
        AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);

        int result = am.requestAudioFocus(audioFocusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN);


        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            return true;
        } else {
            Timber.w("Error retrieving audio focus");
            return false;
        }
    }

     public static void releaseAudioFocus(Context context) {
        AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);

        int result = am.abandonAudioFocus(audioFocusChangeListener);

        if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            Timber.w("Error abandoning audio focus");
        }

    }
}
