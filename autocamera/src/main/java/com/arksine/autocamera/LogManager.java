package com.arksine.autocamera;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;

import com.orhanobut.logger.AndroidLogAdapter;
import com.orhanobut.logger.CsvFormatStrategy;
import com.orhanobut.logger.DiskLogAdapter;
import com.orhanobut.logger.DiskLogStrategy;
import com.orhanobut.logger.FormatStrategy;
import com.orhanobut.logger.LogStrategy;
import com.orhanobut.logger.Logger;
import com.orhanobut.logger.PrettyFormatStrategy;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import timber.log.Timber;

/**
 * Sets up Logging for device.  Provides an API for permission requests on Android M+,
 * however the calling activity must implement onRequestPermissionsResult and
 * add a disk logger there if permission was granted.
 *
 */

public class LogManager {
    private static final boolean LOG_VERBOSE_RELEASE = true;  // TODO: Change to false to limit release logs
    private static final int MAX_FILE_SIZE = 500 * 1024;

    private static boolean mDiskLoggerAdded = false;
    private static boolean mTimberInitialized = false;
    private static String mFolderName = null;
    private static String mFileName = null;

    private LogManager() {}

    public static boolean initializeLogs(Context appContext) {
        String folder = Environment.getExternalStorageDirectory().getAbsolutePath()
                + File.separatorChar + "logger";

        String file = "logs";

        return initializeLogs(appContext, folder, file);
    }


    public static boolean initializeLogs(Context appContext, String folder, String fileName) {
        mFolderName = folder;
        mFileName = fileName;

        if (hasWriteExternalStoragePermission(appContext)) {
            addDiskLogger();
        }

        if (!mTimberInitialized) {
            FormatStrategy formatStrategy = PrettyFormatStrategy.newBuilder()
                    .methodCount(0)
                    .tag("AUTOCAMERA")
                    .build();

            if (BuildConfig.DEBUG) {
                // Add logcat adapter
                Logger.addLogAdapter(new AndroidLogAdapter(formatStrategy));

                Timber.plant(new Timber.DebugTree() {
                    @Override
                    protected void log(int priority, String tag, String message, Throwable t) {
                        Logger.log(priority, tag, message, t);
                    }
                });

            } else if (LOG_VERBOSE_RELEASE) {
                // Log everything to file
                Timber.plant(new Timber.DebugTree() {
                    @Override
                    protected void log(int priority, String tag, String message, Throwable t) {
                        Logger.log(priority, tag, message, t);
                    }
                });
            } else {
                // Only log Info, Warn, Error, and WTF
                Timber.plant(new Timber.DebugTree() {
                    @Override
                    protected void log(int priority, String tag, String message, Throwable t) {
                        if (priority == Log.VERBOSE || priority == Log.DEBUG) {
                            // Don't log verbose and debug messages
                            return;
                        }

                        Logger.log(priority, tag, message, t);
                    }
                });
            }
            mTimberInitialized = true;
        }
        return isInitialized();
    }

    public static boolean isInitialized() {
        return mDiskLoggerAdded && mTimberInitialized;
    }

    public static void requestWritePermission(Activity myActivity, final int requestId) {
        // Check for Permission to write to external storage for disklogger
        if (ContextCompat.checkSelfPermission(myActivity, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(myActivity,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                // TODO: Show dialog explaining why I need to write to external storage
            } else {
                ActivityCompat.requestPermissions(myActivity,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        requestId);
            }
        } else {
            Timber.v("WRITE_EXTERNAL_STORAGE permission available");
        }
    }

    public static boolean hasWriteExternalStoragePermission(Context context) {
        return context.getPackageManager().checkPermission(
                Manifest.permission.WRITE_EXTERNAL_STORAGE,context.getPackageName())
                == PackageManager.PERMISSION_GRANTED;
    }



    // TODO: add request permission function


    public static void addDiskLogger() {
        if (!mDiskLoggerAdded) {
            HandlerThread diskLogThread = new HandlerThread("AndroidFileLogger." + mFolderName);
            diskLogThread.start();
            Handler diskLogHandler = new Handler(diskLogThread.getLooper(), mDiskWriteHandlerCallback);
            LogStrategy diskLogStrategy = new DiskLogStrategy(diskLogHandler);
            FormatStrategy diskFormatStrategy = CsvFormatStrategy.newBuilder()
                    .logStrategy(diskLogStrategy)
                    .tag("AUTOCAMERA")
                    .build();
            Logger.addLogAdapter(new DiskLogAdapter(diskFormatStrategy));
            mDiskLoggerAdded = true;
        }
    }

    private static final Handler.Callback mDiskWriteHandlerCallback = new Handler.Callback() {
        @Override
        public boolean handleMessage(Message message) {
            String content = (String) message.obj;

            FileWriter fileWriter = null;
            File logFile = getLogFile(mFolderName, mFileName);

            try {
                fileWriter = new FileWriter(logFile, true);

                fileWriter.append(content);

                fileWriter.flush();
                fileWriter.close();
            } catch (IOException e) {
                if (fileWriter != null) {
                    try {
                        fileWriter.flush();
                        fileWriter.close();
                    } catch (IOException e1) { /* fail silently */ }
                }
            }
            return true;
        }
    };

    /**
     * This code came straight from the Logger library.  The only difference is that the
     * handler that calls it
     * @param folderName
     * @param fileName
     * @return
     */
    private static File getLogFile(String folderName, String fileName) {

        File folder = new File(folderName);
        if (!folder.exists()) {
            //TODO: What if folder is not created, what happens then?
            folder.mkdirs();
        }

        int newFileCount = 0;
        File newFile;
        File existingFile = null;

        newFile = new File(folder, String.format("%s_%s.csv", fileName, newFileCount));
        while (newFile.exists()) {
            existingFile = newFile;
            newFileCount++;
            newFile = new File(folder, String.format("%s_%s.csv", fileName, newFileCount));
        }

        if (existingFile != null) {
            if (existingFile.length() >= MAX_FILE_SIZE) {
                return newFile;
            }
            return existingFile;
        }

        return newFile;
    }
}

