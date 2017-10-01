package com.arksine.autocamera;

import android.app.Application;
import android.os.Environment;



import java.io.File;

import timber.log.Timber;

/**
 * Application Class
 */

public class AutoCameraApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        String logDirectory = Environment.getExternalStorageDirectory().getAbsolutePath() +
                File.separatorChar + "AutoIntegrate";
        LogManager.initializeLogs(getApplicationContext(), logDirectory, "autocamera");
    }

}
