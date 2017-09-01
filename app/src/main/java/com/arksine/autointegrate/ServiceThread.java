package com.arksine.autointegrate;

import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.arksine.autointegrate.interfaces.McuLearnCallbacks;
import com.arksine.autointegrate.interfaces.ServiceControlInterface;
import com.arksine.autointegrate.microcontroller.MicroControllerCom;
import com.arksine.autointegrate.power.IntegratedPowerManager;
import com.arksine.autointegrate.preferences.MainSettings;
import com.arksine.autointegrate.radio.RadioCom;
import com.arksine.autointegrate.utilities.BackgroundThreadFactory;
import com.arksine.autointegrate.utilities.DLog;
import com.arksine.autointegrate.utilities.UtilityFunctions;
import com.arksine.hdradiolib.RadioController;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Main Background Thread for service
 */

public class ServiceThread implements Runnable {
    private static String TAG = "ServiceThread";

    private MainService mService;
    private ExecutorService EXECUTOR = Executors.newCachedThreadPool(new BackgroundThreadFactory());

    private Future mMainThreadFuture = null;

    private IntegratedPowerManager mPowerManager = null;
    private AtomicReference<MicroControllerCom> mMicroController = new AtomicReference<>(null);
    private AtomicReference<McuLearnCallbacks> mMcuLearnCallbacks = new AtomicReference<>(null);
    private AtomicReference<RadioCom> mHdRadio = new AtomicReference<>(null);

    private AtomicBoolean mLearningMode = new AtomicBoolean(false);
    private AtomicBoolean mServiceSuspended = new AtomicBoolean(false);
    private AtomicBoolean mServiceThreadRunning = new AtomicBoolean(false);
    private AtomicBoolean mIsWaiting = new AtomicBoolean(false);

    private LocalBroadcastManager mLocalBM;

    private final ServiceControlInterface mServiceInterface = new ServiceControlInterface() {
        @Override
        public void wakeUpDevice() {
            if (mServiceSuspended.get()) {
                EXECUTOR.execute(wakeUpDevice);
                Log.i(TAG, "Service resumed.");
            }
        }

        @Override
        public void suspendDevice() {
            // Stop the main thread, but do not destroy the the ExecutorService so the thread
            // can be restarted
            EXECUTOR.execute(suspendDevice);
        }

        @Override
        public void refreshMcuConnection(boolean learningMode, McuLearnCallbacks cbs) {
            DLog.v(TAG, "Refresh MicroController Connection");
            mLearningMode.set(learningMode);
            mMcuLearnCallbacks.set(cbs);
            EXECUTOR.execute(stopMicroControllerConnection);
        }

        @Override
        public void refreshRadioConnection() {
            DLog.v(TAG, "Refresh Radio Thread");
            EXECUTOR.execute(stopHdRadioConnection);
        }
    };

    ServiceThread(MainService svc) {
        mService = svc;
        AutoIntegrate.setServiceControlInterface(this.mServiceInterface);
        mLocalBM = LocalBroadcastManager.getInstance(mService);


        UtilityFunctions.RootCallback callback = new UtilityFunctions.RootCallback() {
            @Override
            public void OnRootInitialized(boolean rootStatus) {
                mPowerManager = new IntegratedPowerManager(mService, rootStatus);
                notifyServiceThread();
            }
        };
        UtilityFunctions.initRoot(callback);
    }


    @Override
    public void run() {
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(mService);
        int connectionAttempts = 1;

        // Check to see if root is initialized.  If not, we will wait until notified by the RootCallback
        synchronized (this) {
            if (UtilityFunctions.isRootAvailable() == null) {
                try {
                    mIsWaiting.set(true);
                    wait();
                } catch (InterruptedException e) {
                    Log.w(TAG, e.getMessage());
                } finally {
                    if (mIsWaiting.compareAndSet(true, false)) {
                        Log.i(TAG, "Wait interrupted");
                    }
                }
            }
        }

        while (mServiceThreadRunning.get()) {

            // Check to see if MicroController Integration is enabled
            if (sharedPrefs.getBoolean("main_pref_key_toggle_controller", false)) {

                // If the MicroController connection hasn't been established, do so.
                if (mMicroController.get() == null || !mMicroController.get().isConnected()) {
                    mMicroController.set(new MicroControllerCom(mService, mLearningMode.get(),
                            mMcuLearnCallbacks.get()));
                    if (mMicroController.get().connect()) {
                        DLog.v(TAG, "Micro Controller connection established");

                        // update Radio Driver if Radio is Connected
                        if (mHdRadio.get() != null && mHdRadio.get().isConnected()) {
                            mHdRadio.get().updateDriver();
                        }
                    } else {
                        DLog.v(TAG, "Error connecting to Micro Controller: Connection Attempt " + connectionAttempts);
                        AutoIntegrate.setMcuControlInterface(null);
                        mMicroController.set(null);
                    }
                }
            } else {
                Log.i(TAG, "Micro Controller Integration Disabled");
            }


            // Check for HD Radio Integration, we do this second because it is possible that
            // the driver relies on the MCU
            if (sharedPrefs.getBoolean("main_pref_key_toggle_radio", false)) {

                // If the HD Radio Object is either not set or not connected to its driver,
                // attempt connection
                if (mHdRadio.get() == null || !mHdRadio.get().isConnected()) {

                    mHdRadio.set(new RadioCom(mService));
                    if (mHdRadio.get().connect()) {
                        DLog.v(TAG, "HD Radio Connection Set Up");
                    } else {
                        DLog.v(TAG, "Error Setting up HD Radio: Attempt " + connectionAttempts);
                        mHdRadio.set(null);
                    }
                }
            } else {
                Log.i(TAG, "HD Radio Integration Disabled");
            }


            if (allConnected() || (connectionAttempts > 9)) {
                // TODO: should I send a toast indicating to the user max number of connection attempts
                // has been reached?  Should I track connection attempts per connection type?


                // reset connection attempts
                connectionAttempts = 1;

                // Pause execution of this thread until some event requires it to wake up (ie:
                // someone changed a setting or the device has had power applied
                synchronized (this) {
                    try {
                        mIsWaiting.set(true);
                        wait();
                        // sleep for 100ms to make sure settings and vars are updated
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        Log.w(TAG, e.getMessage());
                    } finally {
                        if (mIsWaiting.compareAndSet(true, false)) {
                            Log.i(TAG, "Wait interrupted");
                        }
                    }
                }
            } else {
                connectionAttempts++;
                // sleep for 1 second between connection attempts
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Log.w(TAG, e.getMessage());
                }
            }

        }

        // Clean up all spawned threads.  Stop the HD Radio first in the event that it uses
        // the MCU for comms.
        stopHdRadioConnection.run();
        stopMicroControllerConnection.run();



        mServiceThreadRunning.set(false);
        DLog.v(TAG, "Service Thread finished executing");

    }

    private boolean allConnected() {
        boolean connected = true;
        SharedPreferences globalPrefs = PreferenceManager.getDefaultSharedPreferences(mService);
        if (globalPrefs.getBoolean("main_pref_key_toggle_controller", false)) {
            connected = (mMicroController.get() != null && mMicroController.get().isConnected());
        }

        if (globalPrefs.getBoolean("main_pref_key_toggle_radio", false)) {
            connected = (connected && (mHdRadio.get() != null && mHdRadio.get().isConnected()));
        }

        DLog.i(TAG, "All connected status: " + connected);

        return connected;
    }

    boolean isServiceThreadRunning() {
        return mServiceThreadRunning.get();
    }


    void startServiceThread() {

        // Just in case, make sure the executor is active
        if (EXECUTOR == null || EXECUTOR.isShutdown()) {
            EXECUTOR = Executors.newCachedThreadPool(new BackgroundThreadFactory());
        }

        // Make sure that the mainthread isn't running
        if (mMainThreadFuture != null && !mMainThreadFuture.isDone()) {
            // Stop the main thread.  Because it is a blocking call, do it from another thread
            EXECUTOR.execute(stopMainThread);
        }


        mServiceSuspended.set(false);
        mIsWaiting.set(false);
        mServiceThreadRunning.set(true);
        mMainThreadFuture = EXECUTOR.submit(this);

        // Send intent to status fragment so it knows service status has changed
        PreferenceManager.getDefaultSharedPreferences(mService).edit()
                .putBoolean("service_suspended", false).apply();
        Intent statusChangedIntent = new Intent(mService.getString(R.string.ACTION_SERVICE_STATUS_CHANGED));
        statusChangedIntent.setClass(mService, MainSettings.class);
        statusChangedIntent.putExtra("service_status", "On");
        mLocalBM.sendBroadcast(statusChangedIntent);
    }

    // Only call this when you are ready to shut down the service, as it shuts down the executor.

    // DO NOT call on the UI thread, as it is blocking.
    void destroyServiceThread() {
        mServiceThreadRunning.set(false);
        notifyServiceThread();
        EXECUTOR.shutdown();
        try {
            EXECUTOR.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException e){
            // Thread interrupted, return
            Log.w(TAG, e.getMessage());
            return;
        }

        // Thread is still alive, forcibly terminate it
        if (!EXECUTOR.isTerminated()) {
            EXECUTOR.shutdownNow();
        }

        mMainThreadFuture = null;

        // stop the power manager
        mPowerManager.destroy();

        // Send intent to status fragment so it knows service status has changed
        Intent statusChangedIntent = new Intent(mService.getString(R.string.ACTION_SERVICE_STATUS_CHANGED));
        statusChangedIntent.setClass(mService, MainSettings.class);
        statusChangedIntent.putExtra("service_status", "Off");
        mLocalBM.sendBroadcast(statusChangedIntent);

        mService.stopSelf();
    }

    private synchronized void notifyServiceThread() {
        if (mIsWaiting.compareAndSet(true, false)) {
            notify();
        }
    }

    RadioController getRadioInterface() {
        if (mHdRadio.get() != null && mHdRadio.get().isConnected()) {
            return mHdRadio.get().getRadioInterface();
        } else {
            return null;
        }
    }

    // *** The code below to stop threads are blocking, so they are implemented as runnables
    //     rather than as functions.  This makes it harder to accidentally call one on the UI thread

    private Runnable stopMainThread = new Runnable() {
        @Override
        public void run() {
            if (mMainThreadFuture != null ) {
                mServiceThreadRunning.set(false);

                // Make sure the service thread isn't waiting
                notifyServiceThread();

                // Make sure the thread dies
                try {
                    // Use get with a timeout to see if the thread dies
                    mMainThreadFuture.get(10, TimeUnit.SECONDS);
                } catch (Exception e) {
                    Log.w(TAG, e.getMessage());
                }

                // if the thread is still alive, kill it
                if (!mMainThreadFuture.isDone()) {
                    DLog.i(TAG, "Main Thread did not properly shut down.");
                    mMainThreadFuture.cancel(true);
                }

                mMainThreadFuture = null;
                AutoIntegrate.setServiceControlInterface(null);

                DLog.v(TAG, "Main Thead Stopped");
            }
        }
    };

    private Runnable stopMicroControllerConnection = new Runnable() {
        @Override
        public void run() {
            // Disconnect from Micro Controller if connected
            if (mMicroController.get() != null ) {
                mMicroController.get().disconnect();
                mMicroController.set(null);
                AutoIntegrate.setMcuControlInterface(null);
            }
            // make sure the service thread isn't waiting
            notifyServiceThread();
            DLog.v(TAG, "Micro Controller Disconnected");
        }
    };

    private Runnable stopHdRadioConnection = new Runnable() {
        @Override
        public void run() {
            if (mHdRadio.get() != null) {
                mHdRadio.get().disconnect();
                mHdRadio.set(null);
            }

            notifyServiceThread();

            DLog.v(TAG, "Hd Radio Disconnected");
        }
    };

    private Runnable wakeUpDevice = new Runnable() {
        @Override
        public void run() {
            mPowerManager.wakeUp();

            // since the device is waking up, sleep for 2 seconds before attempting to start
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Log.w(TAG, e.getMessage());
            }
            mServiceSuspended.set(false);
            startServiceThread();
        }
    };

    private Runnable suspendDevice = new Runnable() {
        @Override
        public void run() {
            mServiceSuspended.set(true);
            stopMainThread.run();

            // Broadcast Intent to Status Fragment notifying that service status has changed
            PreferenceManager.getDefaultSharedPreferences(mService).edit()
                    .putBoolean("service_suspended", true).apply();
            Intent statusChangedIntent = new Intent(mService.getString(R.string.ACTION_SERVICE_STATUS_CHANGED));
            statusChangedIntent.setClass(mService, MainSettings.class);
            statusChangedIntent.putExtra("service_status", "Suspended");
            mLocalBM.sendBroadcast(statusChangedIntent);

            mPowerManager.goToSleep();
        }
    };

}
