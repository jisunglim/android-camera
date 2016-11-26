package io.jaylim.study.myapplication.camera1;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.hardware.Camera;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import io.jaylim.study.myapplication.R;
import io.jaylim.study.myapplication.utils.CameraUtil;

/**
 * Created by jaylim on 11/7/2016.
 */

@SuppressWarnings("ALL")
public class CameraOneActivity extends Activity {
    private static final String TAG = CameraOneActivity.class.getSimpleName();

    private Camera mCamera;
    private CameraPreview mPreview;
    private Context mContext;

    public static Intent newIntent(Context packageContext) {
        return new Intent(packageContext, CameraOneActivity.class);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Get context
        mContext = this;

        // Use the full display as a preview screen
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.layout_camera_one);

        // Check the number of cameras
        int numCamera = Camera.getNumberOfCameras();
        if (numCamera > 0) {
           try {
               mCamera = Camera.open(0);
           } catch (RuntimeException e) {
               Toast.makeText(mContext, "No camera hardware found.", Toast.LENGTH_LONG).show();
           }

           // Set the camera orientation to match the view orientation
           CameraUtil.setCameraDisplayOrientation(this, 0, mCamera);
        }

        // Set the size of the SurfaceLayout as large as possible.
        mPreview = new CameraPreview(this, mCamera);
        LayoutParams params =
                new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        mPreview.setLayoutParams(params);

        // Add SurfaceView instance to the FrameLayout instance as a view.
        FrameLayout preview = (FrameLayout) findViewById(R.id.camera_preview);
        preview.addView(mPreview);

        // Construct camera instance
        mPreview.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mCamera.takePicture(shutterCallback, rawCallback, jpegCallback);
            }
        });

        // Create custom SurfaceView instance and bind it with the camera instance
        Toast.makeText(mContext, "Touch anywhere on screen to take picture", Toast.LENGTH_SHORT)
                .show();
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mCamera != null) {
            mCamera.release();
            mCamera = null;
        }
    }

    private void refreshGallery(File file) {

    }

    Camera.ShutterCallback shutterCallback = new Camera.ShutterCallback() {
        @Override
        public void onShutter() {
            Log.d(TAG, "onShutter'd");
        }
    };

    Camera.PictureCallback rawCallback = new Camera.PictureCallback() {
        @Override
        public void onPictureTaken(byte[] data, Camera camera) {
            Log.d(TAG, "onPictureTaken - raw");
        }
    };

    Camera.PictureCallback jpegCallback = new Camera.PictureCallback() {
        @Override
        public void onPictureTaken(byte[] data, Camera camera) {
            new SaveImageTask().execute(data);

            mCamera.startPreview();
        }
    };

    private class SaveImageTask extends AsyncTask<byte[], Void, Void> {
        @Override
        protected Void doInBackground(byte[]... params) {
            FileOutputStream outputStream = null;

            try {
                File sdCard = Environment.getExternalStorageDirectory();
                File dir = new File(sdCard.getAbsolutePath() + "/camtest");
                dir.mkdirs();

                String fileName = String.format("%d.jpg", System.currentTimeMillis());
                File outFile = new File(dir, fileName);

                outputStream = new FileOutputStream(outFile);
                outputStream.write(params[0]);
                outputStream.flush();
                outputStream.close();

                refreshGallery(outFile);
            } catch (FileNotFoundException e1) {
                e1.printStackTrace();
            } catch (IOException e2) {
                e2.printStackTrace();
            }

            return null;
        }
    }
}
