package com.arksine.autocamera;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.serenegiant.usb.USBMonitor;
import com.serenegiant.usb.UVCCamera;

import java.lang.reflect.Method;
import java.util.HashMap;

import timber.log.Timber;

public class MainActivity extends AppCompatActivity {

    private static final String ACTION_USB_PERMISSION = "com.arksine.autocamera.USB_PERMISSION";
    public static final String ACTION_USB_ATTACHED = "android.hardware.usb.action.USB_DEVICE_ATTACHED";
    public static final String ACTION_USB_DETACHED = "android.hardware.usb.action.USB_DEVICE_DETACHED";

    private SurfaceView mCameraView = null;
    private SurfaceHolder mSurfaceHolder = null;
    private Surface mPreviewSurface = null;
    private FrameLayout mRootLayout;

    private final Object mCamLock = new Object();
    private volatile UsbDevice mUsbCameraDevice = null;
    private UVCCamera mUVCCamera = null;
    private USBMonitor mUsbMonitor = null;
    private boolean mIsPreviewing = false;
    private boolean mIsActive = false;

    private boolean mImmersive = true;
    private boolean mIsFullScreen = true;

    private Handler mActivityHandler = new Handler();
    private Runnable immersiveMsg = new Runnable() {
        @Override
        public void run() {
            mCameraView.setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN);

        }
    };


    private Runnable setAspectRatio = new Runnable() {
        @Override
        public void run() {
            setViewLayout();
        }
    };

    // local broadcast receiver to listen for intent to shut down
    private BroadcastReceiver mCameraReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(getString(R.string.ACTION_CLOSE_CAMERA))) {

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mActivityHandler.removeCallbacks(immersiveMsg);
                        MainActivity.this.finish();
                    }
                });
            } else if (action.equals(ACTION_USB_PERMISSION)) {
                UsbDevice uDev = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

                if (uDev.equals(mUsbCameraDevice)) {
                    boolean accessGranted = intent
                            .getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);
                    if (accessGranted) {
                        // Right now the activity may not have focus, so onFocusChanged
                        // wont be called and we have to set it manually
                        if (mImmersive) {
                            mCameraView.setSystemUiVisibility(
                                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                            | View.SYSTEM_UI_FLAG_FULLSCREEN);

                            mRootLayout.post(setAspectRatio);
                        }
                        mUsbMonitor.processExternallyMangedDevice(mUsbCameraDevice,
                                USBMonitor.ExternalAction.ADD_DEVICE);
                    } else {
                        Timber.w("Permission denied for device %s", uDev);
                        String text = "Failed to receive permission to access USB Capture Device";
                        Timber.w(text);
                        Toast.makeText(getApplicationContext(), text, Toast.LENGTH_SHORT).show();
                    }
                }
            } else if (action.equals(ACTION_USB_ATTACHED)) {
                UsbDevice uDev = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                mUsbMonitor.processExternallyMangedDevice(uDev,
                        USBMonitor.ExternalAction.PROCESS_ATTACH);
            } else if (action.equals(ACTION_USB_DETACHED)) {
                UsbDevice uDev = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                mUsbMonitor.processExternallyMangedDevice(uDev,
                        USBMonitor.ExternalAction.PROCESS_DETACH);
            }
        }
    };
    private boolean mIsReceiverRegistered = false;

    private final SurfaceHolder.Callback mCameraViewCallback = new SurfaceHolder.Callback() {
        @Override
        public void surfaceCreated(final SurfaceHolder holder) {
            Timber.v("Camera surfaceCreated:");
        }

        @Override
        public void surfaceChanged(final SurfaceHolder holder, final int format, final int width, final int height) {
            if ((width == 0) || (height == 0)) return;
            Timber.v("Camera surfaceChanged:");
            mPreviewSurface = holder.getSurface();
            synchronized (mCamLock) {
                if (mIsActive && !mIsPreviewing) {
                    mUVCCamera.setPreviewDisplay(mPreviewSurface);
                    mUVCCamera.startPreview();
                    mIsPreviewing = true;
                }
            }
        }

        @Override
        public void surfaceDestroyed(final SurfaceHolder holder) {
            Timber.v("Camera surfaceDestroyed:");
            synchronized (mCamLock) {
                if (mUVCCamera != null) {
                    mUVCCamera.stopPreview();
                }
                mIsPreviewing = false;
                mPreviewSurface = null;
            }
        }
    };

    private final USBMonitor.OnDeviceConnectListener mConnectListener =
            new USBMonitor.OnDeviceConnectListener() {
                @Override
                public void onAttach(UsbDevice device) {}

                @Override
                public void onDettach(UsbDevice device) {}

                @Override
                public void onConnect(final UsbDevice device, final USBMonitor.UsbControlBlock ctrlBlock,
                                      final boolean createNew) {
                    synchronized (mCamLock) {
                        if (mUVCCamera != null)
                            mUVCCamera.destroy();
                        mIsActive = mIsPreviewing = false;
                    }
                    Thread connectThread = new Thread(new Runnable() {
                        @Override
                        public void run() {
                            mUVCCamera = new UVCCamera();
                            mUVCCamera.open(ctrlBlock);
                            Timber.d("Supported Size: %s", mUVCCamera.getSupportedSize());

                            // TODO: Get camera variables from shared prefs, but for initial testing
                            //       we'll use base format (default width, height, YUYV frames
                            try {

                                mUVCCamera.setPreviewSize(UVCCamera.DEFAULT_PREVIEW_WIDTH,
                                        UVCCamera.DEFAULT_PREVIEW_HEIGHT, UVCCamera.FRAME_FORMAT_MJPEG);
                                // TODO: set any other base variables here with other funtions, may as
                                // well catch everything in this try block
                            } catch (final IllegalArgumentException e) {
                                mUVCCamera.destroy();
                                mUVCCamera = null;
                                Toast.makeText(MainActivity.this, "Unable to start camera",
                                        Toast.LENGTH_SHORT).show();
                                Timber.e(e);
                            }
                            if ((mUVCCamera != null) && (mPreviewSurface != null)) {
                                Timber.v("Preview Starting");
                                mIsActive = true;
                                mUVCCamera.setPreviewDisplay(mPreviewSurface);
                                mUVCCamera.startPreview();
                                mIsPreviewing = true;
                            }
                        }
                    });
                    connectThread.start();
                }

                @Override
                public void onDisconnect(UsbDevice device, USBMonitor.UsbControlBlock ctrlBlock) {
                    synchronized (mCamLock) {
                        if (mUVCCamera != null) {
                            mUVCCamera.close();
                            if (mPreviewSurface != null) {
                                mPreviewSurface.release();
                                mPreviewSurface = null;
                            }
                            mIsActive = mIsPreviewing = false;

                        }
                    }
                }

                @Override
                public void onCancel(UsbDevice device) {}
            };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Timber.plant(new Timber.DebugTree());

        setContentView(R.layout.activity_main);

        mRootLayout = (FrameLayout) findViewById(R.id.activity_main);
        mCameraView = (SurfaceView) findViewById(R.id.camera_view);
        //mCameraView.setFocusable(true);
        //mCameraView.setBackgroundColor(Color.BLACK);

        mSurfaceHolder = mCameraView.getHolder();
        mSurfaceHolder.addCallback(mCameraViewCallback);

        mCameraView.setOnSystemUiVisibilityChangeListener
                (new View.OnSystemUiVisibilityChangeListener() {
                    @Override
                    public void onSystemUiVisibilityChange(int visibility) {
                        if ((visibility & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0) {
                            // The screen has been touched and the system bars are now visible.

                            if(mImmersive) {
                                // If immersive is set to true, hide system bars after 3 seconds
                                mActivityHandler.postDelayed(immersiveMsg, 3000);
                            }
                        }
                    }
                });

        mUsbMonitor = new USBMonitor(this, mConnectListener);

        SharedPreferences globalPrefs =
                PreferenceManager.getDefaultSharedPreferences(this);

        mImmersive = globalPrefs.getBoolean("camera_pref_key_layout_immersive", true);
        mIsFullScreen = globalPrefs.getBoolean("camera_pref_key_layout_fullscreen", true);

        getSelectedUsbCamera();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (!mIsReceiverRegistered) {
            mIsReceiverRegistered = true;
            IntentFilter filter = new IntentFilter(getString(R.string.ACTION_CLOSE_CAMERA));
            registerReceiver(mCameraReceiver, filter);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (mIsReceiverRegistered) {
            mIsReceiverRegistered = false;
            unregisterReceiver(mCameraReceiver);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        mActivityHandler.removeCallbacks(immersiveMsg);

        synchronized (mCamLock) {
            if (mUVCCamera != null) {
                mUVCCamera.destroy();
                mUVCCamera = null;
            }
            mIsActive = mIsPreviewing = false;
        }
        if (mUsbMonitor != null) {
            mUsbMonitor.destroy();
            mUsbMonitor = null;
        }
        mCameraView = null;
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);

        if (mCameraView == null) {
            return;
        }
        if (mImmersive) {
            if (hasFocus) {
                mCameraView.setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                | View.SYSTEM_UI_FLAG_FULLSCREEN);
            } else {
                // no need to have this in the queue if we lost focus
                mActivityHandler.removeCallbacks(immersiveMsg);
            }
        }

        if (hasFocus) {
            mRootLayout.post(setAspectRatio);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.camera_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        switch (id) {
            case R.id.menu_cam_settings:
                Intent intent = new Intent(this, SettingsActivity.class);
                this.startActivity(intent);

                break;
            case R.id.toggle_fullscreen:
                mIsFullScreen = !mIsFullScreen;
                setViewLayout();
                break;
            default:
                return super.onOptionsItemSelected(item);
        }
        return true;
    }

    /**
     * Finds the currently selected USB device.
     */
    private void getSelectedUsbCamera() {
        String prefSelectDevice = PreferenceManager.getDefaultSharedPreferences(this)
                .getString("camera_pref_key_select_device", "NO_DEVICE");

        if (prefSelectDevice.equals("NO_DEVICE")){
            String text = "No device selected in preferences";
            Timber.w(text);
            Toast.makeText(this, text, Toast.LENGTH_SHORT).show();

        } else {

            UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
            mUsbCameraDevice = findUsbDevice(usbManager, prefSelectDevice);

            if (mUsbCameraDevice == null) {
                String text = "No usb device matching selection found";
                Timber.w(text);
                Toast.makeText(this, text, Toast.LENGTH_SHORT).show();

            } else if (mUVCCamera != null && mUVCCamera.getDevice().equals(mUsbCameraDevice)) {
                Timber.v("Camera Already Connected");
                // TODO: Device is already connected, if any preference values changed set them here
            }  else {
                // If the request usb permission is set in user settings and the device does not have
                // permission then we will request permission
                if (!(usbManager.hasPermission(mUsbCameraDevice))) {
                    if (!grantAutomaticUsbPermission(mUsbCameraDevice, this)) {
                        // Unable to receive automatic permission, request from user
                        PendingIntent mPendingIntent = PendingIntent.getBroadcast(
                                getApplicationContext(), 0, new Intent(ACTION_USB_PERMISSION), 0);
                        usbManager.requestPermission(mUsbCameraDevice, mPendingIntent);
                    }
                } else {
                    mUsbMonitor.processExternallyMangedDevice(mUsbCameraDevice,
                            USBMonitor.ExternalAction.ADD_DEVICE);
                }
            }
        }
    }

    private UsbDevice findUsbDevice(UsbManager usbManager, String deviceEntry) {
        String[] devIds = deviceEntry.split(":");

        // Make sure the entry value is formatted correctly
        boolean correctFormat;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            correctFormat = devIds.length == 3;
        } else {
            correctFormat = devIds.length == 2;
        }
        if (!correctFormat) {
            Timber.i("Invalid USB entry: %s", deviceEntry);
            return null;
        }

        // Find device by vid, pid, and serial number(if available)
        HashMap<String, UsbDevice> usbDeviceList = usbManager.getDeviceList();
        boolean found;

        for (UsbDevice dev : usbDeviceList.values()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP &&
                    dev.getSerialNumber() != null) {
                found = dev.getVendorId() == Integer.parseInt(devIds[0]) &&
                        dev.getProductId() == Integer.parseInt(devIds[1]) &&
                        dev.getSerialNumber().equals(devIds[2]);
            } else {
                found = dev.getVendorId() == Integer.parseInt(devIds[0]) &&
                        dev.getProductId() == Integer.parseInt(devIds[1]);
            }

            if (found) {
                return dev;
            }
        }

        return null;
    }


    private void setViewLayout() {
        if (mCameraView == null) {
            return;
        }

        FrameLayout.LayoutParams params;

        if (mIsFullScreen)  {
            params = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT, Gravity.CENTER);
            mCameraView.setLayoutParams(params);
        } else {
            int currentWidth = mRootLayout.getWidth();
            int currentHeight = mRootLayout.getHeight();

            if (currentWidth >= (4 * currentHeight) / 3) {
                int destWidth = (4 * currentHeight) / 3 + 1;
                params = new FrameLayout.LayoutParams(destWidth,
                        FrameLayout.LayoutParams.MATCH_PARENT, Gravity.CENTER);
            } else {
                int destHeight = (3 * currentWidth) / 4 + 1;
                params = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                        destHeight, Gravity.CENTER);
            }

            mCameraView.setLayoutParams(params);

        }

        Timber.v("Current view size %d x %d: ", mCameraView.getWidth(), mCameraView.getHeight());
    }

    private static boolean grantAutomaticUsbPermission(UsbDevice usbDevice, Context context)
    {
        try
        {
            Object iUsbManager;
            Class<?> ServiceManager = Class.forName("android.os.ServiceManager");
            Class<?> Stub = Class.forName("android.hardware.usb.IUsbManager$Stub");

            PackageManager pkgManager=context.getPackageManager();
            ApplicationInfo appInfo=pkgManager.getApplicationInfo(context.getPackageName(), PackageManager.GET_META_DATA);

            Method getServiceMethod=ServiceManager.getDeclaredMethod("getService",String.class);
            getServiceMethod.setAccessible(true);
            android.os.IBinder binder=(android.os.IBinder)getServiceMethod.invoke(null, Context.USB_SERVICE);

            Method asInterfaceMethod=Stub.getDeclaredMethod("asInterface", android.os.IBinder.class);
            asInterfaceMethod.setAccessible(true);
            iUsbManager=asInterfaceMethod.invoke(null, binder);


            System.out.println("UID : " + appInfo.uid + " " + appInfo.processName + " " + appInfo.permission);
            final Method grantDevicePermissionMethod = iUsbManager.getClass().getDeclaredMethod("grantDevicePermission", UsbDevice.class,int.class);
            grantDevicePermissionMethod.setAccessible(true);
            grantDevicePermissionMethod.invoke(iUsbManager, usbDevice,appInfo.uid);


            Timber.i("Method OK : %s %s", binder.toString(), iUsbManager.toString());
            return true;
        }
        catch(Exception e)
        {
            Timber.i("SignatureOrSystem permission not available, " +
                            " cannot assign automatic usb permission : %s",
                    usbDevice.getDeviceName());
            Timber.w(e);
            return false;
        }
    }
}
