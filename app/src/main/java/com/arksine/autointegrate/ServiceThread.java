package com.arksine.autointegrate;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Process;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

/**
 * Created by Eric on 10/2/2016.
 */

public class ServiceThread implements Runnable {
    private static String TAG = "ServiceThread";

    private Thread mThread;
    private Context mContext;
    private ArduinoCom arduino = null;
    private Thread mArduinoThread = null;
    private volatile boolean serviceSuspended = false;
    private volatile boolean serviceRunning = false;

    ServiceThread(Context context) {
        mContext = context;
    }

    // Stop Reciever cleans up and stops the service when the stop button is pressed on
    // the service notification
    public class WakeThreadReceiver extends BroadcastReceiver {
        public WakeThreadReceiver() {
        }

        // TODO: Instead of "REFRESH_SERVICE_THREAD, we need intent's to refresh individual items/
        //       threads (arduino, radio, power).  This involves shutting down those threads and deleting them.
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (mContext.getString(R.string.ACTION_WAKE_SERVICE_THREAD).equals(action)) {
                serviceSuspended = false;
                notifyServiceThread();
            } else if (mContext.getString(R.string.ACTION_SUSPEND_SERVICE_THREAD).equals(action)) {
                suspendServiceThread();
            } else if (mContext.getString(R.string.ACTION_REFRESH_ARDUINO_THREAD).equals(action)) {
                stopArduinoThread();
                notifyServiceThread();

            } else if (mContext.getString(R.string.ACTION_REFRESH_RADIO_THREAD).equals(action)) {
                //TODO: stopRadioThread();
                notifyServiceThread();
            }
        }
    };

    @Override
    public void run() {
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(mContext);

        // Register the receiver for local broadcasts, dont want other apps screwing with this
        WakeThreadReceiver wakeRecvr = new WakeThreadReceiver();
        IntentFilter filter = new IntentFilter(mContext.getString(R.string.ACTION_WAKE_SERVICE_THREAD));
        filter.addAction(mContext.getString(R.string.ACTION_REFRESH_SERVICE_THREAD));
        filter.addAction(mContext.getString(R.string.ACTION_SUSPEND_SERVICE_THREAD));
        LocalBroadcastManager.getInstance(mContext).registerReceiver(wakeRecvr, filter);

        serviceRunning = true;

        while (serviceRunning) {

            // TODO: Add/Start thread for Radio.  Camera nor power management should need a thread
            //       We do need to check to see if Power Mangement is enabled.  If so, we will
            //       Instantiate it so we can listen for Power Management events.  When device
            //       is put into sleep mode we should kill arduino and radio threads, and
            //       pause this thread using wait() until a broadcast is recieved to wake up

            if (!serviceSuspended) {
                // Check to see if Arduino Integration is enabled
                if (sharedPrefs.getBoolean("status_pref_key_toggle_arduino", false)) {

                    // If the thread hasn't been started and isn't running then start it
                    if (mArduinoThread == null || !mArduinoThread.isAlive()) {
                        arduino = new ArduinoCom(mContext);
                        if (arduino.connect()) {
                            mArduinoThread = new Thread(arduino);
                            mArduinoThread.setPriority(Process.THREAD_PRIORITY_BACKGROUND);
                            mArduinoThread.start();
                            Log.i(TAG, "Arduino Thread Started");
                        } else {
                            Log.e(TAG, "Error connecting to Arduino");
                        }
                    }
                } else {
                    Log.i(TAG, "Arduino Integration is Disabled");
                    stopArduinoThread();
                }
            }

            // Pause execution of this thread until some event requires it to wake up (ie:
            // someone changed a setting or the device has had power applied
            synchronized (this) {
                try {
                    wait();
                    // sleep for 100ms to make sure settings and vars are updated
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Log.i(TAG, e.getMessage());
                }
            }

            // If we are suspending or refreshing the service, we want to cleanup all threads.
            // In the case if refreshing, they will be relaunched
            if (serviceSuspended) {
                // TODO: stop all threads
                stopArduinoThread();
            }
        }

        // Clean up spawned threads
        stopArduinoThread();


        serviceRunning = false;
        LocalBroadcastManager.getInstance(mContext).unregisterReceiver(wakeRecvr);
        Log.i(TAG, "Service Thread finished executing");

    }

    private boolean allConnected() {
        // TODO: as more threads are added, add them to statement below
        return(arduino != null && arduino.isConnected());
    }


    public void startServiceThread() {
        if (mThread != null && mThread.isAlive()) {
            stopServiceThread();
        }

        mThread = new Thread(this);
        mThread.setPriority(Process.THREAD_PRIORITY_BACKGROUND);
        mThread.start();
    }

    public void stopServiceThread() {
        serviceRunning = false;
        notifyServiceThread();
    }

    public void suspendServiceThread() {
        serviceSuspended = true;
        notifyServiceThread();
    }

    public synchronized void notifyServiceThread() {
        notify();
    }


    private void stopArduinoThread() {
        // Disconnect from Arduino if connected
        if (mArduinoThread != null ) {
            arduino.disconnect();

            // Make sure the thread dies
            try {
                mArduinoThread.join(10000);
            } catch (InterruptedException e) {
                Log.i(TAG, e.getMessage());
            }

            // if the thread is still alive, kill it
            if (mArduinoThread.isAlive()) {
                Log.i(TAG, "Arduino Thread did not properly shut down.");
                mArduinoThread.interrupt();
            }

            arduino = null;
            mArduinoThread = null;

            Log.i(TAG, "Arduino Thread Disconnected");
        }
    }
}
