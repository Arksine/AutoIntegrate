package com.arksine.autointegrate;

import android.app.Application;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.util.Log;

import com.arksine.autointegrate.interfaces.MCUControlInterface;
import com.arksine.autointegrate.interfaces.ServiceControlInterface;
import com.arksine.autointegrate.utilities.AppItem;
import com.arksine.autointegrate.utilities.RootManager;

import java.util.ArrayList;
import java.util.List;

import timber.log.Timber;

/**
 * Application wrapper to provide package global access to static interfaces
 */

public class AutoIntegrate extends Application {
    private static final boolean LOG_ALL = true;  // TODO: Change to false for release

    private static volatile List<AppItem> mAppItems = null;
    private static final Object APP_LIST_LOCK = new Object();

    private static ServiceControlInterface mServiceControlInterface = null;
    private static MCUControlInterface mMcuControlInterface = null;


    @Override
    public void onCreate() {
        super.onCreate();

        // TODO: might not should do this here
        RootManager.initSuperUser();

        if (BuildConfig.DEBUG || LOG_ALL) {
            Timber.plant(new Timber.DebugTree());
        } else {
            Timber.plant(new CrashReportingTree());
        }
    }


    public static void updateAppList(final Context context) {

        Thread appListThread = new Thread(new Runnable() {
            @Override
            public void run() {
                synchronized (APP_LIST_LOCK) {
                    if (mAppItems == null) {
                        PackageManager pm = context.getPackageManager();
                        List<ApplicationInfo> apps = pm.getInstalledApplications(0);
                        mAppItems = new ArrayList<>();

                        for (ApplicationInfo app : apps) {
                            //Create App Items for each installed app
                            if ((app.flags & ApplicationInfo.FLAG_SYSTEM) == 0) {
                                AppItem item = new AppItem(pm.getApplicationLabel(app).toString(),
                                        app.packageName, pm.getApplicationIcon(app));
                                mAppItems.add(item);
                            }
                        }
                    }
                }
            }
        });
        appListThread.start();
    }

    public static void destroyAppList() {
        synchronized (APP_LIST_LOCK) {
            mAppItems = null;
        }
    }

    public static List<AppItem> getAppItems() {
        return mAppItems;
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
