package io.jaylim.study.myapplication.capture;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.Point;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaActionSound;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.IntDef;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v13.app.ActivityCompat;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import butterknife.Unbinder;
import io.jaylim.study.myapplication.R;
import io.jaylim.study.myapplication.utils.BasicUtil;

/**
 *
 * Created by jaylim on 11/22/2016.
 */

public class CaptureFragment extends Fragment {

  private static final String TAG = CaptureFragment.class.getSimpleName();

  public static CaptureFragment newInstance() {
    return new CaptureFragment();
  }

  @Nullable
  @Override
  public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
                           @Nullable Bundle savedInstanceState) {
    return inflater.inflate(R.layout.fragment_capture, container, false);
  }

  Unbinder mUnbinder;

  @BindView(R.id.capture_take_picture_button)
  ImageButton captureButton;
  @BindView(R.id.capture_selfie_mode_button)
  ImageButton selfieButton;
  @BindView(R.id.capture_texture_view)
  ResizableTextureView mTextureView;

  @OnClick(R.id.capture_take_picture_button)
  public void _captureImage() {
    captureButton.setImageResource(R.drawable.camera_shoot_button_hold_70dp);
    setUpImageFile();
    takePicture();
  }

  @OnClick(R.id.capture_selfie_mode_button)
  public void _shiftSelfieMode() {
    closeCamera();
    mSelfieMode = !mSelfieMode;
    openCamera();

  }

  @OnClick(R.id.capture_texture_view)
  public void _triggerAutoFocus() {
    unlockFocus(mPreviewRequestBuilder);
  }

  // STEP - STORAGE DIR ///////////////////////////////////////////////////////////////////////////

  @Override
  public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);
    mUnbinder = ButterKnife.bind(this, view);
  }

  @Override
  public void onActivityCreated(@Nullable Bundle savedInstanceState) {
    super.onActivityCreated(savedInstanceState);
    setUpStorageDir();
  }

  @Override
  public void onDestroyView() {
    mUnbinder.unbind();
    super.onDestroyView();
  }

  @Override
  public void onResume() {
    super.onResume();
    startBackgroundThread();
    if (mTextureView.isAvailable()) {
      openCamera();
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

  // STEP - STORAGE DIR ///////////////////////////////////////////////////////////////////////////

  private File mDir;

  private File mFile;

  /* Get access storage directory */
  private void setUpStorageDir() {
    File storageRoot = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);
    File dir = new File(storageRoot.getAbsolutePath() + "/camera_capture_test");
    if (dir.isDirectory() || dir.mkdirs()) {
      mDir = dir;
    } else {
      // TODO - How to solve this problem?
      Log.e(TAG, "There was problem to find out application-specified directory. " +
          "The picture will be downloaded into the application root directory.");
      mDir = getActivity().getExternalFilesDir(null);
    }
  }

  private void setUpImageFile() {
    String fileName = String.format(Locale.US, "%d.jpg", System.currentTimeMillis());
    mFile = new File(mDir, fileName);
  }

  private void rescanFile(File file) {
    if (Build.VERSION.SDK_INT < 19)
      getActivity().sendBroadcast(new Intent(
          Intent.ACTION_MEDIA_MOUNTED,
          Uri.parse("file://" + file.toString())));
    else {
      MediaScannerConnection
          .scanFile(
              getActivity(),
              new String[]{file.toString()},
              null,
              (path, uri) -> {
                Log.i("ExternalStorage", "Scanned "
                    + path + ":");
                Log.i("ExternalStorage", "-> uri="
                    + uri);
              });
    }
  }

  // STEP /////////////////////////////////////////////////////////////////////////////////////////

  /* FUNC - Semaphore for safe exit. */
  private Semaphore mCameraOpenCloseLock = new Semaphore(1);

  /* FUNC - Open and close camera device */
  private MediaActionSound mShutter;

  private String mCameraId;

  private CameraDevice mCameraDevice;

  private void openCamera() {
    if (ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.CAMERA)
        != PackageManager.PERMISSION_GRANTED) {
      requestCameraPermission();
      return;
    }

    setUpCameraOutputs();

    Activity activity = getActivity();
    CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
    try {
      if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
        throw new RuntimeException("Time out waiting to lock camera opening.");
      }
      manager.openCamera(mCameraId, mStateCallback, mBackgroundHandler);
    } catch (CameraAccessException e) {
      e.printStackTrace();
    } catch (InterruptedException e) {
      throw new RuntimeException("Interrupted while trying to lock camera opening.", e);
    }
  }

  private void closeCamera() {
    try {
      mCameraOpenCloseLock.acquire();
      if (null != mCompoundSession) {
        mCompoundSession.close();
        mCompoundSession = null;
      }
      if (null != mShutter) {
        mShutter.release();
        mShutter = null;
      }
      if (null != mCameraDevice) {
        mCameraDevice.close();
        mCameraDevice = null;
      }
      if (null != mImageReader) {
        mImageReader.close();
        mImageReader = null;
      }
    } catch (InterruptedException e) {
      throw new RuntimeException("Interrupted while trying to lock camera closing.", e);
    } finally {
      mCameraOpenCloseLock.release();
    }
  }

  /* FUNC - Camera device state callback */
  private final CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {
    @Override
    public void onOpened(@NonNull CameraDevice camera) {
      // Camera device
      mCameraDevice = camera;

      // Shutter sound
      mShutter = new MediaActionSound();
      mShutter.load(MediaActionSound.SHUTTER_CLICK);

      // Session
      createCameraCompoundSession();

      mCameraOpenCloseLock.release();
    }

    @Override
    public void onDisconnected(@NonNull CameraDevice camera) {
      mCameraOpenCloseLock.release();
      camera.close();
      mCameraDevice = null;
    }

    @Override
    public void onError(@NonNull CameraDevice camera, int error) {
      mCameraOpenCloseLock.release();
      camera.close();
      mCameraDevice = null;
      Activity activity = getActivity();
      if (null != activity) {
        activity.finish();
      }
    }
  };

  // STEP /////////////////////////////////////////////////////////////////////////////////////////

  /* FUNC - set camera outputs */
  private int mSensorOrientation;

  private Size mPreviewSize;

  private boolean mFlashSupported;

  private ImageReader mImageReader;

  private final static int BASE_DIMENSION_WIDTH = 0;
  private final static int BASE_DIMENSION_HEIGHT = 1;

  private boolean mSelfieMode = false;

  private int mHardwareLevel;

  /**
   * Sets up member variables related to camera.
   * <p>
   * 1. Select a {@link CameraDevice}
   * - [TODO] Choose the camera you want to use to capture image. (Rear, Front)
   * <p>
   * 2. Check physical orientation of the camera sensor
   * - Orthogonality between the natural orientation of device and camera sensor
   * -> width, height swapping
   * <p>
   * 3. Get constraints
   * - Display size (resolution) -> maximum constraints
   * - Display aspect ratio -> specific aspect ratio
   * <p>
   * 4. Set {@link ImageReader}
   * - Format, size, max shot
   * - OnImageAvailableListener, BackgroundHandler
   * <p>
   * 5. Set preview size
   */
  @SuppressWarnings({"SuspiciousNameCombination", "ConstantConditions"})
  private void setUpCameraOutputs() {
    Activity activity = getActivity();
    CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
    try {
      for (String cameraId : manager.getCameraIdList()) {
        CameraCharacteristics characteristics
            = manager.getCameraCharacteristics(cameraId);

        Integer expectedLensFacing = mSelfieMode ? CameraCharacteristics.LENS_FACING_FRONT
            : CameraCharacteristics.LENS_FACING_BACK;

        Integer lensFacing = characteristics.get(CameraCharacteristics.LENS_FACING);
        if (lensFacing == null || !Objects.equals(lensFacing, expectedLensFacing)) {
          continue;
        }

        //noinspection ConstantConditions
        mHardwareLevel = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);

        StreamConfigurationMap map = characteristics.get(
            CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        if (map == null) {
          continue;
        }

        //
        int displayRotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        mSensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);

        boolean isOrthogonal = checkOrthogonality(displayRotation, mSensorOrientation);

        // Get aspectRation what we want
        Point displaySize = new Point();
        activity.getWindowManager().getDefaultDisplay().getSize(displaySize);

        Size aspectRatio = isOrthogonal ? new Size(displaySize.y, displaySize.x) :
            new Size(displaySize.x, displaySize.y);

        // Get base line and its length
        int baseDimension = isOrthogonal ? BASE_DIMENSION_HEIGHT : BASE_DIMENSION_WIDTH;
        int baseLength = displaySize.x;

        Size imageSize = choosePictureSize(map.getOutputSizes(ImageFormat.JPEG),
            aspectRatio, baseDimension);
        mImageReader = ImageReader.newInstance(imageSize.getWidth(), imageSize.getHeight(),
            ImageFormat.JPEG, 2);
        mImageReader.setOnImageAvailableListener(
            mOnImageAvailableListener, mBackgroundHandler);

        // Choose preview size
        mPreviewSize = choosePreviewSize(map.getOutputSizes(SurfaceTexture.class),
            imageSize, baseLength, baseDimension);

        mTextureView.setAspectRatio(
            mPreviewSize.getHeight(), mPreviewSize.getWidth()
        );

        // Set flash
        Boolean available = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
        mFlashSupported = (available == null) ? false : available;

        mCameraId = cameraId;
        return;
      }
    } catch (CameraAccessException e) {
      e.printStackTrace();
    } catch (NullPointerException e) {
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

  /* FUNC - Choose max resolution picture size */
  private static Size choosePictureSize(Size[] choices, Size aspectRatio, int baseDimension) {
    List<Size> feasibleSize = new ArrayList<>();
    List<Size> longerHeight = new ArrayList<>();
    List<Size> longerWidth = new ArrayList<>();

    int w = aspectRatio.getWidth();
    int h = aspectRatio.getHeight();

    for (Size option : choices) {
      Log.e(TAG, "Image capture - " + option.getWidth() + " : " + option.getHeight());
      if (option.getHeight() == option.getWidth() * h / w) {
        feasibleSize.add(option);
      } else if (option.getHeight() > option.getWidth() * h / w) {
        longerHeight.add(option);
      } else {
        longerWidth.add(option);
      }
    }

    if (feasibleSize.size() > 0) {
      return Collections.max(feasibleSize, new SizeComparator());
    } else {
      Log.e(TAG, "Couldn't find any suitable preview size.");
      switch (baseDimension) {
        case BASE_DIMENSION_HEIGHT:
          return Collections.max(longerHeight, new SizeComparator());
        case BASE_DIMENSION_WIDTH:
          return Collections.max(longerWidth, new SizeComparator());
        default:
          Log.e(TAG, "Invalid value for baseSide parameter.");
          return choices[0];
      }
    }
  }

  /* FUNC - Choose optimal preview size */
  private static Size choosePreviewSize(Size[] choices, Size aspectRatio,
                                        int baseLength, int baseDimension) {

    // Collect the supported resolutions that are at least as big as the preview Surface
    List<Size> bigEnough = new ArrayList<>();
    // Collect the supported resolutions that are smaller than the preview Surface
    List<Size> notBigEnough = new ArrayList<>();

    // TODO - Ignore the case that there is no size which has same aspect ratio with image capture.

    int w = aspectRatio.getWidth();
    int h = aspectRatio.getHeight();

    for (Size option : choices) {
      int optionLength = (BASE_DIMENSION_WIDTH == baseDimension) ?
          option.getWidth() : option.getHeight();

      // Confirm feasibility : Maximum size && Aspect ratio
      if (option.getHeight() == option.getWidth() * h / w) {
        // Big enough
        if (optionLength >= baseLength) {
          Log.e(TAG, "Big enough" + option.getWidth() + " : " + option.getHeight());
          bigEnough.add(option);
        } else {
          Log.e(TAG, "Not Big enough" + option.getWidth() + " : " + option.getHeight());
          notBigEnough.add(option);
        }
      }
    }

    if (bigEnough.size() > 0) {
      return Collections.min(bigEnough, new SizeComparator());
    } else if (notBigEnough.size() > 0) {
      return Collections.max(notBigEnough, new SizeComparator());
    } else {
      Log.e(TAG, "Couldn't find any suitable preview size.");
      return choices[0];
    }
  }

  static class SizeComparator implements Comparator<Size> {
    @Override
    public int compare(Size lhs, Size rhs) {
      // We cast here to ensure the multiplications won't overflow
      return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
          (long) rhs.getWidth() * rhs.getHeight());
    }

  }

  // STEP /////////////////////////////////////////////////////////////////////////////////////////

  /* FUNC - Image available callback listener */
  private ImageReader.OnImageAvailableListener mOnImageAvailableListener =
      new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
          mBackgroundHandler.post(new ImageSaver(reader.acquireNextImage(), mFile));
        }
      };

  /* FUNC - Thread for image saving */
  private static class ImageSaver implements Runnable {

    private final Image mImage;
    private final File mFile;

    ImageSaver(Image image, File file) {
      mImage = image;
      mFile = file;
    }

    @Override
    public void run() {
      ByteBuffer buffer = mImage.getPlanes()[0].getBuffer();
      byte[] bytes = new byte[buffer.remaining()];
      buffer.get(bytes);
      FileOutputStream output = null;

      try {

        output = new FileOutputStream(mFile);
        output.write(bytes);

      } catch (IOException e) {
        e.printStackTrace();
      } finally {
        mImage.close();
        if (null != output) {
          try {
            output.close();
          } catch (IOException e) {
            e.printStackTrace();
          }
        }
      }
    }
  }

  // STEP /////////////////////////////////////////////////////////////////////////////////////////

  /* FUNC - UI LOGIC */
  private static final int UI_LOGIC_RELEASE_CAPTURE_BUTTON = 0x0001;
  private static final int UI_LOGIC_ACTIVATE_OK_BUTTON = 0x0002;
  private static final int UI_LOGIC_DEACTIVATE_OK_BUTTON = 0x003;

  @Retention(RetentionPolicy.SOURCE)
  @IntDef({UI_LOGIC_RELEASE_CAPTURE_BUTTON, UI_LOGIC_ACTIVATE_OK_BUTTON,
      UI_LOGIC_DEACTIVATE_OK_BUTTON})
  public @interface UiLogic {
  }

  private static final String UI_LOGIC = "UiLogic";

  public void requestUiChange(@UiLogic int request) {
    // Request some action to Ui thread.
    Bundle bundle = new Bundle();
    bundle.putSerializable(UI_LOGIC, request);

    Message msg = Message.obtain();
    msg.setData(bundle);
    mUiThreadHandler.sendMessage(msg);
  }

  /* FUNC - UI Thread Handler */
  Handler mUiThreadHandler = new Handler(Looper.getMainLooper()) {
    @Override
    public void handleMessage(Message msg) {
      int uiLogicRequest = msg.getData().getInt(UI_LOGIC);

      switch (uiLogicRequest) {
        case UI_LOGIC_RELEASE_CAPTURE_BUTTON:
          captureButton.setImageResource(R.drawable.camera_shoot_button_release_70dp);
          break;
        case UI_LOGIC_ACTIVATE_OK_BUTTON:
          // TODO - Activate ok button
          break;
        case UI_LOGIC_DEACTIVATE_OK_BUTTON:
          // TODO
          break;
        default:
          // Nothing to do.
          break;
      }
    }
  };

  // STEP /////////////////////////////////////////////////////////////////////////////////////////

  /* FUNC - Compound Session */
  /**
   * Reusable builder for configuration of request builder.
   */
  private CaptureRequest.Builder mPreviewRequestBuilder;
  private CaptureRequest mPreviewRequest;
  private CameraCaptureSession mCompoundSession;

  private void createCameraCompoundSession() {
    try {
      SurfaceTexture texture = mTextureView.getSurfaceTexture();
      assert texture != null;

      // We configure the size of default buffer to be the size of camera preview we want.
      texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());

      // This is the output Surface we need to start preview.
      // This object will be registered into previewRequest and previewSession
      Surface surface = new Surface(texture);

      mPreviewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
      mPreviewRequestBuilder.addTarget(surface);

      // noinspection ArraysAsListWithZeroOrOneArgument
      mCameraDevice.createCaptureSession(Arrays.asList(surface, mImageReader.getSurface()),
          mCompoundSessionStateCallback, null);
    } catch (CameraAccessException e) {
      e.printStackTrace();
    }
  }

  /* FUNC - Compound session state callback*/
  private CameraCaptureSession.StateCallback mCompoundSessionStateCallback =
      new CameraCaptureSession.StateCallback() {
        @Override
        public void onConfigured(@NonNull CameraCaptureSession session) {
          if (null == mCameraDevice) {
            return;
          }

          mCompoundSession = session;

          try {
            // Initialize 3A mode for camera preview.
            initialize3aMode(mPreviewRequestBuilder);

            // Finally, we start displaying the camera preview.
            mPreviewRequest = mPreviewRequestBuilder.build();
            mCompoundSession.setRepeatingRequest(mPreviewRequest, mCaptureCallback,
                mBackgroundHandler);

          } catch (CameraAccessException e) {
            e.printStackTrace();
          }
        }

        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
          BasicUtil.showToast(getActivity(), "Failed");
        }
      };

  // STEP /////////////////////////////////////////////////////////////////////////////////////////

  /* FUNC - Compound session preview request capture callback */
  private int mState = STATE_PREVIEW;

  private static final int STATE_PREVIEW = 0;
  private static final int STATE_WAITING_FOCUS_LOCK = 1;
  private static final int STATE_WAITING_PRECAPTURE = 3;
  private static final int STATE_WAITING_NON_PRECAPTURE = 4;
  private static final int STATE_PICTURE_TAKEN = 5;

  private CameraCaptureSession.CaptureCallback mCaptureCallback =
      new CameraCaptureSession.CaptureCallback() {

        private void process(CaptureResult result) {
          switch (mHardwareLevel) {
            case CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3 :
            case CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL :
              fullProcess(result);
              break;
            case CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED :
            case CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY :
              limitedProcess();
              break;
          }
        }

        private void fullProcess(CaptureResult result) {
          switch (mState) {
            case STATE_PREVIEW :
              // Do nothing
              break;
            case STATE_WAITING_FOCUS_LOCK: { /* Only AF is triggered */
              Integer afState = result.get(CaptureResult.CONTROL_AF_STATE);
              if (null == afState || CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED == afState
                  || CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED == afState) {
                Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);

                if (null == aeState || CaptureResult.CONTROL_AE_STATE_CONVERGED == aeState) {
                  Integer awbState = result.get(CaptureResult.CONTROL_AWB_STATE);

                  if (null == awbState || CaptureResult.CONTROL_AWB_STATE_CONVERGED == awbState) {
                    mState = STATE_PICTURE_TAKEN;
                    captureStillPicture(); // START

                  }

                } else if (CaptureRequest.CONTROL_AE_STATE_FLASH_REQUIRED == aeState) {
                  mState = STATE_WAITING_PRECAPTURE;
                  triggerPrecapture(mPreviewRequestBuilder);

                }
              }
              break;
            }

            case STATE_WAITING_PRECAPTURE: { /* AF and AE precapture are triggered */
              Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);

              if (aeState == null || aeState == CaptureRequest.CONTROL_AE_STATE_PRECAPTURE) {
                mState = STATE_WAITING_NON_PRECAPTURE;
              }
              break;
            }

            case STATE_WAITING_NON_PRECAPTURE: {
              // CONTROL_AE_STATE can be null on some devices
              Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
              if (null == aeState || aeState != CaptureResult.CONTROL_AE_STATE_PRECAPTURE) {
                Integer awbState = result.get(CaptureResult.CONTROL_AWB_STATE);

                if (null == awbState || CaptureResult.CONTROL_AWB_STATE_CONVERGED == awbState) {
                  mState = STATE_PICTURE_TAKEN;
                  captureStillPicture(); // START
                }

              }
              break;
            }
          }
        }

        private void limitedProcess() {
          switch (mState) {
            case STATE_PREVIEW :
              // Do nothing
              break;
            case STATE_WAITING_FOCUS_LOCK :
              mState = STATE_PICTURE_TAKEN;
              captureStillPicture();
          }
        }

        @Override
        public void onCaptureProgressed(@NonNull CameraCaptureSession session,
                                        @NonNull CaptureRequest request,
                                        @NonNull CaptureResult partialResult) {
          Log.e(TAG,"STATE" + partialResult.get(CaptureResult.CONTROL_AF_STATE));
          if (request.get(CaptureRequest.CONTROL_AF_TRIGGER) == CaptureRequest.CONTROL_AF_TRIGGER_START) {
            Log.e(TAG, "Trigger focusing on preview");
          }
          process(partialResult);
        }

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                       @NonNull CaptureRequest request,
                                       @NonNull TotalCaptureResult result) {
          Log.e(TAG,"STATE" + result.get(CaptureResult.CONTROL_AF_STATE));
          if (request.get(CaptureRequest.CONTROL_AF_TRIGGER) == CaptureRequest.CONTROL_AF_TRIGGER_START) {
            Log.e(TAG, "Trigger focusing on preview");
          }
          process(result);
        }
      };


  // STEP /////////////////////////////////////////////////////////////////////////////////////////

  private void takePicture() {
    mState = STATE_WAITING_FOCUS_LOCK;
    lockFocus(mPreviewRequestBuilder);
  }

  /* Set auto lock focusing */
  private void lockFocus(CaptureRequest.Builder initialBuilder) {
    try {
      // Try lock focus
      initialBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
          CameraMetadata.CONTROL_AF_TRIGGER_START);

      mCompoundSession.capture(initialBuilder.build(), mCaptureCallback,
          mBackgroundHandler);
    } catch (CameraAccessException e) {
      e.printStackTrace();
    }
  }

  private void unlockFocus(CaptureRequest.Builder initialBuilder) {
    try {
      // Initialize 3A mode for camera preview.
      initialBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
          CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);

      mCompoundSession.capture(initialBuilder.build(), mCaptureCallback,
          mBackgroundHandler);
    } catch (CameraAccessException e) {
      e.printStackTrace();
    }
  }

  private void startPreview() {
    try {
      // Finally, we restart displaying the camera preview.
      mCompoundSession.setRepeatingRequest(mPreviewRequest, mCaptureCallback, mBackgroundHandler);
    } catch (CameraAccessException e) {
      e.printStackTrace();
    }
  }

  private void stopPreview() {
    try {
      mCompoundSession.stopRepeating();
    } catch (CameraAccessException e) {
      e.printStackTrace();
    }
  }

  /* If it is failed to lock focus, we should set precapture sequence as a next option */
  private void triggerPrecapture(CaptureRequest.Builder initialBuilder) {
    try {
      initialBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
          CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);
      mCompoundSession.capture(initialBuilder.build(), mCaptureCallback,
          mBackgroundHandler);
    } catch (CameraAccessException e) {
      e.printStackTrace();
    }
  }


  // STEP /////////////////////////////////////////////////////////////////////////////////////////

  private void captureStillPicture() {
    try {
      final Activity activity = getActivity();
      if (null == activity || null == mCameraDevice) {
        return;
      }

      final CaptureRequest.Builder captureBuilder =
          mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
      captureBuilder.addTarget(mImageReader.getSurface());

      initialize3aMode(captureBuilder);

      captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, mSensorOrientation);

      CameraCaptureSession.CaptureCallback CaptureCallback
          = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                       @NonNull CaptureRequest request,
                                       @NonNull TotalCaptureResult result) {
          // Shutter sound
          mShutter.play(MediaActionSound.SHUTTER_CLICK);

          // Release button
          requestUiChange(UI_LOGIC_RELEASE_CAPTURE_BUTTON);

          // Show test
          BasicUtil.showToast(getActivity(), "Saved : " + mFile);
          Log.d(TAG, mFile.toString());
          unlockFocus(mPreviewRequestBuilder);

          mState = STATE_PREVIEW;
          startPreview();

          // rescan file
          rescanFile(mFile);
        }
      };

      stopPreview();
      mCompoundSession.capture(captureBuilder.build(), CaptureCallback, null);
    } catch (CameraAccessException e) {
      e.printStackTrace();
    }
  }

  // STEP /////////////////////////////////////////////////////////////////////////////////////////

  private void initialize3aMode(CaptureRequest.Builder previewRequestBuilder) {

    switch (mHardwareLevel) {
      case CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3 :
      case CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL :
        setPreviewAutoFocus(previewRequestBuilder);
        setPreviewAutoExposure(previewRequestBuilder);
        setPreviewAutoWhiteBalance(previewRequestBuilder);
        break;
      case CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED :
      case CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY :
        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
        break;
    }


  }

  /**
   * Set or reset AF mode of previewRequestBuilder.
   *
   * @param previewRequestBuilder Request builder for setting preview capture.
   */
  private void setPreviewAutoFocus(CaptureRequest.Builder previewRequestBuilder) {
    previewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
    previewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
        CaptureRequest.CONTROL_AF_TRIGGER_IDLE);
  }

  /**
   * Set or reset AE mode of previewRequestBuilder.
   *
   * @param previewRequestBuilder Request builder for setting preview capture.
   */
  private void setPreviewAutoExposure(CaptureRequest.Builder previewRequestBuilder) {
    if (mFlashSupported) {
      previewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
          CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
    } else {
      previewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
          CaptureRequest.CONTROL_AE_MODE_ON);
    }

    previewRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
        CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_IDLE);
  }

  /**
   * Set or reset AWB mode of previewRequestBuilder.
   *
   * @param previewRequestBuilder Request builder for setting preview capture.
   */
  private void setPreviewAutoWhiteBalance(CaptureRequest.Builder previewRequestBuilder) {
    previewRequestBuilder.set(CaptureRequest.CONTROL_AWB_MODE,
        CaptureRequest.CONTROL_AWB_MODE_AUTO);
  }

  // STEP /////////////////////////////////////////////////////////////////////////////////////////

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

  // STEP /////////////////////////////////////////////////////////////////////////////////////////

  /* FUNC - TextureView */
  private final TextureView.SurfaceTextureListener mSurfaceTextureListener
      = new TextureView.SurfaceTextureListener() {

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
      openCamera();
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
      return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture texture) {
    }

  };

  // STEP /////////////////////////////////////////////////////////////////////////////////////////

  /* FUNC - Permission logic */
  private static final String FRAGMENT_DIALOG = "dialog";

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
