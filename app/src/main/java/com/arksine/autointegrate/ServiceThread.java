package com.arksine.autointegrate;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.arksine.autointegrate.interfaces.RadioControlInterface;
import com.arksine.autointegrate.microcontroller.MicroControllerCom;
import com.arksine.autointegrate.power.IntegratedPowerManager;
import com.arksine.autointegrate.preferences.MainSettings;
import com.arksine.autointegrate.radio.RadioCom;
import com.arksine.autointegrate.utilities.BackgroundThreadFactory;
import com.arksine.autointegrate.utilities.DLog;
import com.arksine.autointegrate.utilities.UtilityFunctions;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Main Background Thread for service
 */

public class ServiceThread implements Runnable {
    private static String TAG = "ServiceThread";

    private MainService mService;
    private ExecutorService EXECUTOR = Executors.newCachedThreadPool(new BackgroundThreadFactory());

    private Future mMainThreadFuture = null;

    private IntegratedPowerManager mPowerManager = null;
    private volatile MicroControllerCom mMicroController = null;
    private volatile RadioCom mHdRadio = null;
    private volatile boolean mLearningMode = false;

    private volatile boolean serviceSuspended = false;
    private volatile boolean serviceThreadRunning = false;
    private volatile boolean isWaiting = false;

    private LocalBroadcastManager mLocalBM;

    ServiceThread(MainService svc) {
        mService = svc;
        mLocalBM = LocalBroadcastManager.getInstance(mService);

        if (!isReceiverRegistered) {
            // Register the receiver for local broadcasts
            IntentFilter filter = new IntentFilter(mService.getString(R.string.ACTION_WAKE_DEVICE));
            filter.addAction(mService.getString(R.string.ACTION_REFRESH_CONTROLLER_CONNECTION));
            filter.addAction(mService.getString(R.string.ACTION_REFRESH_RADIO_CONNECTION));
            filter.addAction(mService.getString(R.string.ACTION_SUSPEND_DEVICE));

            mLocalBM.registerReceiver(mServiceThreadReceiver, filter);
            isReceiverRegistered = true;
        }

        UtilityFunctions.RootCallback callback = new UtilityFunctions.RootCallback() {
            @Override
            public void OnRootInitialized(boolean rootStatus) {
                mPowerManager = new IntegratedPowerManager(mService, rootStatus);
                notifyServiceThread();
            }
        };
        UtilityFunctions.initRoot(callback);
    }

    // Broadcast Receiver that responds to events sent to alter the service thread
    private boolean isReceiverRegistered = false;
    private BroadcastReceiver mServiceThreadReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (mService.getString(R.string.ACTION_WAKE_DEVICE).equals(action)) {
                if (serviceSuspended) {
                    EXECUTOR.execute(wakeUpDevice);
                    Log.i(TAG, "Service resumed.");
                }
            } else if (mService.getString(R.string.ACTION_SUSPEND_DEVICE).equals(action)) {
                // Stop the main thread, but do not destroy the the ExecutorService so the thread
                // can be restarted
                EXECUTOR.execute(suspendDevice);

            } else if (mService.getString(R.string.ACTION_REFRESH_CONTROLLER_CONNECTION).equals(action)) {
                DLog.v(TAG, "Refresh MicroController Connection");
                mLearningMode = intent.getBooleanExtra("LearningMode", false);
                EXECUTOR.execute(stopMicroControllerConnection);

            } else if (mService.getString(R.string.ACTION_REFRESH_RADIO_CONNECTION).equals(action)) {
                DLog.v(TAG, "Refresh Radio Thread");
                EXECUTOR.execute(stopHdRadioConnection);
            }
        }
    };

    @Override
    public void run() {
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(mService);
        int connectionAttempts = 1;

        // Check to see if root is initialized.  If not, we will wait until notified by the RootCallback
        synchronized (this) {
            if (UtilityFunctions.isRootAvailable() == null) {
                try {
                    isWaiting = true;
                    wait();
                } catch (InterruptedException e) {
                    isWaiting = false;
                    Log.w(TAG, e.getMessage());
                }
            }
        }

        while (serviceThreadRunning) {
            // Check to see if MicroController Integration is enabled
            if (sharedPrefs.getBoolean("main_pref_key_toggle_controller", false)) {

                // If the MicroController connection hasn't been established, do so.
                if (mMicroController == null || !mMicroController.isConnected()) {
                    mMicroController = new MicroControllerCom(mService, mLearningMode);
                    if (mMicroController.connect()) {
                        DLog.v(TAG, "Micro Controller connection established");
                    } else {
                        DLog.v(TAG, "Error connecting to Micro Controller: Connection Attempt " + connectionAttempts);
                    }
                }
            } else {
                Log.i(TAG, "Micro Controller Integration Disabled");
            }

            // Check for HD Radio Integration
            if (sharedPrefs.getBoolean("main_pref_key_toggle_radio", false)) {

                // Since the connection status determines if the radio is powered on or not,
                // its possible for it to be disconnected.  We will only create a new mHdRadio object
                // if its null or if the hdRadio wasn't able to successfully setup
                if (mHdRadio == null || !mHdRadio.isRadioReady()) {
                    mHdRadio = new RadioCom(mService);
                    if (mHdRadio.connect()) {
                        DLog.v(TAG, "HD Radio Connection Set Up");
                    } else {
                        DLog.v(TAG, "Error Setting up HD Radio: Attempt " + connectionAttempts);
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
                        isWaiting = true;
                        wait();
                        // sleep for 100ms to make sure settings and vars are updated
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        isWaiting = false;
                        Log.w(TAG, e.getMessage());
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

        // Clean up all spawned threads.
        stopMicroControllerConnection.run();
        stopHdRadioConnection.run();


        serviceThreadRunning = false;
        DLog.v(TAG, "Service Thread finished executing");

    }

    private boolean allConnected() {
        boolean connected = true;
        SharedPreferences globalPrefs = PreferenceManager.getDefaultSharedPreferences(mService);
        if (globalPrefs.getBoolean("main_pref_key_toggle_controller", false)) {
            connected = (mMicroController != null && mMicroController.isConnected());
        }

        if (globalPrefs.getBoolean("main_pref_key_toggle_radio", false)) {
            connected = (connected && (mHdRadio != null && mHdRadio.isRadioReady()));
        }

        DLog.i(TAG, "All connected status: " + connected);

        return connected;
    }

    public boolean isServiceThreadRunning() {
        return serviceThreadRunning;
    }


    public void startServiceThread() {

        // Just in case, make sure the executor is active
        if (EXECUTOR == null || EXECUTOR.isShutdown()) {
            EXECUTOR = Executors.newCachedThreadPool(new BackgroundThreadFactory());
        }

        // Make sure that the mainthread isn't running
        if (mMainThreadFuture != null && !mMainThreadFuture.isDone()) {
            // Stop the main thread.  Because it is a blocking call, do it from another thread
            EXECUTOR.execute(stopMainThread);
        }


        serviceSuspended = false;
        isWaiting = false;
        serviceThreadRunning = true;
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
    public void destroyServiceThread() {
        serviceThreadRunning = false;
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

        // unregister receiver since the service will stop
        if (isReceiverRegistered) {
            mLocalBM.unregisterReceiver(mServiceThreadReceiver);
            isReceiverRegistered = false;
        }

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
        if (isWaiting) {
            isWaiting = false;
            notify();
        }
    }

    public RadioControlInterface getRadioInterface() {
        if (mHdRadio != null && mHdRadio.isRadioReady()) {
            return mHdRadio.getRadioInterface();
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
                serviceThreadRunning = false;

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

                DLog.v(TAG, "Main Thead Stopped");
            }
        }
    };

    private Runnable stopMicroControllerConnection = new Runnable() {
        @Override
        public void run() {
            // Disconnect from Micro Controller if connected
            if (mMicroController != null ) {
                mMicroController.disconnect();
                mMicroController = null;
            }
            // make sure the service thread isn't waiting
            notifyServiceThread();

            DLog.v(TAG, "Micro Controller Disconnected");
        }
    };

    private Runnable stopHdRadioConnection = new Runnable() {
        @Override
        public void run() {
            if (mHdRadio != null) {
                mHdRadio.disconnect();
                mHdRadio = null;
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
            serviceSuspended = false;
            startServiceThread();
        }
    };

    private Runnable suspendDevice = new Runnable() {
        @Override
        public void run() {
            serviceSuspended = true;
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
