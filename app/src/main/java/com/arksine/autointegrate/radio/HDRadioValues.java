package com.arksine.autointegrate.radio;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.SparseArray;

import com.arksine.autointegrate.utilities.DLog;

import java.util.HashMap;

/**
 * Class containing values returned by the HD Radio, with methods to get and set them.  Calls
 * are synchronized to prevent reading/writing to the same value at the same time.
 */

public class HDRadioValues {
    private static final String TAG = HDRadioValues.class.getSimpleName();

    private final Object WRITE_LOCK = new Object();

    private volatile boolean mSeekAll;

    private SparseArray<String> mHdTitles = new SparseArray<>(5);
    private SparseArray<String> mHdArtists = new SparseArray<>(5);
    private final HashMap<RadioKey.Command, Object> mHdValues = new HashMap<>(26);

    public HDRadioValues (Context context) {

        // Restore persisted values
        SharedPreferences globalPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        RadioController.TuneInfo info = new RadioController.TuneInfo();
        info.frequency = globalPrefs.getInt("radio_pref_key_frequency", 879);
        info.band = (globalPrefs.getString("radio_pref_key_band", "FM").equals("FM")) ?
                RadioKey.Band.FM : RadioKey.Band.AM;
        int subchannel = globalPrefs.getInt("radio_pref_key_subchannel", 0);
        int volume = globalPrefs.getInt("radio_pref_key_volume", 50);
        int bass = globalPrefs.getInt("radio_pref_key_bass", 15);
        int treble = globalPrefs.getInt("radio_pref_key_treble", 15);

        mSeekAll = globalPrefs.getBoolean("radio_pref_key_seekall", true);

        // TODO: Don't really need to store seek
        mHdValues.put(RadioKey.Command.POWER, false);
        mHdValues.put(RadioKey.Command.MUTE, false);
        mHdValues.put(RadioKey.Command.SIGNAL_STRENGTH, 0);
        mHdValues.put(RadioKey.Command.TUNE, info);
        mHdValues.put(RadioKey.Command.SEEK, 0);
        mHdValues.put(RadioKey.Command.HD_ACTIVE, false);
        mHdValues.put(RadioKey.Command.HD_STREAM_LOCK, false);
        mHdValues.put(RadioKey.Command.HD_SIGNAL_STRENGH, 0);
        mHdValues.put(RadioKey.Command.HD_SUBCHANNEL, subchannel);
        mHdValues.put(RadioKey.Command.HD_SUBCHANNEL_COUNT, 0);
        mHdValues.put(RadioKey.Command.HD_TUNER_ENABLED, true);
        mHdValues.put(RadioKey.Command.HD_TITLE, "");
        mHdValues.put(RadioKey.Command.HD_ARTIST, "");
        mHdValues.put(RadioKey.Command.HD_CALLSIGN, "");
        mHdValues.put(RadioKey.Command.HD_STATION_NAME, "");
        mHdValues.put(RadioKey.Command.HD_UNIQUE_ID, "");
        mHdValues.put(RadioKey.Command.HD_API_VERSION, "");
        mHdValues.put(RadioKey.Command.HD_HW_VERSION, "");
        mHdValues.put(RadioKey.Command.RDS_ENABLED, false);
        mHdValues.put(RadioKey.Command.RDS_GENRE, "");
        mHdValues.put(RadioKey.Command.RDS_PROGRAM_SERVICE, "");
        mHdValues.put(RadioKey.Command.RDS_RADIO_TEXT, "");
        mHdValues.put(RadioKey.Command.VOLUME, volume);
        mHdValues.put(RadioKey.Command.BASS, bass);
        mHdValues.put(RadioKey.Command.TREBLE, treble);
        mHdValues.put(RadioKey.Command.COMPRESSION, 0);
    }

    public Object getHdValue(RadioKey.Command key) {
        synchronized (WRITE_LOCK) {
            return mHdValues.get(key);
        }
    }

    public void setHdValue(RadioKey.Command key, Object value) {
        synchronized (WRITE_LOCK) {
            switch (key) {
                case HD_SUBCHANNEL:
                    int index = (int) value;
                    mHdValues.put(RadioKey.Command.HD_TITLE, mHdTitles.get(index));
                    mHdValues.put(RadioKey.Command.HD_ARTIST, mHdArtists.get(index));
                    ((RadioController.TuneInfo) mHdValues.get(RadioKey.Command.TUNE)).subchannel = index;
                    break;
                case TUNE:
                    // reset values when we tune to a new channel
                    mHdValues.put(RadioKey.Command.HD_SUBCHANNEL, 0);
                    mHdValues.put(RadioKey.Command.HD_SUBCHANNEL_COUNT, 0);
                    mHdValues.put(RadioKey.Command.HD_ACTIVE, false);
                    mHdValues.put(RadioKey.Command.HD_STREAM_LOCK, false);
                    mHdValues.put(RadioKey.Command.RDS_ENABLED, false);
                    mHdValues.put(RadioKey.Command.RDS_GENRE, "");
                    mHdValues.put(RadioKey.Command.RDS_PROGRAM_SERVICE, "");
                    mHdValues.put(RadioKey.Command.RDS_RADIO_TEXT, "");
                    mHdValues.put(RadioKey.Command.HD_CALLSIGN, "");
                    mHdValues.put(RadioKey.Command.HD_STATION_NAME, "");
                    mHdValues.put(RadioKey.Command.HD_TITLE, "");
                    mHdValues.put(RadioKey.Command.HD_ARTIST, "");
                    mHdArtists.clear();
                    mHdTitles.clear();
                    break;
                case HD_TITLE:
                    RadioController.HDSongInfo val = (RadioController.HDSongInfo) value;
                    mHdTitles.put(val.subchannel, val.description);
                    value = val.description;
                    break;
                case HD_ARTIST:
                    RadioController.HDSongInfo val2 = (RadioController.HDSongInfo) value;
                    mHdArtists.put(val2.subchannel, val2.description);
                    value = val2.description;
                    break;
                case SEEK:
                    // Don't need to store seek value
                    return;
                default:
            }

            if (DLog.DEBUG) {
                if (value instanceof RadioController.TuneInfo) {
                    DLog.i(TAG, "Stored " + key.toString() + ": "
                            + ((RadioController.TuneInfo) value).frequency + " "
                            + ((RadioController.TuneInfo) value).band.toString());
                } else {
                    DLog.i(TAG, "Stored " + key.toString() + ": " + value);
                }
            }


            mHdValues.put(key, value);
        }
    }

    public boolean getSeekAll() {
        return mSeekAll;
    }

    public void setSeekAll(boolean seekAll) {
        this.mSeekAll = seekAll;
    }

    public void savePersistentPrefs(Context context) {
        // Persist changeable values
        SharedPreferences globalPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        RadioController.TuneInfo tuneInfo = (RadioController.TuneInfo)mHdValues.get(RadioKey.Command.TUNE);
        boolean hdActive = (boolean)mHdValues.get(RadioKey.Command.HD_ACTIVE);

        int subchannel = (hdActive) ? (int)mHdValues.get(RadioKey.Command.HD_SUBCHANNEL) : 0;
        int volume = (int)mHdValues.get(RadioKey.Command.VOLUME);
        int bass = (int)mHdValues.get(RadioKey.Command.BASS);
        int treble = (int)mHdValues.get(RadioKey.Command.TREBLE);

        globalPrefs.edit()
                .putInt("radio_pref_key_frequency", tuneInfo.frequency)
                .putString("radio_pref_key_band", tuneInfo.band.toString())
                .putInt("radio_pref_key_subchannel", subchannel)
                .putInt("radio_pref_key_volume", volume)
                .putInt("radio_pref_key_bass", bass)
                .putInt("radio_pref_key_treble", treble)
                .putBoolean("radio_pref_key_seekall", mSeekAll)
                .apply();
    }
}
