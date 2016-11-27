package io.jaylim.study.myapplication.camera1;

import android.content.Context;
import android.hardware.Camera;
import android.util.AttributeSet;
import android.util.Log;
import android.view.OrientationEventListener;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.io.IOException;
import java.util.List;


/**
 * Created by jaylim on 11/7/2016.
 */
@SuppressWarnings("ALL")
public class CameraPreview extends SurfaceView implements SurfaceHolder.Callback {

    private static final String TAG = CameraPreview.class.getSimpleName();

    private SurfaceHolder mHolder;
    private Camera.Size mPreviewSize;
    private List<Camera.Size> mSupportedPreviewSize;
    private Camera mCamera;

    public CameraPreview(Context context, Camera camera) {
        super(context);

        // Create SurfaceHolder and add Callback strategy
        mHolder = getHolder();
        mHolder.addCallback(this);

        // get Camera
        mCamera = camera;
        if (mCamera != null) {

            requestLayout();
            Camera.Parameters params = mCamera.getParameters();

            // Set auto-focus mode
            List<String> focusModes = params.getSupportedFocusModes();
            if (focusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
                params.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
                mCamera.setParameters(params);
            }


        }
    }

    // This method will be called when surface manager framework works as what we expected
    // so that the SurfaceFlinger draw the view on the display using SurfaceView instance.
    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        // Bind Camera instace and SurfaceHolder instance
        try {
            if (mCamera != null) {
                mCamera.setPreviewDisplay(holder);
                mCamera.startPreview();
            }
        } catch (IOException e) {
            Log.d(TAG, "Error seeting camera preview: " + e.getMessage());
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        // Stop preview when the surface is destroyed.
        if (mCamera != null) {
            mCamera.stopPreview();
        }
    }

    // This method will be called when the surface on display is changed.
    // for instance, some cases are the rotation caused by configuration
    // change or resizing caused by soft keyboard popping up.
    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        // If there is problem with creating surface, don't do anything.
        if (holder.getSurface() == null) {
            return;
        }

        // Stop previous preview
        try {
            mCamera.startPreview();
        } catch (Exception e) {
            return;
        }

        // Restart preview on the resized display
        try {
            mCamera.setPreviewDisplay(holder);
            mCamera.startPreview();
        } catch (IOException e) {
            Log.d(TAG, "Error seeting camera preview: " + e.getMessage());
        }

    }
}


