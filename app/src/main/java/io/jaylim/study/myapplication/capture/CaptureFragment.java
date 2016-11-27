package io.jaylim.study.myapplication.capture;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
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
import android.os.Bundle;
import android.os.Environment;
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
import android.widget.ImageButton;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.Unbinder;
import io.jaylim.study.myapplication.R;
import io.jaylim.study.myapplication.utils.BasicUtil;

/**
 * Created by jaylim on 11/22/2016.
 */

public class CaptureFragment extends Fragment {

  private static final String TAG = CaptureFragment.class.getSimpleName();

  public static CaptureFragment newInstance() {
    return new CaptureFragment();
  }

  private String mCameraId;

  public ResizableTextureView mTextureView;

  @Nullable
  @Override
  public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
                           @Nullable Bundle savedInstanceState) {
    return inflater.inflate(R.layout.fragment_capture, container, false);
  }

  Unbinder mUnbinder;

  @BindView(R.id.capture_capture_button_release)
  ImageButton captureButton;
  @BindView(R.id.capture_selfie_button)
  ImageButton selfieButton;
  @BindView(R.id.capture_live_filter_button)
  ImageButton liveFilterButton;

  @Override
  public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);
    mUnbinder = ButterKnife.bind(this, view);

    captureButton.setOnTouchListener( (v, e) -> {
      switch(e.getAction()) {
        case MotionEvent.ACTION_DOWN :
          captureButton.setImageResource(R.drawable.capture_capture_button_hold_70dp);
          takePicture();
          return true;
        case MotionEvent.ACTION_UP :
          captureButton.setImageResource(R.drawable.capture_capture_button_release_70dp);
          return true;
        default :
          return false;
      }
    });
    selfieButton.setOnClickListener(v -> {
      BasicUtil.showToast(getActivity(), "Selfie <-> Scene");
      closeCamera();
      mSelfieMode = !mSelfieMode;
      openCamera();
    });
    liveFilterButton.setOnClickListener(v -> BasicUtil.showToast(getActivity(), "Live Filter"));

    mTextureView = (ResizableTextureView) view.findViewById(R.id.capture_texture_view);
  }

  File mFile;

  @Override
  public void onActivityCreated(@Nullable Bundle savedInstanceState) {
    super.onActivityCreated(savedInstanceState);
    File sdCard = Environment.getExternalStorageDirectory();
    Log.i(TAG, sdCard.toString());
    File dir = new File(sdCard.getAbsolutePath() + "/camera_capture_test");
    String fileName = String.format("%d.jpg", System.currentTimeMillis());
    if (dir.isDirectory() || dir.mkdirs()) {
      mFile = new File(dir, fileName);
      Log.i(TAG, mFile.toString());
    } else {
      mFile = new File(getActivity().getExternalFilesDir(null), fileName);
      Log.i(TAG, mFile.toString());

    }
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

  @Override
  public void onDestroyView() {
    mUnbinder.unbind();
    super.onDestroyView();
  }

  // Semaphore for safe exit.
  private Semaphore mCameraOpenCloseLock = new Semaphore(1);

  /* FUNC - Open and close camera device */
  private MediaActionSound mShuttuer;
  CameraDevice mCameraDevice;

  private void openCamera() {
    if (ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.CAMERA)
        != PackageManager.PERMISSION_GRANTED) {
      requestCameraPermission();
      return;
    }

    // Shutter sound
    mShuttuer = new MediaActionSound();
    mShuttuer.load(MediaActionSound.SHUTTER_CLICK);


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
      if (null != mShuttuer) {
        mShuttuer.release();
        mShuttuer= null;
      }
      if (null != mCompoundSession) {
        mCompoundSession.close();
        mCompoundSession = null;
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

  //STEP////////////////////////////////////////////////////////////////////////////////////////

  /* FUNC - set camera outputs */
  private int mSensorOrientation;

  private Size mPreviewSize;

  private boolean mFlashSupported;

  private ImageReader mImageReader;

  private final static int BASE_SIDE_WIDTH = 0;
  private final static int BASE_SIDE_HEIGHT = 1;

  private boolean mSelfieMode = false;

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

        // TODO - Do not use front facing camera yet
        Integer cameraMode = mSelfieMode ? CameraCharacteristics.LENS_FACING_FRONT
            : CameraCharacteristics.LENS_FACING_BACK;

        Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
        if (facing != null && !Objects.equals(facing, cameraMode)) {
          continue;
        }

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

        Log.e(TAG, "Device Size - width : " + displaySize.x + ", height : " + displaySize.y);

        Size aspectRatio = isOrthogonal ? new Size(displaySize.y, displaySize.x) :
            new Size(displaySize.x, displaySize.y);

        // Get base line and its length
        int baseSide = isOrthogonal ? BASE_SIDE_HEIGHT : BASE_SIDE_WIDTH;
        int baseLength = displaySize.x;


        // TODO - Image capture only support 4:3 and 16:9 capture on Note5
        Size imageSize = choosePictureSize(map.getOutputSizes(ImageFormat.JPEG),
            aspectRatio, baseSide);
        mImageReader = ImageReader.newInstance(imageSize.getWidth(), imageSize.getHeight(),
            ImageFormat.JPEG, 2);
        mImageReader.setOnImageAvailableListener(
            mOnImageAvailableListener, mBackgroundHandler);

        // Choose preview size
        mPreviewSize = choosePreviewSize(map.getOutputSizes(SurfaceTexture.class),
            imageSize, baseLength, baseSide);

        mTextureView.setAspectRatio(
            mPreviewSize.getHeight(), mPreviewSize.getWidth()
        );

        // Set flash
        Boolean available = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
        mFlashSupported = (available == null) ? false : available;

        Log.e(TAG, "ImageSize - width : " + imageSize.getWidth()
            + ", height : " + imageSize.getHeight());
        Log.e(TAG, "PreviewSize - width : " + mPreviewSize.getWidth()
            + ", height : " + mPreviewSize.getHeight());

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
  private static Size choosePictureSize(Size[] choices, Size aspectRatio, int baseSide) {
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
      switch (baseSide) {
        case BASE_SIDE_HEIGHT:
          return Collections.max(longerHeight, new SizeComparator());
        case BASE_SIDE_WIDTH:
          return Collections.max(longerWidth, new SizeComparator());
        default:
          Log.e(TAG, "Invalid value for baseSide parameter.");
          return choices[0];
      }
    }
  }

  /* FUNC - Choose optimal preview size */
  private static Size choosePreviewSize(Size[] choices, Size aspectRatio,
                                        int baseLength, int baseSide) {

    // Collect the supported resolutions that are at least as big as the preview Surface
    List<Size> bigEnough = new ArrayList<>();
    // Collect the supported resolutions that are smaller than the preview Surface
    List<Size> notBigEnough = new ArrayList<>();

    // TODO - Ignore the case where the size having image capture's aspect ratio don't exist.

    int w = aspectRatio.getWidth();
    int h = aspectRatio.getHeight();

    for (Size option : choices) {
      int optionLength = (BASE_SIDE_WIDTH == baseSide) ? option.getWidth() : option.getHeight();

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

  //STEP////////////////////////////////////////////////////////////////////////////////////////

  /* FUNC - Image available callback listener */
  ImageReader.OnImageAvailableListener mOnImageAvailableListener =
      new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
          BasicUtil.showToast(getActivity(), "Image Available : " + mFile);
          mBackgroundHandler.post(new ImageSaver(reader.acquireNextImage(), mFile));
        }
      };

  /* FUNC - Thread for image saving */
  private static class ImageSaver implements Runnable {

    private final Image mImage;
    private final File mFile;

    public ImageSaver(Image image, File file) {
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

  //STEP////////////////////////////////////////////////////////////////////////////////////////

  /* FUNC - Preview Session */
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

  private CameraCaptureSession.StateCallback mCompoundSessionStateCallback =
      new CameraCaptureSession.StateCallback() {
        @Override
        public void onConfigured(@NonNull CameraCaptureSession session) {
          if (null == mCameraDevice) {
            return;
          }

          mCompoundSession = session;
          try {
            // Auto focus should be continuous for camera preview.
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);

            // Flash is automatically enabled when necessary.
            setAutoExposure(mPreviewRequestBuilder);

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

  /* FUNC - Capture session capture callback */

  private int mState = STATE_PREVIEW;

  private static final int STATE_PREVIEW = 0;
  private static final int STATE_WAITING_LOCK_FOCUS = 1;
  private static final int STATE_WAITING_PRECAPTURE = 2;
  private static final int STATE_WAITING_OTHER_THAN_PRECAPTURE = 3;
  private static final int STATE_PICTURE_TAKEN = 4;

  private CameraCaptureSession.CaptureCallback mCaptureCallback =
      new CameraCaptureSession.CaptureCallback() {

        private void process(CaptureResult result) {
          switch (mState) {
            case STATE_PREVIEW:
              // Nothing to do if we are on preview state (default)
              break;
            case STATE_WAITING_LOCK_FOCUS: {
              Integer afState = result.get(CaptureResult.CONTROL_AF_STATE);
              // No AF, no AE.
              if (null == afState) {
                // One of the destination state for Auto Focus
                captureStillPicture();

              } else if (CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED == afState ||
                  CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED == afState) {

                // CONTROL_AE_STATE can be null on some devices
                Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                if (aeState == null ||
                    aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED) {
                  // One of the destination state for Auto Exposure
                  // Good or bad AF, and Good or no AE.
                  mState = STATE_PICTURE_TAKEN;
                  captureStillPicture();
                } else {
                  // Good or bad AF, and reprocessing AE.
                  runPrecaptureSequence();
                  // At this point it comes out that 'mState = STATE_WAITING_PRECAPTURE' and
                  // AR_PRECAPTURE_TRIGGER_START is set on the 'mPreviewRequest'
                }
              }
              //
              break;
            }
            case STATE_WAITING_PRECAPTURE: {
              // CONTROL_AE_STATE can be null on some devices
              Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
              if (aeState == null ||
                  aeState == CaptureResult.CONTROL_AE_STATE_PRECAPTURE ||
                  aeState == CaptureRequest.CONTROL_AE_STATE_FLASH_REQUIRED) {
                mState = STATE_WAITING_OTHER_THAN_PRECAPTURE;
              }
              break;
            }
            case STATE_WAITING_OTHER_THAN_PRECAPTURE: {
              // CONTROL_AE_STATE can be null on some devices
              Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
              if (aeState == null || aeState != CaptureResult.CONTROL_AE_STATE_PRECAPTURE) {
                // Every AE processing finished and now capture the picture.
                mState = STATE_PICTURE_TAKEN;
                captureStillPicture();
              }
              break;
            }
          }
        }

        @Override
        public void onCaptureProgressed(@NonNull CameraCaptureSession session,
                                        @NonNull CaptureRequest request,
                                        @NonNull CaptureResult partialResult) {
          process(partialResult);
        }

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                       @NonNull CaptureRequest request,
                                       @NonNull TotalCaptureResult result) {
          process(result);
        }
      };

  /*  */

  /* FUNC - Camera stateCallback */
  private final CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {
    @Override
    public void onOpened(@NonNull CameraDevice camera) {
      mCameraOpenCloseLock.release();
      mCameraDevice = camera;
      createCameraCompoundSession();
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

  // TODO ///////////////////////////////////////////////////////////////////////////////////////

  private void takePicture() {
    lockFocus();
  }


  /* Set auto lock focusing */
  private void lockFocus() {
    try {
      // Tell the camera to lock focus before capture the image
      mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
          CameraMetadata.CONTROL_AF_TRIGGER_START);
      mState = STATE_WAITING_LOCK_FOCUS;
      mCompoundSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback,
          mBackgroundHandler);
    } catch (CameraAccessException e) {
      e.printStackTrace();
    }
  }

  private void unlockFocus() {
    mState = STATE_PREVIEW;
    restartSession();
  }

  private void restartSession() {
    if (null != mCompoundSession) {
      mCompoundSession.close();
      mCompoundSession = null;
    }
    createCameraCompoundSession();
  }

  /* If it is failed to lock focus, we should set precapture sequence as a next option */
  private void runPrecaptureSequence() {
    try {
      mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
          CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);
      mState = STATE_WAITING_PRECAPTURE;
      mCompoundSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback,
          mBackgroundHandler);
    } catch (CameraAccessException e) {
      e.printStackTrace();
    }
  }

  /*  */
  private void captureStillPicture() {
    try {
      final Activity activity = getActivity();
      if (null == activity || null == mCameraDevice) {
        return;
      }

      final CaptureRequest.Builder captureBuilder =
          mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
      captureBuilder.addTarget(mImageReader.getSurface());

      captureBuilder.set(CaptureRequest.CONTROL_AF_MODE,
          CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
      setAutoExposure(captureBuilder);

      captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, mSensorOrientation);

      CameraCaptureSession.CaptureCallback CaptureCallback
          = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                       @NonNull CaptureRequest request,
                                       @NonNull TotalCaptureResult result) {
          // Shutter sound
          mShuttuer.play(MediaActionSound.SHUTTER_CLICK);

          BasicUtil.showToast(getActivity(), "Saved : " + mFile);
          Log.d(TAG, mFile.toString());
          unlockFocus();
        }
      };
      mCompoundSession.stopRepeating();
      mCompoundSession.capture(captureBuilder.build(), CaptureCallback, null);
    } catch (CameraAccessException e) {
      e.printStackTrace();
    }
  }

  private void setAutoExposure(CaptureRequest.Builder requestBuilder) {
    if (mFlashSupported) {
      requestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
          CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
    } else {
      requestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
          CaptureRequest.CONTROL_AE_MODE_ON);
    }
  }

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
