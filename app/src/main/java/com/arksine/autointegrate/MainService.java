package com.arksine.autointegrate;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Icon;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.RemoteCallbackList;
import android.provider.Settings;
import android.widget.Toast;

import com.arksine.autointegrate.activities.MainActivity;
import com.arksine.autointegrate.radio.RemoteRadioEvents;
import com.arksine.hdradiolib.RadioController;

import timber.log.Timber;


public class MainService extends Service {

    private Notification mNotification;
    private final IBinder mBinder = new LocalBinder();

    public final RemoteCallbackList<RemoteRadioEvents> mRadioCallbacks
            = new RemoteCallbackList<RemoteRadioEvents>();

    // Stop Reciever cleans up and stops the service when the stop button is pressed on
    // the service notification, or when the device is ready to shutdown
    public class StopReciever extends BroadcastReceiver {
        public StopReciever() {
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(getString(R.string.ACTION_STOP_SERVICE)) ||
                    action.equals(Intent.ACTION_SHUTDOWN)) {
                // Because the call to destroyServiceThread blocks, we need to create
                // a thread to stop the service so we don't block the ui thread
                Thread stopThread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        mServiceThread.destroyServiceThread();
                    }
                });
                stopThread.start();
            }
        }
    }
    private final StopReciever mStopReceiver = new StopReciever();

    private ServiceThread mServiceThread;
    private boolean mHasWritePermission;

    @Override
    public void onCreate() {

        /**
         * Make sure that the service has permission to write system settings
         */
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && !Settings.System.canWrite(this)) {
            mHasWritePermission = false;
            return;
        } else {
            mHasWritePermission = true;
        }

        // Instantiate the service thread
        mServiceThread = new ServiceThread(this);

        // The code below registers a receiver that allows us
        // to stop the service through an action shown on the notification.
        IntentFilter filter = new IntentFilter(getString(R.string.ACTION_STOP_SERVICE));
        filter.addAction(Intent.ACTION_SHUTDOWN);
        registerReceiver(mStopReceiver, filter);

        Intent stopIntent = new Intent(getString(R.string.ACTION_STOP_SERVICE));

        PendingIntent stopPendingIntent = PendingIntent.getBroadcast(this, R.integer.REQUEST_STOP_SERVICE,
                stopIntent, 0);

        // TODO: may need to generate a different small icon. Also want to add  pause and resume notifications?
        // TODO: add Android Auto car extension to api 23+?
        Bitmap largeIcon = getLargeNotificationIcon();
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this,
                R.integer.REQUEST_START_AUTOINTEGRATE_ACTIVITY, notificationIntent, 0);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Icon stopIcon = Icon.createWithResource(this, R.drawable.ic_stop);
            Notification.Action stopAction = new Notification.Action.Builder(stopIcon,
                    "Stop Service", stopPendingIntent).build();
            mNotification = new Notification.Builder(this)
                    .setContentTitle(getText(R.string.service_notification_title))
                    .setContentText("Service Running")
                    .setSmallIcon(R.drawable.ic_notification_small)
                    .setLargeIcon(largeIcon)
                    .setContentIntent(pendingIntent)
                    .addAction(stopAction)
                    .setOngoing(true)
                    .setPriority(Notification.PRIORITY_HIGH)
                    .build();
        } else {
            mNotification = new Notification.Builder(this)
                    .setContentTitle(getText(R.string.service_notification_title))
                    .setContentText("Service Running")
                    .setSmallIcon(R.drawable.ic_notification_small)
                    .setLargeIcon(largeIcon)
                    .setContentIntent(pendingIntent)
                    .addAction(R.drawable.ic_stop,
                            "Stop Service", stopPendingIntent)
                    .setOngoing(true)
                    .setPriority(Notification.PRIORITY_HIGH)
                    .build();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        if (!mHasWritePermission) {
            Toast.makeText(this, "Write Settings Permission not granted, cannot start service",
                    Toast.LENGTH_SHORT).show();
            Timber.e("Permission WRITE_SETTINGS not granted, exiting service");
            return START_NOT_STICKY;
        }

        // Start the service thread if it isnt running
        if (!mServiceThread.isServiceThreadRunning()) {
            mServiceThread.startServiceThread();
        } else {
            Timber.w("Attempt to start service when already running");
        }

        startForeground(R.integer.ONGOING_NOTIFICATION_ID, mNotification);

        // If we get killed, after returning from here, restart
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onDestroy() {
        mRadioCallbacks.kill();
        unregisterReceiver(mStopReceiver);
    }

    private Bitmap getLargeNotificationIcon() {
        Bitmap icon = BitmapFactory.decodeResource(getResources(),
                R.drawable.notification_large);

        float scaleMultiplier = getResources().getDisplayMetrics().density / 3f;

        return Bitmap.createScaledBitmap(icon, (int)(icon.getWidth() * scaleMultiplier),
                (int)(icon.getHeight() * scaleMultiplier), false);
    }

    // This class provides a Binder so multiple activities can bind to the serivce to control
    // the HD Radio functionality.  It is the only functionality that needs such a binder, everything
    // can only be accessed package wide, through static functions in the Application class

    public class LocalBinder extends Binder {
        public RadioController getRadioInterface() {
            return mServiceThread.getRadioInterface();
        }




        public void registerRadioCallback(RemoteRadioEvents cb) {
            if (cb != null) {
                mRadioCallbacks.register(cb);
            }
        }

        public void unregisterRadioCallback(RemoteRadioEvents cb) {
            if (cb != null) {
                mRadioCallbacks.unregister(cb);
            }
        }
  }
}
