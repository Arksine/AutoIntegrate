package com.arksine.autointegrate;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.arksine.autointegrate.microcontroller.MicroControllerCom;
import com.arksine.autointegrate.power.IntegratedPowerManager;
import com.arksine.autointegrate.preferences.MainSettings;
import com.arksine.autointegrate.utilities.BackgroundThreadFactory;
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

    private Context mContext;
    private ExecutorService EXECUTOR = Executors.newCachedThreadPool(new BackgroundThreadFactory());

    private Future mMainThreadFuture = null;

    private IntegratedPowerManager mPowerManager = null;
    private volatile MicroControllerCom mMicroController = null;
    private volatile boolean mLearningMode = false;

    private volatile boolean serviceSuspended = false;
    private volatile boolean serviceThreadRunning = false;
    private volatile boolean isWaiting = false;

    private LocalBroadcastManager mLocalBM;

    ServiceThread(Context context) {
        mContext = context;
        mLocalBM = LocalBroadcastManager.getInstance(mContext);

        if (!isReceiverRegistered) {
            // Register the receiver for local broadcasts
            IntentFilter filter = new IntentFilter(mContext.getString(R.string.ACTION_WAKE_DEVICE));
            filter.addAction(mContext.getString(R.string.ACTION_REFRESH_CONTROLLER_CONNECTION));
            filter.addAction(mContext.getString(R.string.ACTION_REFRESH_RADIO_CONNECTION));
            filter.addAction(mContext.getString(R.string.ACTION_SUSPEND_DEVICE));

            mLocalBM.registerReceiver(mServiceThreadReceiver, filter);
            isReceiverRegistered = true;
        }

        mPowerManager = new IntegratedPowerManager(mContext);

    }



    // Broadcast Receiver that responds to events sent to alter the service thread
    private boolean isReceiverRegistered = false;
    private BroadcastReceiver mServiceThreadReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (mContext.getString(R.string.ACTION_WAKE_DEVICE).equals(action)) {
                if (serviceSuspended) {
                    EXECUTOR.execute(wakeUpDevice);
                    Log.i(TAG, "Service resumed.");
                }
            } else if (mContext.getString(R.string.ACTION_SUSPEND_DEVICE).equals(action)) {
                // Stop the main thread, but do not destroy the the ExecutorService so the thread
                // can be restarted
                EXECUTOR.execute(suspendDevice);

            } else if (mContext.getString(R.string.ACTION_REFRESH_CONTROLLER_CONNECTION).equals(action)) {
                Log.i(TAG, "Refresh MicroController Connection");
                mLearningMode = intent.getBooleanExtra("LearningMode", false);
                EXECUTOR.execute(stopMicroControllerConnection);

            } else if (mContext.getString(R.string.ACTION_REFRESH_RADIO_CONNECTION).equals(action)) {
                Log.i(TAG, "Refresh Radio Thread");
                //TODO: stopRadioThread();
            }
        }
    };

    @Override
    public void run() {
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        int connectionAttempts = 1;

        while (serviceThreadRunning) {

            // TODO: Add thread for Radio.  Camera nor power management should need a thread
            //       We do need to check to see if Power Mangement is enabled.  If so, we will
            //       Instantiate it so we can listen for Power Management events.  When device
            //       is put into sleep mode we should kill mMicroController and radio threads, and
            //       pause this thread using wait() until a broadcast is recieved to wake up


            // Check to see if MicroController Integration is enabled
            if (sharedPrefs.getBoolean("status_pref_key_toggle_controller", false)) {

                // If the MicroController connection hasn't been established, do so.
                if (mMicroController == null || !mMicroController.isConnected()) {
                    mMicroController = new MicroControllerCom(mContext, mLearningMode);
                    if (mMicroController.connect()) {
                        Log.i(TAG, "Micro Controller connection established");
                    } else {
                        Log.e(TAG, "Error connecting to Micro Controller: Connection Attempt " + connectionAttempts);
                    }
                }
            } else {
                Log.i(TAG, "Micro Controller Integration Disabled");
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
                        Log.i(TAG, e.getMessage());
                    }
                }
            } else {
                connectionAttempts++;
                // sleep for 1 second between connection attempts
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Log.i(TAG, e.getMessage());
                }
            }

        }

        // Clean up all spawned threads.
        stopMicroControllerConnection.run();


        serviceThreadRunning = false;
        Log.i(TAG, "Service Thread finished executing");

    }

    private boolean allConnected() {
        // TODO: as more connections are added, add them to statement below, but ONLY if they
        //       are enabled in settings
        boolean connected = true;
        SharedPreferences globalPrefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        if (globalPrefs.getBoolean("status_pref_key_toggle_controller", false)) {
            connected = (connected && (mMicroController != null && mMicroController.isConnected()));
        }

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
        PreferenceManager.getDefaultSharedPreferences(mContext).edit()
                .putBoolean("service_suspended", false).apply();
        Intent statusChangedIntent = new Intent(mContext.getString(R.string.ACTION_SERVICE_STATUS_CHANGED));
        statusChangedIntent.setClass(mContext, MainSettings.class);
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
            Log.e(TAG, e.getMessage());
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
        Intent statusChangedIntent = new Intent(mContext.getString(R.string.ACTION_SERVICE_STATUS_CHANGED));
        statusChangedIntent.setClass(mContext, MainSettings.class);
        statusChangedIntent.putExtra("service_status", "Off");
        mLocalBM.sendBroadcast(statusChangedIntent);
    }

    public synchronized void notifyServiceThread() {
        if (isWaiting) {
            isWaiting = false;
            notify();
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
                    Log.i(TAG, e.getMessage());
                }

                // if the thread is still alive, kill it
                if (!mMainThreadFuture.isDone()) {
                    Log.i(TAG, "Main Thread did not properly shut down.");
                    mMainThreadFuture.cancel(true);
                }

                mMainThreadFuture = null;

                Log.i(TAG, "Main Thead Stopped");
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

            Log.i(TAG, "Micro Controller Disconnected");
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
                Log.e(TAG, e.getMessage());
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
            PreferenceManager.getDefaultSharedPreferences(mContext).edit()
                    .putBoolean("service_suspended", true).apply();
            Intent statusChangedIntent = new Intent(mContext.getString(R.string.ACTION_SERVICE_STATUS_CHANGED));
            statusChangedIntent.setClass(mContext, MainSettings.class);
            statusChangedIntent.putExtra("service_status", "Suspended");
            mLocalBM.sendBroadcast(statusChangedIntent);

            mPowerManager.goToSleep();
        }
    };

}
