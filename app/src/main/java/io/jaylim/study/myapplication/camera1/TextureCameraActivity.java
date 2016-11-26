package io.jaylim.study.myapplication.camera1;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

import io.jaylim.study.myapplication.utils.CameraUtil;

/**
 * Created by jaylim on 11/8/2016.
 */

public class TextureCameraActivity extends Activity implements TextureView.SurfaceTextureListener {

    private static final String TAG = TextureCameraActivity.class.getSimpleName();

    private TextureView mTextureView;
    private Camera mCamera;

    public static Intent newIntent(Context packageContext) {
        return new Intent(packageContext, TextureCameraActivity.class);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Set window to have no title bar abd become fullscreen
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

        // Create TextureView instance
        mTextureView = new TextureView(this);

        // Make The TextureView instance to be full size view
        mTextureView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        // Set the listener implementation
        mTextureView.setSurfaceTextureListener(this);

        // Place TextureView instance into the Activity's view hierarchy.
        setContentView(mTextureView);

        //  Take snapshot
        mTextureView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mCamera.takePicture(mShutterCallback, mRawCallback, mJpegCallback);
            }
        });

        Toast.makeText(this, "Touch anywhere on screen to take picture",
                Toast.LENGTH_SHORT).show();
    }

    /* Called when the TextureView instance becomes available. */
    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        // Create camera instance
        mCamera = Camera.open();

        /* Manipulate preview view size
         *
         * Camera.Size previewSize = mCamera.getParameters().getPreviewSize();
         * mTextureView.setLayoutParams(new FrameLayout.LayoutParams(
         *         previewSize.width, previewSize.height, Gravity.CENTER
         * ));
         */

        // Set the camera orientation to match the view orientation
        CameraUtil.setCameraDisplayOrientation(this, 0, mCamera);

        try {
            mCamera.setPreviewTexture(surface);
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Get camera parameter
        Camera.Parameters params = mCamera.getParameters();

        /* Metering feature
         *
         * if (params.getMaxNumMeteringAreas() > 0) {
         *     List<Camera.Area> meteringAreas = new ArrayList<>();
         *
         *     // 60% weight for rectangular area 1
         *     Rect areaRect1 = new Rect(-100, -100, 100, 100);
         *    meteringAreas.add(new Camera.Area(areaRect1, 600));
         *
         *    // 40% weight for rectangular area 2
         *    Rect areaRect2 = new Rect(800, -1000, 1000, -800);
         *    meteringAreas.add(new Camera.Area(areaRect2, 400));
         *
         *    //
         *    params.setMeteringAreas(meteringAreas);
         * }
         */

        // Check available focus modes
        List<String> focusModes = params.getSupportedFocusModes();
        // if auto focus is available
        if (focusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
            // Set as default
            params.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
        }

        // set the flash as auto mode
        if (!params.getFlashMode().equals(Camera.Parameters.FLASH_MODE_AUTO)) {
            params.setFlashMode(Camera.Parameters.FLASH_MODE_AUTO);
        }

        // Maximize jpeg quality
        params.setJpegQuality(100);

        // Set zoom
        if (params.isZoomSupported()) {
            int current = params.getZoom();
            int i = params.getMaxZoom();
            params.setZoom((i > current + 2) ? current + 1 : current);
        }

        // Set camera parameter
        mCamera.setParameters(params);

        mCamera.startPreview();
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        mCamera.stopPreview();
        mCamera.release();
        return true;
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        // Nothing to do
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        // Nothing to do
    }

    Camera.ShutterCallback mShutterCallback = new Camera.ShutterCallback() {
        @Override
        public void onShutter() {
            Log.d(TAG, "onshutter'd");
        }
    };

    Camera.PictureCallback mRawCallback = new Camera.PictureCallback() {
        @Override
        public void onPictureTaken(byte[] data, Camera camera) {
            Log.d(TAG, "onPictureTaken - raw");
        }
    };

    Camera.PictureCallback mJpegCallback = new Camera.PictureCallback() {
        @Override
        public void onPictureTaken(byte[] data, Camera camera) {
            new SaveImageTask().execute(data);
            camera.startPreview();
        }
    };

    private void refreshGallery(File file) {
        Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        mediaScanIntent.setData(Uri.fromFile(file));
        sendBroadcast(mediaScanIntent);
    }

    private class SaveImageTask extends AsyncTask<byte[], Void, Void> {
        @Override
        protected Void doInBackground(byte[]... data) {
            FileOutputStream outStream = null;

            try {
                File sdCard = Environment.getExternalStorageDirectory();
                File dir = new File(sdCard.getAbsolutePath() + "/camtest");
                dir.mkdirs();

                String fileName = String.format("%d.jpg", System.currentTimeMillis());
                File outFile = new File(dir, fileName);

                outStream = new FileOutputStream(outFile);
                outStream.write(data[0]);
                outStream.flush();
                outStream.close();

                Log.d(TAG, "onPictureTaken - wrote bytes: " + data.length + " to " +
                outFile.getAbsolutePath());

                refreshGallery(outFile);
            } catch (IOException e2) {
                e2.printStackTrace();
            }
            return null;
        }
    }
}
