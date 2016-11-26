package io.jaylim.study.myapplication.preview;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v13.app.ActivityCompat;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import io.jaylim.study.myapplication.R;

import static io.jaylim.study.myapplication.utils.BasicUtil.showToast;

/**
 * Created by jaylim on 11/22/2016.
 */

public class PreviewFragment extends Fragment {

  private static final String TAG = PreviewFragment.class.getSimpleName();

  public static PreviewFragment newInstance() {
    return new PreviewFragment();
  }

  private String mCameraId;

  public ResizableTextureView mTextureView;

  @Nullable
  @Override
  public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
                           @Nullable Bundle savedInstanceState) {
    return inflater.inflate(R.layout.fragment_preview, container, false);
  }

  @Override
  public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);
    view.findViewById(R.id.preview_capture_button).setOnTouchListener( (v, e) -> {
      switch(e.getAction()) {
        case MotionEvent.ACTION_DOWN :
          // TODO - start/restart video recording

          return true;
        case MotionEvent.ACTION_UP :
          // TODO - pause/stop video recording

          return true;
        default:
          return false;
      }
    });

    view.findViewById(R.id.preview_resize_button).setOnClickListener(v -> {
      // TODO - Resize preview
    });

    view.findViewById(R.id.preview_selfie_button).setOnClickListener(v -> {
      // Shift selfie mode
    });
    mTextureView = (ResizableTextureView) view.findViewById(R.id.preview_texture_view);
  }

  @Override
  public void onActivityCreated(@Nullable Bundle savedInstanceState) {
    super.onActivityCreated(savedInstanceState);
  }

  @Override
  public void onResume() {
    super.onResume();
    startBackgroundThread();

    // When the screen is turned off and turned back on, the SurfaceTexture is already
    // available, and "onSurfaceTextureAvailable" will not be called.
    if (mTextureView.isAvailable()) {
      openCamera(mTextureView.getWidth(), mTextureView.getHeight());
    } else {
      mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
    }
  }

  @Override
  public void onPause() {
    closeCamera();
    stopBackgroundThread();
    super.onPause();
  }

  /* FUNC - set camera outputs */
  private int mSensorOrientation;

  private Size mPreviewSize;

  private boolean mFlashSupported;

  private static final int ASPECT_RATIO_SQUARE = 0;
  private static final int ASPECT_RATIO_4_3    = 1;
  private static final int ASPECT_RATIO_16_9   = 2;
  private static final int ASPECT_RATIO_FULL   = 3;


  @SuppressWarnings("SuspiciousNameCombination")
  private void setUpCameraOutputs(int width, int height) {
    Activity activity = getActivity();
    CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
    try {
      for (String cameraId : manager.getCameraIdList()) {
        CameraCharacteristics characteristics
            = manager.getCameraCharacteristics(cameraId);

        // TODO - Do not use front facing camera yet
        Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
        if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
          continue;
        }

        // Get stream configuration map
        StreamConfigurationMap map = characteristics.get(
            CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        if (map == null) {
          continue;
        }

        // Handle with orthogonality between natural orientation of display and camera sensor.
        int displayRotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        // noinspection ConstantConditions
        mSensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);

        // Dimension matrices has to be swapped if the natural orientation of display is
        // orthogonal to the natural orientation of camera sensor.
        boolean isOrthogonal = checkOrthogonality(displayRotation, mSensorOrientation);


        // get physical display size
        Point displaySize = new Point();
        activity.getWindowManager().getDefaultDisplay().getSize(displaySize);

        if (width == displaySize.x && height == displaySize.y) {
          Log.e(TAG, "width : " + width + ", height : " + height);
        }

        // Set expected preview with width and height from TextureView
        int expectedCameraSensorHeight = isOrthogonal ? width : height;

        // Set max preview size with physical maximum size of device
        int maxCameraSensorHeight = isOrthogonal ? displaySize.x : displaySize.y;

        // Set displayAspectRatio
        Size[] aspectRatio = new Size[4];
        aspectRatio[ASPECT_RATIO_SQUARE] = new Size(1, 1); // square
        aspectRatio[ASPECT_RATIO_4_3] = new Size(4, 3); // 4 : 3
        aspectRatio[ASPECT_RATIO_16_9] = new Size(16, 9);// 16 : 9
        aspectRatio[ASPECT_RATIO_FULL] = isOrthogonal ? new Size(displaySize.y, displaySize.x)
            : new Size(displaySize.x, displaySize.y);

        // Choose preview size
        mPreviewSize = choosePreviewSize(map.getOutputSizes(SurfaceTexture.class),
            aspectRatio[ASPECT_RATIO_FULL], expectedCameraSensorHeight, maxCameraSensorHeight);

        //
        int orientation = getResources().getConfiguration().orientation;
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
          mTextureView.setAspectRatio(mPreviewSize.getWidth(), mPreviewSize.getHeight());
        } else {
          mTextureView.setAspectRatio(mPreviewSize.getHeight(), mPreviewSize.getWidth());
        }

        Boolean available = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
        mFlashSupported = (available == null) ? false : available;

        mCameraId = cameraId;
        return;
      }
    } catch (CameraAccessException e) {
      e.printStackTrace();
    } catch (NullPointerException e) {
      // Currently an NPE is thrown when the Camera2API is used but not supported on the
      // device this code runs.
      ErrorDialog.newInstance(getString(R.string.camera_2_camera_error))
          .show(getChildFragmentManager(), FRAGMENT_DIALOG);
    }
  }

  /* FUNC - Check orthogonality */
  private static boolean checkOrthogonality(int displayRotation, int cameraSensorOrientation) {
    switch (displayRotation) {
      case Surface.ROTATION_0:
      case Surface.ROTATION_180:
        return cameraSensorOrientation == 90 || cameraSensorOrientation == 270;
      case Surface.ROTATION_90:
      case Surface.ROTATION_270:
        return cameraSensorOrientation == 0 || cameraSensorOrientation == 180;
      default:
        Log.e(TAG, "Invalid display rotation : " + displayRotation);
        return false;
    }
  }


  /* FUNC - Choose optimal preview size */
  private static Size choosePreviewSize(Size[] choices, Size aspectRatio,
                                        int expectedHeight, int maxHeight) {

    // Collect the supported resolutions that are at least as big as the preview Surface
    List<Size> bigEnough = new ArrayList<>();
    // Collect the supported resolutions that are smaller than the preview Surface
    List<Size> notBigEnough = new ArrayList<>();

    int w = aspectRatio.getWidth();
    int h = aspectRatio.getHeight();

    for (Size option : choices) {
      Log.e(TAG, option.getWidth() + " : " + option.getHeight());
      // Confirm feasibility : Maximum size && Aspect ratio
      if (option.getHeight() <= maxHeight && option.getHeight() == option.getWidth() * h / w) {
        // Big enough
        if (option.getHeight() >= expectedHeight) {
          bigEnough.add(option);
        } else {
          notBigEnough.add(option);
        }
      }
    }

    if (bigEnough.size() > 0) {
      return Collections.min(bigEnough, new CompareSizesByArea());
    } else if (notBigEnough.size() > 0) {
      return Collections.max(notBigEnough, new CompareSizesByArea());
    } else {
      Log.e(TAG, "Couldn't find any suitable preview size.");
      return choices[0];
    }
  }

  static class CompareSizesByArea implements Comparator<Size> {
    @Override
    public int compare(Size lhs, Size rhs) {
      // We cast here to ensure the multiplications won't overflow
      return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
          (long) rhs.getWidth() * rhs.getHeight());
    }

  }

  /* FUNC - Configuration change logic (No usage till now) */
  private void configureTransform(int viewWidth, int viewHeight) {
    Activity activity = getActivity();
    if (null == mTextureView || null == mPreviewSize || null == activity) {
      return;
    }

    // Get rotation of display
    int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();

    // Create matrix object which is algebraic transform
    Matrix transform = new Matrix();

    /* Define transform with functions on same descartes coordinate */
    // Size of textureView on display
    RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
    // Size of preview
    RectF bufferRect = new RectF(0, 0, mPreviewSize.getHeight(), mPreviewSize.getWidth());

    // Get center of textureView
    float centerX = viewRect.centerX();
    float centerY = viewRect.centerY();

    // case : landscape vs. portrait
    if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {

      // display is landscape mode
      // Align preview image into the center of the textureView
      bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());

      // Change the aspect ratio of the textureView to align it into the preview image.
      transform.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);

      // Select scale to fit the result view into the display size maintaining
      // it's aspect ratio which was given from preview size.
      float scale = Math.max(
          (float) viewHeight / mPreviewSize.getHeight(),
          (float) viewWidth / mPreviewSize.getWidth()
      );

      transform.postScale(scale, scale, centerX, centerY);
      transform.postRotate(90 * (rotation - 2), centerX, centerY);
    } else if (Surface.ROTATION_180 == rotation) {
      transform.postRotate(180, centerX, centerY);
    }
    mTextureView.setTransform(transform);

  }


  /* FUNC - Preview Session */
  private CaptureRequest.Builder mPreviewRequestBuilder;
  private CaptureRequest mPreviewRequest;
  private CameraCaptureSession mCaptureSession;

  private void createCameraPreviewSession() {
    try {
      SurfaceTexture texture = mTextureView.getSurfaceTexture();
      assert texture != null;

      // We configure the size of default buffer to be the size of camera preview we want.
      texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());

      // This is the output Surface we need to start preview.
      Surface surface = new Surface(texture);

      mPreviewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
      mPreviewRequestBuilder.addTarget(surface);

      mCameraDevice.createCaptureSession(Collections.singletonList(surface),
          mSessionStateCallback, null);

    } catch (CameraAccessException e) {
      e.printStackTrace();
    }
  }

  private void setAutoFlash(CaptureRequest.Builder requestBuilder) {
    if (mFlashSupported) {
      requestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
          CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
    }
  }

  private CameraCaptureSession.StateCallback mSessionStateCallback =
      new CameraCaptureSession.StateCallback() {
        @Override
        public void onConfigured(CameraCaptureSession session) {
          if (null == mCameraDevice) {
            return;
          }

          mCaptureSession = session;
          try {
            // Auto focus should be continuous for camera preview.
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            // Flash is automatically enabled when necessary.
            setAutoFlash(mPreviewRequestBuilder);

            // Finally, we start displaying the camera preview.
            mPreviewRequest = mPreviewRequestBuilder.build();
            mCaptureSession.setRepeatingRequest(mPreviewRequest,
                null, mBackgroundHandler);
          } catch (CameraAccessException e) {
            e.printStackTrace();
          }
        }

        @Override
        public void onConfigureFailed(CameraCaptureSession session) {
          showToast(getActivity(), "Failed");
        }
      };

  /* FUNC - Open and close camera device */
  CameraDevice mCameraDevice;

  private void openCamera(int width, int height) {
    if (ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.CAMERA)
        != PackageManager.PERMISSION_GRANTED) {
      requestCameraPermission();
      return;
    }

    setUpCameraOutputs(width, height);
    // configureTransform(width, height);
    Activity activity = getActivity();
    CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
    try {
      manager.openCamera(mCameraId, mStateCallback, mBackgroundHandler);
    } catch (CameraAccessException e) {
      e.printStackTrace();
    }
  }

  private void closeCamera() {
    if (null != mCameraDevice) {
      mCameraDevice.close();
      mCameraDevice = null;
    }
  }

  /* FUNC - Camera stateCallback */
  private final CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {
    @Override
    public void onOpened(@NonNull CameraDevice camera) {
      mCameraDevice = camera;
      createCameraPreviewSession();
    }

    @Override
    public void onDisconnected(@NonNull CameraDevice camera) {
      camera.close();
      mCameraDevice = null;
    }

    @Override
    public void onError(@NonNull CameraDevice camera, int error) {
      camera.close();
      mCameraDevice = null;
      Activity activity = getActivity();
      if (null != activity) {
        activity.finish();
      }
    }
  };

  /* FUNC - Background Thread -> Looper -> Handler */
  HandlerThread mBackgroundThread;

  Handler mBackgroundHandler;

  private void startBackgroundThread() {
    mBackgroundThread = new HandlerThread("CameraBackground");
    mBackgroundThread.start();
    mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
  }

  private void stopBackgroundThread() {
    mBackgroundThread.quitSafely();
    try {
      mBackgroundThread.join();
      mBackgroundThread = null;
      mBackgroundHandler = null;
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }

  /* FUNC - TextureView */
  private final TextureView.SurfaceTextureListener mSurfaceTextureListener
      = new TextureView.SurfaceTextureListener() {

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
      openCamera(width, height);
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {
      // configureTransform(width, height);
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
      return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture texture) {
      // Invoked when the specified SurfaceTexture is updated
      // through SurfaceTexture.updateTexImage()
    }

  };

  /* FUNC - Permission logic */
  private final String FRAGMENT_DIALOG = "dialog";

  private static final int REQUEST_CAMERA_PERMISSION = 1;

  public static class ErrorDialog extends DialogFragment {
    private static final String ARG_MESSAGE = "message";

    public static ErrorDialog newInstance(String message) {
      ErrorDialog dialog = new ErrorDialog();
      Bundle args = new Bundle();
      args.putString(ARG_MESSAGE, message);
      dialog.setArguments(args);
      return dialog;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
      final Activity activity = getActivity();
      return new AlertDialog.Builder(activity)
          .setMessage(getArguments().getString(ARG_MESSAGE))
          .setPositiveButton(android.R.string.ok, (dialogInterface, i) -> activity.finish())
          .create();
    }
  }

  public static class ConfirmationDialog extends DialogFragment {

    public static ConfirmationDialog newInstance() {
      return new ConfirmationDialog();
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
      final Fragment parent = getParentFragment();
      return new AlertDialog.Builder(getActivity())
          .setMessage(R.string.camera_2_request_permission)
          .setPositiveButton(android.R.string.ok, (dialog, i) ->
              requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION)
          )
          .setNegativeButton(android.R.string.cancel, (dialog, i) -> {
            Activity activity = parent.getActivity();
            if (activity != null) {
              activity.finish();
            }
          })
          .create();
    }
  }

  private void requestCameraPermission() {
    if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
      ConfirmationDialog.newInstance()
          .show(getChildFragmentManager(), FRAGMENT_DIALOG);
    } else {
      requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
    }
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                         @NonNull int[] grantResults) {
    if (requestCode == REQUEST_CAMERA_PERMISSION) {
      if (grantResults.length != 1 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
        ErrorDialog.newInstance(getString(R.string.camera_2_request_permission))
            .show(getChildFragmentManager(), FRAGMENT_DIALOG);
      }
    } else {
      super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }
  }
}
