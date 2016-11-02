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
import android.view.SurfaceHolder;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.arksine.autointegrate.R;
import com.arksine.autointegrate.camera.CameraView;
import com.arksine.autointegrate.utilities.HardwareReceiver;

import java.util.HashMap;

// TODO:  We are going to use the libUVCCamera android library to handle camera ops,
//        however we need to alter it so it can accept a UsbDevice that we already
//        granted permission.  We can possibly just create our own class in this
//        app to manage it and call the necessary functions

public class CameraActivity extends AppCompatActivity {
    private final static String TAG = "CameraActivity";

    CameraView camView = null;
    SurfaceHolder mCamHolder;
    private FrameLayout mRootLayout;

    private boolean mImmersive = true;
    private boolean mIsFullScreen = true;
    private boolean mShowOptionalToasts = true;

    private Handler mActivityHandler = new Handler();
    private Runnable immersiveMsg = new Runnable() {
        @Override
        public void run() {
            camView.setSystemUiVisibility(
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // TODO: need to add buttons or menu to toolbar to toggle Fullscreen and immersive mode
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);
        mRootLayout = (FrameLayout) findViewById(R.id.activity_camera);





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

    /**
     * Finds the currently selected USB device.  We have to do it here so we don't initialize the
     * surfaceview with
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

            }
            else {
                // If the request usb permission is set in user settings and the device does not have
                // permission then we will request permission
                if (!(usbManager.hasPermission(uDevice))) {
                    if (!HardwareReceiver.grantAutomaticUsbPermission(uDevice, this)) {
                        HardwareReceiver.UsbCallback callback = new HardwareReceiver.UsbCallback() {
                            @Override
                            public void onUsbPermissionRequestComplete(boolean requestStatus) {
                                if (requestStatus) {
                                    CameraActivity.this.initView(uDevice);

                                    // Right now the activity doesn't have focus, so if we need to manually
                                    // set immersive mode.
                                    if (mImmersive) {
                                        camView.setSystemUiVisibility(
                                                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                                                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                                                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                                        | View.SYSTEM_UI_FLAG_FULLSCREEN);

                                        mRootLayout.post(setAspectRatio);
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
                    initView(uDevice);
                }
            }
        }
    }

    private void initView(UsbDevice selectedDevice) {

        camView = new CameraView(this);
        mCamHolder = camView.getHolder();

        mRootLayout.addView(camView);

        camView.setOnSystemUiVisibilityChangeListener
                (new View.OnSystemUiVisibilityChangeListener() {
                    @Override
                    public void onSystemUiVisibilityChange(int visibility) {
                        if ((visibility & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0) {

                            // The screen has been touched and the system bars are now visible.
                            // If immersive is set to true, hide system bars after 3 seconds
                            if(mImmersive) {
                                mActivityHandler.postDelayed(immersiveMsg, 3000);
                            }
                        }
                    }
                });

    }


    private void setViewLayout()
    {
        if (camView == null) {
            return;
        }

        FrameLayout.LayoutParams params;

        if (mIsFullScreen)  {

            params = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT, Gravity.CENTER);
            camView.setLayoutParams(params);


        }
        else {
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

            camView.setLayoutParams(params);
        }
    }


}
