package com.arksine.autointegrate.activities;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.arksine.autointegrate.R;
import com.arksine.autointegrate.utilities.HardwareReceiver;
import com.serenegiant.usb.USBMonitor;
import com.serenegiant.usb.UVCCamera;

import java.util.HashMap;

// TODO:  Need to implement settings and retreive them.  Need a launcher to test the activity (can put it in settings

public class CameraActivity extends AppCompatActivity {
    private final static String TAG = "CameraActivity";
    private final static boolean DEBUG = false;

    private SurfaceView mCameraView = null;
    private Surface mPreviewSurface;
    private FrameLayout mRootLayout;

    private final Object mCamLock = new Object();
    private UVCCamera mUVCCamera = null;
    private USBMonitor mUsbMonitor = null;
    private boolean mIsPreviewing = false;
    private boolean mIsActive = false;

    private boolean mImmersive = true;
    private boolean mIsFullScreen = true;
    private boolean mShowOptionalToasts = true;

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

                mActivityHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        CameraActivity.this.finish();
                    }
                }, 1000);
            }
        }
    };
    private boolean mIsReceiverRegistered = false;

    private final SurfaceHolder.Callback mCameraViewCallback = new SurfaceHolder.Callback() {
        @Override
        public void surfaceCreated(final SurfaceHolder holder) {
            if (DEBUG) Log.v(TAG, "surfaceCreated:");
        }

        @Override
        public void surfaceChanged(final SurfaceHolder holder, final int format, final int width, final int height) {
            if ((width == 0) || (height == 0)) return;
            if (DEBUG) Log.v(TAG, "surfaceChanged:");
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
            if (DEBUG) Log.v(TAG, "surfaceDestroyed:");
            synchronized (mCamLock) {
                if (mUVCCamera != null) {
                    mUVCCamera.stopPreview();
                }
                mIsPreviewing = false;
            }
            mPreviewSurface = null;
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
                    if (DEBUG) Log.i(TAG, "supportedSize:" + mUVCCamera.getSupportedSize());

                    // TODO: Get camera variables from shared prefs, but for initial testing
                    //       we'll use base format (default width, height, YUYV frames
                    try {

                        mUVCCamera.setPreviewSize(UVCCamera.DEFAULT_PREVIEW_WIDTH,
                                UVCCamera.DEFAULT_PREVIEW_HEIGHT, UVCCamera.FRAME_FORMAT_YUYV);
                        // TODO: set any other base variables here with other funtions, may as
                        // well catch everything in this try block
                    } catch (final IllegalArgumentException e) {
                        mUVCCamera.destroy();
                        mUVCCamera = null;
                        Toast.makeText(CameraActivity.this, "Unable to start camera",
                                Toast.LENGTH_SHORT).show();
                        e.printStackTrace();
                    }
                    if ((mUVCCamera != null) && (mPreviewSurface != null)) {
                        // TODO: in the future we may use the frame callback instead of built-in
                        //       surface display...if we need to deinterlace
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
        public void onCancel() {}
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // TODO: need to add buttons or menu to toolbar to toggle Fullscreen and immersive mode
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);
        mRootLayout = (FrameLayout) findViewById(R.id.activity_camera);

        mCameraView = (SurfaceView) findViewById(R.id.camera_view);
        mCameraView.getHolder().addCallback(mCameraViewCallback);
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
    }

    @Override
    protected void onResume() {
        super.onResume();

        SharedPreferences globalPrefs =
                PreferenceManager.getDefaultSharedPreferences(this);

        mImmersive = globalPrefs.getBoolean("camera_pref_key_layout_immersive", true);
        mIsFullScreen = globalPrefs.getBoolean("camera_pref_key_layout_fullscreen", true);
        mShowOptionalToasts = globalPrefs.getBoolean("camera_pref_key_layout_toasts", true);

        getSelectedUsbCamera();

        if (!mIsReceiverRegistered) {
            mIsReceiverRegistered = true;
            IntentFilter filter = new IntentFilter(getString(R.string.ACTION_CLOSE_CAMERA));
            LocalBroadcastManager.getInstance(this).registerReceiver(mCameraReceiver, filter);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (mIsReceiverRegistered) {
            mIsReceiverRegistered = false;
            LocalBroadcastManager.getInstance(this).unregisterReceiver(mCameraReceiver);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

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

    /**
     * Finds the currently selected USB device.
     */
    private void getSelectedUsbCamera() {
        String prefSelectDevice = PreferenceManager.getDefaultSharedPreferences(this)
                .getString("camera_pref_key_select_device", "NO_DEVICE");

        if (prefSelectDevice.equals("NO_DEVICE")){
            String text = "No device selected in preferences";
            Log.e(TAG, text);
            Toast.makeText(this, text, Toast.LENGTH_SHORT).show();

        } else {
            String[] devIds = prefSelectDevice.split(":");

            UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
            HashMap<String, UsbDevice> usbDeviceList = usbManager.getDeviceList();
            final UsbDevice uDevice = usbDeviceList.get(devIds[2]);

            if (uDevice == null) {
                // TODO: I should probably check to see if the usbfs location has changed by
                //       searching for the VID:PID
                String text = "No usb device matching selection found";
                Log.e(TAG, text);
                Toast.makeText(this, text, Toast.LENGTH_SHORT).show();

            } else if (mUVCCamera != null && mUVCCamera.getDevice().equals(uDevice)) {
                // TODO: Device is already connected, if any preference values changed set them here
            }  else {
                // If the request usb permission is set in user settings and the device does not have
                // permission then we will request permission
                if (!(usbManager.hasPermission(uDevice))) {
                    if (!HardwareReceiver.grantAutomaticUsbPermission(uDevice, this)) {
                        HardwareReceiver.UsbCallback callback = new HardwareReceiver.UsbCallback() {
                            @Override
                            public void onUsbPermissionRequestComplete(boolean requestStatus) {
                                if (requestStatus) {

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
                                        mUsbMonitor.processExternallyMangedDevice(uDevice,
                                                USBMonitor.ExternalAction.ADD_DEVICE);
                                    }
                                } else {
                                    String text = "Failed to receive permission to access USB Capture Device";
                                    Log.d(TAG, text);
                                    Toast.makeText(getApplicationContext(), text, Toast.LENGTH_SHORT).show();
                                }
                            }
                        };
                        HardwareReceiver.requestUsbPermission(uDevice, callback, this);
                    }
                } else {
                    mUsbMonitor.processExternallyMangedDevice(uDevice,
                            USBMonitor.ExternalAction.ADD_DEVICE);
                }
            }
        }
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
    }
}
