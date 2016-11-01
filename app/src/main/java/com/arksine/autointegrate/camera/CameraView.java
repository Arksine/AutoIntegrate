package com.arksine.autointegrate.camera;

import android.content.Context;
import android.util.AttributeSet;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

/**
 * Created by Eric on 11/1/2016.
 */

public class CameraView extends SurfaceView implements
        SurfaceHolder.Callback, Runnable{

    public CameraView (Context context) {
        super(context);
    }

    public CameraView(Context context, AttributeSet attrs) {
        super(context, attrs);

    }

    @Override
    public void run() {

    }

    @Override
    public void surfaceCreated (SurfaceHolder surfaceHolder) {

    }

    @Override
    public void surfaceDestroyed (SurfaceHolder surfaceHolder) {

    }

    @Override
    public void surfaceChanged (SurfaceHolder surfaceHolder, int format, int winWidth,
                                int winHeight) {

    }
}
