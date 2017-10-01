package com.arksine.autointegrate;

import android.app.Application;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Environment;
import android.util.Log;

import com.arksine.autointegrate.interfaces.MCUControlInterface;
import com.arksine.autointegrate.interfaces.ServiceControlInterface;
import com.arksine.autointegrate.utilities.AppItem;
import com.arksine.autointegrate.utilities.LogManager;
import com.arksine.autointegrate.utilities.RootManager;
import com.orhanobut.logger.AndroidLogAdapter;
import com.orhanobut.logger.DiskLogAdapter;
import com.orhanobut.logger.Logger;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import timber.log.Timber;

/**
 * Application wrapper to provide package global access to static interfaces
 */

public class AutoIntegrate extends Application {
    private static final boolean LOG_VERBOSE_RELEASE = true;  // TODO: Change to false to limit release logs

    private static volatile List<AppItem> mAppItems = null;
    private static final Object APP_LIST_LOCK = new Object();

    private static ServiceControlInterface mServiceControlInterface = null;
    private static AtomicReference<MCUControlInterface> mMcuControlInterface
            = new AtomicReference<>(null);

    // TODO: add reference for radio interface

    @Override
    public void onCreate() {
        super.onCreate();

        String logDirectory = Environment.getExternalStorageDirectory().getAbsolutePath() +
                File.separatorChar + "AutoIntegrate";
        LogManager.initializeLogs(getApplicationContext(), logDirectory, "autointegrate");

        // TODO: might not should do this here
        RootManager.initSuperUser();
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
        mMcuControlInterface.set(mcuInterface);
    }

    public static ServiceControlInterface getServiceControlInterface() {
        return mServiceControlInterface;
    }

    public static AtomicReference<MCUControlInterface> getMcuInterfaceRef() {
        return mMcuControlInterface;
    }

}
