package com.arksine.autointegrate;

import android.app.Application;
import android.util.Log;

import com.arksine.autointegrate.interfaces.MCUControlInterface;
import com.arksine.autointegrate.interfaces.ServiceControlInterface;

import timber.log.Timber;

/**
 * Application wrapper to provide package global access to static interfaces
 */

public class AutoIntegrate extends Application {
    private static final boolean LOG_ALL = true;  // TODO: Change to false for release

    private static ServiceControlInterface mServiceControlInterface = null;
    private static MCUControlInterface mMcuControlInterface = null;


    @Override
    public void onCreate() {
        super.onCreate();

        if (BuildConfig.DEBUG || LOG_ALL) {
            Timber.plant(new Timber.DebugTree());
        } else {
            Timber.plant(new CrashReportingTree());
        }
    }

    public static void setServiceControlInterface(ServiceControlInterface serviceInterface) {
        mServiceControlInterface = serviceInterface;
    }

    public static void setMcuControlInterface(MCUControlInterface mcuInterface) {
        mMcuControlInterface = mcuInterface;
    }

    public static ServiceControlInterface getServiceControlInterface() {
        return mServiceControlInterface;
    }

    public static MCUControlInterface getmMcuControlInterface() {
        return mMcuControlInterface;
    }

    private static class CrashReportingTree extends Timber.Tree {
        @Override
        protected void log(int priority, String tag, String message, Throwable t) {
            if (priority == Log.VERBOSE || priority == Log.DEBUG) {
                return;
            }

            // TODO: Instead of using Log to output to logcat, use a custom library to output
            // to my own file.  Will make finding problems easier.
            Log.e(tag, message);

            if (t != null) {
                t.printStackTrace();
            }
        }
    }

}
