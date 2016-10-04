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
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;


public class MainService extends Service {

    private static String TAG = "MainService";


    // Stop Reciever cleans up and stops the service when the stop button is pressed on
    // the service notification
    public class StopReciever extends BroadcastReceiver {
        public StopReciever() {
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (getString(R.string.ACTION_STOP_SERVICE).equals(action)) {
                mServiceThread.stopServiceThread();
                // stops all queued services

                // TODO: Do we need to stopself?
                //stopSelf();
            }
        }
    }
    private final StopReciever mStopReceiver = new StopReciever();
    private ServiceThread mServiceThread;

    @Override
    public void onCreate() {

        // Instantiate the service thread
        mServiceThread = new ServiceThread(this);

        // The code below registers a receiver that allows us
        // to stop the service through an action shown on the notification.
        IntentFilter filter = new IntentFilter(getString(R.string.ACTION_STOP_SERVICE));
        registerReceiver(mStopReceiver, filter);

        Intent stopIntent = new Intent(getString(R.string.ACTION_STOP_SERVICE));

        PendingIntent stopPendingIntent = PendingIntent.getBroadcast(this, R.integer.REQUEST_STOP_SERVICE,
                stopIntent, 0);

        // TODO: LargeIcon not working
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
        mServiceThread.startServiceThread();

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
    }

}
