package io.jaylim.study.myapplication.camera2;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Created by jaylim on 11/16/2016.
 */

public class CameraPreviewActivity extends Activity {
    private final static String TAG = CameraPreviewActivity.class.getSimpleName();

    public static Intent newIntent(Context packageContext) {
        return new Intent(packageContext, CameraPreviewActivity.class);
    }

    private Size mPreviewSize;
    private TextureView mTextureView;
    private CameraDevice mCameraDevice;
    private CaptureRequest.Builder mPreviewRequestBuilder;
    private CameraCaptureSession mPreviewSession;
    private CameraManager mCameraManager;
    private StreamConfigurationMap map;

    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // STEP 1 - Create CameraManager instance
        mCameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);

        // Set fullscreen preview window
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
        );

        // Create TextureView instance
        mTextureView = new TextureView(this);
        // Set layout parameters
        mTextureView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        // Register listener
        mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);

        // Register view object into the activity's view hierarchy,
        // set the activity content to an explicit view.
        setContentView(mTextureView);

        // Add listener on click to capture the image
        // STEP 6 - When user touch the screen, take picture.
        mTextureView.setOnClickListener(v -> {
            try {
                takePicture();
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        });
    }


    final private TextureView.SurfaceTextureListener mSurfaceTextureListener =
            new TextureView.SurfaceTextureListener() {
                @Override
                public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                    // STEP 2.1 - When the texture view is available, create CameraDevice instance.
                    openCamera(width, height);
                }

                @Override
                public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
                }

                @Override
                public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                    return true;
                }

                @Override
                public void onSurfaceTextureUpdated(SurfaceTexture surface) {
                }
            };

    private static Size chooseOptimalSize(Size[] choices,
                                          int textureViewWidth, int textureViewHeight,
                                          int maxWidth, int maxHeight, Size aspectRatio) {

        // Collect the supported resolutions that are at least as big as the preview Surface
        List<Size> bigEnough = new ArrayList<>();
        // Collect the supported resolutions that are smaller than the preview Surface
        List<Size> notBigEnough = new ArrayList<>();
        int w = aspectRatio.getWidth();
        int h = aspectRatio.getHeight();
        for (Size option : choices) {
            if (option.getWidth() <= maxWidth && option.getHeight() <= maxHeight &&
                    option.getHeight() == option.getWidth() * h / w) {
                if (option.getWidth() >= textureViewWidth &&
                        option.getHeight() >= textureViewHeight) {
                    bigEnough.add(option);
                } else {
                    notBigEnough.add(option);
                }
            }
        }

        // Pick the smallest of those big enough. If there is no one big enough, pick the
        // largest of those not big enough.
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else if (notBigEnough.size() > 0) {
            return Collections.max(notBigEnough, new CompareSizesByArea());
        } else {
            Log.e(TAG, "Couldn't find any suitable preview size");
            return choices[0];
        }
    }

    /**
     * Compares two {@code Size}s based on their areas.
     */
    static class CompareSizesByArea implements Comparator<Size> {

        @Override
        public int compare(Size lhs, Size rhs) {
            // We cast here to ensure the multiplications won't overflow
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                    (long) rhs.getWidth() * rhs.getHeight());
        }

    }

    private void openCamera(int width, int height) {
        try {
            String cameraId = mCameraManager.getCameraIdList()[0];

            CameraCharacteristics characteristics =
                    mCameraManager.getCameraCharacteristics(cameraId);

            // get StreamConfigurationMap which provides information on
            map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if (map == null) {
                return;
            }
            mPreviewSize = map.getOutputSizes(SurfaceTexture.class)[0];



            // TODO - Show the preview with original ration.

//            Matrix matrix = new Matrix();
//            RectF viewRect = new RectF(0, 0, width, height);
//            RectF bufferRect = new RectF(0, 0, mPreviewSize.getWidth(), mPreviewSize.getHeight());
//
//            // Calculate aspect ratio of preview display
//            float scale = Math.max(
//                    (float) width / mPreviewSize.getWidth(),
//                    (float) height / mPreviewSize.getHeight()
//            );
//
//            // Get cetral position of TextureView object.
//            float centerX = viewRect.centerX();
//            float centerY = viewRect.centerY();
//
//
//
            /**/
            if (ActivityCompat.checkSelfPermission(this,
                    Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                return;
            }

            // STEP 2.2 - Open camera with async callback strategy
            mCameraManager.openCamera(cameraId, mStateCallback, null);
            Log.i(TAG, "[CALL] Call opening camera device");


        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    // STEP 2.3 - The async callback frame.
    final private CameraDevice.StateCallback mStateCallback =
            new CameraDevice.StateCallback() {
                @Override
                public void onOpened(@NonNull CameraDevice camera) {
                    // STEP 2.4 - When camera opened, get CameraDevice instance.
                    mCameraDevice = camera;
                    Log.i(TAG, "[BACK] Camera device opened");
                    // STEP 3 - Start Preview
                    startPreview();
                }

                @Override
                public void onDisconnected(@NonNull CameraDevice camera) {
                    Log.i(TAG, "CameraDevice Callback : onDisconnected");
                }

                @Override
                public void onError(@NonNull CameraDevice camera, int error) {
                    Log.i(TAG, "CameraDevice Callback : onError");
                }
            };

    @Override
    protected void onPause() {
        super.onPause();
        // Close the CameraDevice and release the reference.
        if (null != mCameraDevice) {
            mCameraDevice.close();
            mCameraDevice = null;
        }
        Log.i(TAG, "CameraDevice is closed");
    }

    // STEP 3 - Start Preview
    protected void startPreview() {
        // Check CameraDevice, TextureView, and PreviewSession are ready.
        if (null == mCameraDevice || !mTextureView.isAvailable() || null == mPreviewSize) {
            Log.i(TAG, "At least one of device, view, and previewSize are not available");
            return;
        }

        // get SurfaceTexture
        SurfaceTexture surfaceTexture = mTextureView.getSurfaceTexture();
        if (null == surfaceTexture) {
            return;
        }
        // Create surface from SurfaceTexture instance
        Surface surface = new Surface(surfaceTexture);

        try { // STEP 3.1 - Create and set capture request for previewing the capture
            // Create request for preview referring to preview template.
            mPreviewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);

            // Register surface object to the request as it's target surface.
            mPreviewRequestBuilder.addTarget(surface);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

        try { // STEP 3.2 - Create and configure capture session for previewing the capture.
            // Create capture session, handing over the request which was set up right before.
            mCameraDevice.createCaptureSession(Arrays.asList(surface),
                    // Session callback
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            // Configuration is just finished.
                            // The session can start processing capture requests.
                            mPreviewSession = session;
                            updatePreview();
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                            Toast.makeText(CameraPreviewActivity.this, "onConfigureFailed",
                                    Toast.LENGTH_SHORT).show();
                        }
                    }, null);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

    }

    protected void updatePreview() {
        if (null == mCameraDevice) {
            return;
        }
        // Additional setting for capture request - Set overall mode of 3A
        // (auto-exposure, auto-white-balance, auto-focus) control routines.
        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
        CaptureRequest previewRequest = mPreviewRequestBuilder.build();

        // Create a CameraPreview thread that has a looper.
        HandlerThread thread = new HandlerThread("CameraPreview");
        // Run the thread
        thread.start();
        // Create handler from the looper.
        Handler backgroundHandler = new Handler(thread.getLooper());

        try {
            // Hand over the request to session with repeating thread handler.
            // Set the session to repeat its request.
            mPreviewSession.setRepeatingRequest(previewRequest, null, backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

    }

    protected void takePicture() throws CameraAccessException {
        if (null == mCameraDevice) {
            return;
        }
        try {
            // JPEG image format is always supported as an output format.
            int imageFormat = ImageFormat.JPEG;

            /* TODO - If you want to use another format, check whether the format is supported.
             * for (int supportedFormat : map.getOutputFormats()) {
             *    if (supportedFormat == ImageFormat.YUV_420_888) {
             *        imageFormat = supportedFormat;
             *    }
             * }
             */

            // Get available image size for JPEG format from StreamConfigurationMap.
            Size[] jpegSizes = null;
            if (map != null) {
                jpegSizes = map.getOutputSizes(imageFormat);
            }

            // null if the format is not supported output.
            if (jpegSizes == null) {
                return;
            }

            for (Size size : jpegSizes) {
                Log.i(TAG, size.toString() +
                        ", width : " + size.getWidth() +
                        ", height : " + size.getHeight());
            }

            // Default width and height
            int width = 640;
            int height = 480;

            // Get image with maximum resolution.
            if (0 < jpegSizes.length) {
                width = jpegSizes[0].getWidth();
                height = jpegSizes[0].getHeight();
            }

            // Create ImageReader instance with desired size and format.
            ImageReader reader = ImageReader.newInstance(width, height, ImageFormat.JPEG, 1);

            // Create surfaces from ImageReader and SurfaceTexture
            Surface imageSurface = reader.getSurface();
            Surface textureSurface = new Surface(mTextureView.getSurfaceTexture());

            List<Surface> outputSurfaces = new ArrayList<Surface>(2);
            outputSurfaces.add(imageSurface);
            outputSurfaces.add(textureSurface);

            // TODO - Build capture request
            final CaptureRequest.Builder captureBuilder =
                    mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            // Set target surface
            captureBuilder.addTarget(imageSurface);

            // Set overall mode of 3A (auto-exposure, auto-white-balance, auto-focus) control routines.
            captureBuilder.set(CaptureRequest.CONTROL_MODE,
                    CameraMetadata.CONTROL_MODE_AUTO);

            // Set rotation of image capture
            int rotation = getWindowManager().getDefaultDisplay().getRotation();
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS.get(rotation));


            // Create file
            File sdCard = Environment.getExternalStorageDirectory();
            File dir = new File(sdCard.getAbsolutePath() + "/muvigram_video");
            if (dir.isDirectory() || dir.mkdirs()) {
                String fileName = String.format("%d.jpg", System.currentTimeMillis());
                final File file = new File(dir, fileName);

                // Create ImageAvailableListener
                ImageReader.OnImageAvailableListener readerListener =
                        new ImageReader.OnImageAvailableListener() {
                            @Override
                            public void onImageAvailable(ImageReader reader) {
                                Image image = null;

                                try {
                                    // Get latest image
                                    image = reader.acquireLatestImage();

                                    //
                                    ByteBuffer buffer = image.getPlanes()[0].getBuffer();

                                    byte[] bytes = new byte[buffer.capacity()];
                                    buffer.get(/*dest*/bytes);

                                    save(bytes);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } finally {
                                    if (image != null) {
                                        image.close();
                                        reader.close();
                                    }
                                }
                            }

                            private void save(byte[] bytes) throws IOException {
                                OutputStream output = null;
                                try {
                                    output = new FileOutputStream(file);
                                    output.write(bytes);
                                } finally {
                                    if (null != output) {
                                        output.close();
                                    }
                                }
                                Toast.makeText(CameraPreviewActivity.this, "Saved : " + file,
                                        Toast.LENGTH_SHORT).show();
                            }
                        };

                // Create BackgroundHandler from looper which is contained in CameraPreview thread.
                HandlerThread thread = new HandlerThread("CameraPicture");
                thread.start();
                final Handler backgroundHandler = new Handler(thread.getLooper());

                // ImageReader : set ImageAvailableListener
                reader.setOnImageAvailableListener(readerListener, backgroundHandler);

                // Create capture callback for CameraCaptureSession
                final CameraCaptureSession.CaptureCallback captureCallback =
                        new CameraCaptureSession.CaptureCallback() {
                            @Override
                            public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                                           @NonNull CaptureRequest request,
                                                           @NonNull TotalCaptureResult result) {
                                super.onCaptureCompleted(session, request, result);

                                startPreview();
                            }
                        };

                mCameraDevice.createCaptureSession(outputSurfaces,
                        new CameraCaptureSession.StateCallback() {
                            @Override
                            public void onConfigured(@NonNull CameraCaptureSession session) {
                                try {
                                    session.capture(captureBuilder.build(), captureCallback, backgroundHandler);
                                } catch (CameraAccessException e1) {
                                    e1.printStackTrace();
                                }
                            }

                            @Override
                            public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                                Toast.makeText(CameraPreviewActivity.this, "onConfigureFailed",
                                        Toast.LENGTH_SHORT).show();
                            }
                        }, backgroundHandler);
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

    }

    private void configureTransform(int width, int height) {
        if (null == mTextureView || null == mPreviewSize) {
            return;
        }

        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, width, height);

        // Calculate aspect ratio of preview display
        float aspect = (float) mPreviewSize.getWidth() / mPreviewSize.getHeight();

        // Get cetral position of TextureView object.
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();

        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            matrix.postScale(1/aspect, aspect, centerX, centerY);
            matrix.postRotate(90*(rotation - 2), centerX, centerY);
        } else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180, centerX, centerY);
        }
        mTextureView.setTransform(matrix);

    }
}
