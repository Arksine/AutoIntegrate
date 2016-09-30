package com.arksine.autointegrate;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.preference.PreferenceManager;
import android.util.Log;

public class MainService extends Service {

    private static String TAG = "MainService";
    private volatile ArduinoCom arduino = null;
    private volatile boolean serviceRunning = false;

    //****Broadcast Receivers****

    // Stop Reciever cleans up and stops the service when the stop button is pressed on
    // the service notification
    public class StopReciever extends BroadcastReceiver {
        public StopReciever() {
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (getString(R.string.ACTION_STOP_SERVICE).equals(action)) {
                serviceRunning = false;
                if (arduino != null) {
                    arduino.disconnect();
                }
                // stops all queued services
                stopSelf();
            }

        }
    }
    private final StopReciever mStopReceiver = new StopReciever();

    // TODO: Usb Device Receiver

    // TODO: Bluetooth Device Receiver

    //****End Broadcast Receivers****

    // Handler that receives messages from the thread
    private final class ServiceHandler extends Handler {

        ServiceHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {

            // TODO: add global preference to that service is started

            arduino = new ArduinoCom((Context)msg.obj);
            serviceRunning = true;

            while (serviceRunning) {
                if (arduino.isConnected()) {
                    arduino.run();
                } else {
                    // sleep for 100ms, then make attempt to connect
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        Log.e(TAG, e.getMessage());
                    }
                    arduino.connect();
                }
            }

            if (arduino != null) {
                arduino.disconnect();
                arduino = null;
            }

            // Stop the service using the startId, so that we don't stop
            // the service in the middle of handling another job
            serviceRunning = false;
            // TODO: add global preference to that service is stopped
            stopSelf(msg.arg1);
        }

    }
    private Looper mServiceLooper;
    private ServiceHandler mServiceHandler;

    @Override
    public void onCreate() {
        // If this is the first time the service has been run

        // Start up the thread running the service.  Note that we create a
        // separate thread because the service normally runs in the process's
        // main thread, which we don't want to block.  We also make it
        // background priority so CPU-intensive work will not disrupt our UI.
        HandlerThread thread = new HandlerThread("AutoIntegrateService",
                Process.THREAD_PRIORITY_BACKGROUND);
        thread.start();

        // Get the HandlerThread's Looper and use it for our Handler
        mServiceLooper = thread.getLooper();
        mServiceHandler = new ServiceHandler(mServiceLooper);

        // The code below registers a receiver that allows us
        // to stop the service through an action shown on the notification.
        IntentFilter filter = new IntentFilter(getString(R.string.ACTION_STOP_SERVICE));
        registerReceiver(mStopReceiver, filter);

        Intent stopIntent = new Intent(getString(R.string.ACTION_STOP_SERVICE));

        PendingIntent stopPendingIntent = PendingIntent.getBroadcast(this, R.integer.REQUEST_STOP_SERVICE,
                stopIntent, 0);

        // TODO: may need to generate a different small icon. Also want to add  pause and resume notifications?
        Bitmap largeIcon = BitmapFactory.decodeResource(getResources(), R.drawable.ic_autointegrate_notification_large);
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this,
                R.integer.REQUEST_START_AUTOINTEGRATE_ACTIVITY, notificationIntent, 0);
        Notification notification;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Icon stopIcon = Icon.createWithResource(this, R.drawable.ic_stop);
            Notification.Action stopAction = new Notification.Action.Builder(stopIcon,
                    "Stop Service", stopPendingIntent).build();
            notification = new Notification.Builder(this)
                    .setContentTitle(getText(R.string.service_notification_title))
                    .setContentText("Service Running")
                    .setSmallIcon(R.drawable.ic_notification_small)
                    .setLargeIcon(largeIcon)
                    .setContentIntent(pendingIntent)
                    .addAction(stopAction)
                    .build();
        } else {
            notification = new Notification.Builder(this)
                    .setContentTitle(getText(R.string.service_notification_title))
                    .setContentText("Service Running")
                    .setSmallIcon(R.drawable.ic_notification_small)
                    .setLargeIcon(largeIcon)
                    .setContentIntent(pendingIntent)
                    .addAction(R.drawable.ic_stop,
                            "Stop Service", stopPendingIntent)
                    .build();
        }

        startForeground(R.integer.ONGOING_NOTIFICATION_ID, notification);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Stop the current thread if its running, so we can launch a new one
        if (arduino != null) {
            arduino.disconnect();
        }

        Message msg = mServiceHandler.obtainMessage();
        msg.arg1 = startId;
        msg.obj = this;                     // The object we are sending is the service context
        mServiceHandler.sendMessage(msg);

        // If we get killed, after returning from here, restart
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        // this service isn't bound to any app
        return null;
    }

    @Override
    public void onDestroy() {


        unregisterReceiver(mStopReceiver);
        if (arduino != null) {
            arduino.disconnect();
        }

    }

}
