package com.arksine.autointegrate;

import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.widget.Toast;

import com.arksine.autointegrate.interfaces.McuLearnCallbacks;
import com.arksine.autointegrate.interfaces.ServiceControlInterface;
import com.arksine.autointegrate.microcontroller.MicroControllerCom;
import com.arksine.autointegrate.power.IntegratedPowerManager;
import com.arksine.autointegrate.preferences.MainSettings;
import com.arksine.autointegrate.radio.RadioCom;
import com.arksine.autointegrate.utilities.BackgroundThreadFactory;
import com.arksine.autointegrate.utilities.RootManager;
import com.arksine.autointegrate.utilities.UtilityFunctions;
import com.arksine.hdradiolib.*;
import com.arksine.hdradiolib.BuildConfig;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import timber.log.Timber;

/**
 * Main Background Thread for service
 */

public class ServiceThread implements Runnable {

    private static final int MAXIMUM_CONNECTION_ATTEMPTS = 10;
    private static final int CONNECTION_ATTEMPT_DELAY = 1000;

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
                Timber.v("Service resumed.");
            } else {
                Timber.d("Service not suspended, skipping wake attempt");
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
            Timber.v("Refresh MicroController Connection");
            mLearningMode.set(learningMode);
            mMcuLearnCallbacks.set(cbs);
            EXECUTOR.execute(stopMicroControllerConnection);
        }

        @Override
        public void refreshRadioConnection() {
            Timber.v("Refresh Radio Thread");
            EXECUTOR.execute(stopHdRadioConnection);
        }
    };

    ServiceThread(MainService svc) {
        mService = svc;
        AutoIntegrate.setServiceControlInterface(this.mServiceInterface);
        mLocalBM = LocalBroadcastManager.getInstance(mService);

        if (RootManager.isInitialized()) {
            boolean rootStatus = RootManager.isRootAvailable();
            mPowerManager = new IntegratedPowerManager(mService, rootStatus);
        } else {
            RootManager.RootCallback callback = new RootManager.RootCallback() {
                @Override
                public void OnRootInitialized(boolean rootStatus) {
                    mPowerManager = new IntegratedPowerManager(mService, rootStatus);
                    notifyServiceThread();
                }
            };
            RootManager.checkRootWithCallback(callback);
        }
    }


    @Override
    public void run() {
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(mService);
        int mcuConnectionAttempts = 0;
        int radioConnectionAttempts = 0;
        boolean mcuEnabled;
        boolean radioEnabled;

        // Check to see if root is initialized.  If not, we will wait until notified by the RootCallback
        synchronized (this) {
            if (!RootManager.isInitialized()) {
                Timber.v("Root not initialized, waiting");

                try {
                    mIsWaiting.set(true);
                    wait(10000);
                } catch (InterruptedException err) {
                    Timber.w(err);
                } finally {
                    if (mIsWaiting.compareAndSet(true, false)) {
                        Timber.v("Wait for root access interrupted / timed out");
                    }
                }
            }
        }

        while (mServiceThreadRunning.get()) {

            mcuEnabled = sharedPrefs.getBoolean("main_pref_key_toggle_controller", false);
            radioEnabled = sharedPrefs.getBoolean("main_pref_key_toggle_radio", false);

            Timber.v("MCU Integration Enabled Status: %b", mcuEnabled);
            Timber.v("Radio Integration Enabled Status: %b", radioEnabled);

            // Check to see if MicroController Integration is enabled
            if (mcuEnabled && mcuConnectionAttempts < MAXIMUM_CONNECTION_ATTEMPTS) {

                // If the MicroController connection hasn't been established, do so.
                if (mMicroController.get() == null || !mMicroController.get().isConnected()) {
                    mMicroController.set(new MicroControllerCom(mService, mLearningMode.get(),
                            mMcuLearnCallbacks.get()));
                    if (mMicroController.get().connect()) {
                        Timber.v("Micro Controller connection established");

                        // update Radio Driver if Radio is Connected
                        if (mHdRadio.get() != null && mHdRadio.get().isConnected()) {
                            mHdRadio.get().updateDriver();
                        }
                    } else {
                        Timber.v("Error connecting to Micro Controller: Connection Attempt " + mcuConnectionAttempts);
                        AutoIntegrate.setMcuControlInterface(null);
                        mMicroController.set(null);
                    }
                    mcuConnectionAttempts++;
                }
            }


            // Check for HD Radio Integration, we do this second because it is possible that
            // the driver relies on the MCU
            if (radioEnabled && radioConnectionAttempts < MAXIMUM_CONNECTION_ATTEMPTS) {

                // If the HD Radio Object is either not set or not connected to its driver,
                // attempt connection
                if (mHdRadio.get() == null || !mHdRadio.get().isConnected()) {

                    mHdRadio.set(new RadioCom(mService));
                    if (mHdRadio.get().connect()) {
                        Timber.v("HD Radio Connection Set Up");
                    } else {
                        Timber.v("Error Setting up HD Radio: Attempt " + radioConnectionAttempts);
                        mHdRadio.set(null);
                    }
                    radioConnectionAttempts++;
                }
            }


            if (threadWaitCheck(mcuEnabled, radioEnabled,
                    mcuConnectionAttempts, radioConnectionAttempts)) {

                if (mcuConnectionAttempts >= MAXIMUM_CONNECTION_ATTEMPTS) {
                    // TODO: Toast needs to be done on UI thread
                    //Toast.makeText(mService, "Maximum MCU connection attempts reached",
                     //       Toast.LENGTH_SHORT).show();
                    Timber.i("Maximum MCU connection attempts reached");
                }

                if (radioConnectionAttempts >= MAXIMUM_CONNECTION_ATTEMPTS) {
                    //Toast.makeText(mService, "Maximum Radio connection attempts reached",
                    //        Toast.LENGTH_SHORT).show();
                    Timber.i("Maximum Radio connection attempts reached");
                }

                // The thread will now sleep, so reset connection attempts for when
                // it is refreshed.
                mcuConnectionAttempts = 0;
                radioConnectionAttempts = 0;

                // Pause execution of this thread until some event requires it to wake up (ie:
                // someone changed a setting or the device has had power applied
                synchronized (this) {
                    try {
                        mIsWaiting.set(true);
                        wait();
                    } catch (InterruptedException e) {
                        Timber.w(e);
                    } finally {
                        if (mIsWaiting.compareAndSet(true, false)) {
                            Timber.i("Wait for service thread interrupted");
                        }
                    }
                }
            }

            // sleep for 1 second between connection attempts.  This gives the service
            // time to apply new settings between attempts
            try {
                Thread.sleep(CONNECTION_ATTEMPT_DELAY);
            } catch (InterruptedException e) {
                Timber.w(e);
            }
        }

        // Clean up all spawned threads.  Stop the HD Radio first in the event that it uses
        // the MCU for comms.
        stopHdRadioConnection.run();
        stopMicroControllerConnection.run();



        mServiceThreadRunning.set(false);
        Timber.v("Service Thread finished executing");

    }

    /**
     * Checks to see if the service thread should enter wait mode.  The thread should enter wait mode
     * if all enabled modules have either successfully connected or have reached their maximum
     * connection attempts. If all modules are disabled the thread should wait by default.
     *
     * @param mcuEnabled    Status of the MCU module
     * @param radioEnabled  Status of the Radio module
     * @param mcuAttempts   Number of attempts the MCU has tried to connect during this cycle
     * @param radioAttempts Number of attempts the Radio has tried to connect during this cycle
     *
     * @return true if the thread should enter wait mode, false if it should continue
     */
    private boolean threadWaitCheck(boolean mcuEnabled, boolean radioEnabled,
                                 int mcuAttempts, int radioAttempts) {

        boolean shouldWait = true;
        if (mcuEnabled) {
            shouldWait = ((mMicroController.get() != null && mMicroController.get().isConnected()) ||
                        mcuAttempts >= MAXIMUM_CONNECTION_ATTEMPTS);
        }

        if (radioEnabled) {
            shouldWait = ((shouldWait && (mHdRadio.get() != null && mHdRadio.get().isConnected())) ||
                        radioAttempts >= MAXIMUM_CONNECTION_ATTEMPTS);
        }

        Timber.v("Thead Wait Status: " + shouldWait);

        return shouldWait;
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
            Timber.w(e);
            return;
        }

        // Thread is still alive, forcibly terminate it
        if (!EXECUTOR.isTerminated()) {
            EXECUTOR.shutdownNow();
        }

        mMainThreadFuture = null;

        // stop the power manager
        mPowerManager.destroy();

        // Remove the service control interface
        AutoIntegrate.setServiceControlInterface(null);

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
                    Timber.w(e.getMessage());
                }

                // if the thread is still alive, kill it
                if (!mMainThreadFuture.isDone()) {
                    Timber.w("Main Thread did not properly shut down.");
                    mMainThreadFuture.cancel(true);
                }

                mMainThreadFuture = null;

                Timber.v("Main Thead Stopped");
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
            Timber.v("Micro Controller Disconnected");
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

            Timber.v("Hd Radio Disconnected");
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
                Timber.w(e);
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
