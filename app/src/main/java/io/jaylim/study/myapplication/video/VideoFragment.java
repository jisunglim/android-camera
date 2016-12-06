package io.jaylim.study.myapplication.video;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Point;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.MediaActionSound;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.IntDef;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v13.app.FragmentCompat;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Stack;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import butterknife.OnTouch;
import butterknife.Unbinder;
import io.jaylim.study.myapplication.R;

/**
 * Created by jaylim on 11/25/2016.
 */

public class VideoFragment extends Fragment {

  private static final String TAG = VideoFragment.class.getSimpleName();

  public static VideoFragment newInstance() {
    return new VideoFragment();
  }

  // STEP - BIND VIEW /////////////////////////////////////////////////////////////////////////////

  @Nullable
  @Override
  public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
                           @Nullable Bundle savedInstanceState) {
    return inflater.inflate(R.layout.fragment_video, container, false);
  }

  Unbinder mUnbinder;

  @BindView(R.id.video_shoot_button)
  ImageButton mShootButton;

  @BindView(R.id.video_music_button)
  ImageButton mMusicButton;
  @BindView(R.id.video_library_button)
  ImageButton mLibraryButton;

  @BindView(R.id.video_selfie_mode_button)
  ImageButton mSelfieButton;
  @BindView(R.id.video_music_cut_button)
  ImageButton mCutButton;
  @BindView(R.id.video_ok_button)
  ImageButton mOkButton;

  @BindView(R.id.video_texture_view)
  ResizableTextureView mTextureView;

  @OnClick(R.id.video_selfie_mode_button)
  public void _shiftSelfieMode(View v) {
    closeCamera();
    mSelfieMode = !mSelfieMode;
    openCamera();

  }

  @OnClick(R.id.video_texture_view)
  public void _triggerFocus(View v) {

  }

  @OnTouch(R.id.video_shoot_button)
  public boolean _shootVideo(View v, MotionEvent e) {
    switch (e.getAction()) {
      case MotionEvent.ACTION_DOWN:
        mShootButton.setImageResource(R.drawable.camera_shoot_button_hold_70dp);
        setUpVideoFile();
        startRecordingVideo();
        return true;
      case MotionEvent.ACTION_UP:
        mShootButton.setImageResource(R.drawable.camera_shoot_button_release_70dp);
        stopRecordingVideo();
        return true;
      default:
        return false;
    }
  }

  @OnClick(R.id.video_stack_bar)
  public void _deleteLatestVideo() {
    popVideoFile();
  }

  @Override
  public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);
    mUnbinder = ButterKnife.bind(this, view);
  }

  @Override
  public void onDestroyView() {
    mUnbinder.unbind();
    super.onDestroyView();
  }

  // STEP - LIFECYCLE /////////////////////////////////////////////////////////////////////////////

  @Override
  public void onActivityCreated(@Nullable Bundle savedInstanceState) {
    super.onActivityCreated(savedInstanceState);
    setUpStorageDir();
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
  private File mVideoFile;
  private Stack<File> mVideoStack = new Stack<>();

  private void setUpStorageDir() {
    File storageRoot = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);
    File dir = new File(storageRoot.getAbsolutePath() + "/camera_video_test");
    if (dir.isDirectory() || dir.mkdirs()) {
      mDir = dir;
    } else {
      // TODO - How to solve this problem?
      Log.e(TAG, "There was problem to find out application-specified directory. " +
          "The picture will be downloaded into the application root directory.");
      mDir = getActivity().getExternalFilesDir(null);
    }
  }

  private void setUpVideoFile() {
    String fileName = String.format(Locale.US, "%d.mp4", System.currentTimeMillis());
    mVideoFile = new File(mDir, fileName);
  }

  private void pushVideoFile() {
    if (mVideoFile != null) {
      mVideoStack.push(mVideoFile);
    }
    if (!mVideoStack.isEmpty()) {
      requestUiChange(UI_LOGIC_ACTIVATE_OK_BUTTON);
    }

  }

  private void popVideoFile() {
    if (!mVideoStack.isEmpty()) {
      File video = mVideoStack.pop();
      Toast.makeText(getActivity(), video.toString(), Toast.LENGTH_SHORT).show();
    }
    if (mVideoStack.isEmpty()) {
      requestUiChange(UI_LOGIC_DEACTIVATE_OK_BUTTON);
    }
  }

  // STEP - TEXTURE VIEW //////////////////////////////////////////////////////////////////////////

  private TextureView.SurfaceTextureListener mSurfaceTextureListener =
      new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
          openCamera();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
          // Size changed
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
          return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {
          // texture view updated
        }
      };

  // STEP - UI MAIN THREAD ////////////////////////////////////////////////////////////////////////

  /* FUNC - UI LOGIC */
  private static final int UI_LOGIC_RELEASE_CAPTURE_BUTTON = 0x0001;
  private static final int UI_LOGIC_HOLD_CAPTURE_BUTTON = 0x0002;
  private static final int UI_LOGIC_ACTIVATE_OK_BUTTON = 0x0003;
  private static final int UI_LOGIC_DEACTIVATE_OK_BUTTON = 0x004;

  @Retention(RetentionPolicy.SOURCE)
  @IntDef({UI_LOGIC_RELEASE_CAPTURE_BUTTON, UI_LOGIC_HOLD_CAPTURE_BUTTON ,
      UI_LOGIC_ACTIVATE_OK_BUTTON, UI_LOGIC_DEACTIVATE_OK_BUTTON})
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
          mShootButton.setImageResource(R.drawable.camera_shoot_button_release_70dp);
          break;
        case UI_LOGIC_HOLD_CAPTURE_BUTTON:
          mShootButton.setImageResource(R.drawable.camera_shoot_button_hold_70dp);
          break;
        case UI_LOGIC_ACTIVATE_OK_BUTTON:
          mOkButton.setImageResource(R.drawable.camera_ok_button_active_30dp);
          mOkButton.setOnClickListener(v -> {
            while (!mVideoStack.isEmpty()) {
              popVideoFile();
            }
          });
          break;
        case UI_LOGIC_DEACTIVATE_OK_BUTTON:
          mOkButton.setImageResource(R.drawable.camera_ok_button_inactive_30dp);
          mOkButton.setOnClickListener(null);
          break;
        default:
          // Nothing to do.
          break;
      }
    }
  };

  // STEP - BACKGROUND THREAD /////////////////////////////////////////////////////////////////////

  private final static String CAMERA_BACKGROUND_HANDLER_THREAD = "CameraBackground";

  HandlerThread mBackgroundThread;

  Handler mBackgroundHandler;

  private void startBackgroundThread() {
    mBackgroundThread = new HandlerThread(CAMERA_BACKGROUND_HANDLER_THREAD);
    mBackgroundThread.start();
    mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
  }

  private void stopBackgroundThread() {
    mBackgroundThread.quitSafely();
    try {
      // Waits forever for this thread to die.
      mBackgroundThread.join();
      mBackgroundThread = null;
      mBackgroundHandler = null;
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }

  // STEP - OPEN/CLOSE CAMERA /////////////////////////////////////////////////////////////////////

  private Semaphore mCameraOpenCloseLock = new Semaphore(1);

  private MediaActionSound mShutter;

  private String mCameraId;

  private CameraDevice mCameraDevice;

  private void openCamera() {

    if (!hasPermissionsGranted(VIDEO_PERMISSIONS)) {
      requestVideoPermissions();
      return;
    }

    setUpCameraOutputs();

    Activity activity = getActivity();
    CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);

    try {
      if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
        throw new RuntimeException("Time out waiting to lock cameraopening");
      }

      //noinspection MissingPermission
      manager.openCamera(mCameraId, mStateCallback, mBackgroundHandler);

    } catch (CameraAccessException e) {
      e.printStackTrace();
    } catch (InterruptedException e) {
      throw new RuntimeException("Interrupted while trying to lock camera open ing.", e);
    }
  }

  private void closeCamera() {
    try {
      mCameraOpenCloseLock.acquire();

      if (null != mShutter) {
        mShutter.release();
        mShutter = null;
      }
      if (null != mCameraDevice) {
        mCameraDevice.close();
        mCameraDevice = null;
      }
      if (null != mMediaRecorder) {
        mMediaRecorder.release();
        mMediaRecorder = null;
      }
    } catch (InterruptedException e) {
      throw new RuntimeException("Interrupted while trying to lock camera closing.", e);
    } finally {
      mCameraOpenCloseLock.release();
    }
  }

  private final CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {
    @Override
    public void onOpened(@NonNull CameraDevice camera) {

      mCameraDevice = camera;

      // mShutter = new MediaActionSound();
      // mShutter.load(MediaActionSound.START_VIDEO_RECORDING);

      mCameraOpenCloseLock.release();

      startPreview();
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

  // STEP - SETUP CAMERA OUTPUT ///////////////////////////////////////////////////////////////////

  private boolean mSelfieMode = false;

  private int mHardwareLevel;

  private int mSensorOrientation;

  private Size mVideoSize;
  private Size mPreviewSize;

  private MediaRecorder mMediaRecorder;

  private final static int BASE_DIMENSION_WIDTH = 0;
  private final static int BASE_DIMENSION_HEIGHT = 1;

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

        mHardwareLevel = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);

        StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        if (map == null) {
          continue;
        }

        int displayRotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        mSensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);

        boolean isOrthogonal = checkOrthogonality(displayRotation, mSensorOrientation);

        Point displaySize = new Point();
        activity.getWindowManager().getDefaultDisplay().getSize(displaySize);

        Size aspectRatio = isOrthogonal ? new Size(displaySize.y, displaySize.x) :
            new Size(displaySize.x, displaySize.y);

        int baseDimension = isOrthogonal ? BASE_DIMENSION_HEIGHT : BASE_DIMENSION_WIDTH;
        int baseLength = displaySize.x;

        mVideoSize = chooseVideoSize(map.getOutputSizes(SurfaceTexture.class), aspectRatio,
            baseDimension);

        mPreviewSize = choosePreviewSize(map.getOutputSizes(SurfaceTexture.class), mVideoSize,
            baseLength, baseDimension);

        mTextureView.setAspectRatio(
            mPreviewSize.getHeight(), mPreviewSize.getWidth()
        );

        mCameraId = cameraId;

        return;
      }
    } catch (CameraAccessException e) {
      e.printStackTrace();
    }
  }

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

  private static Size chooseVideoSize(Size[] choices, Size aspectRatio, int baseDimension) {
    List<Size> feasibleSize = new ArrayList<>();
    List<Size> longerHeight = new ArrayList<>();
    List<Size> longerWidth = new ArrayList<>();

    int w = aspectRatio.getWidth();
    int h = aspectRatio.getHeight();

    for (Size option : choices) {
      Log.e(TAG, "Image capture - " + option.getWidth() + " : " + option.getHeight());
      if (option.getHeight() <= 1080) {
        if (option.getHeight() == option.getWidth() * h / w) {
          feasibleSize.add(option);
        } else if (option.getHeight() > option.getWidth() * h / w) {
          longerHeight.add(option);
        } else {
          longerWidth.add(option);
        }
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
          return choices[choices.length - 1];
      }
    }
  }

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

  // TODO - SETUP MEDIA RECORDER //////////////////////////////////////////////////////////////////

  private void setUpMediaRecorder() throws IOException {
    if (mMediaRecorder == null) {
      mMediaRecorder = new MediaRecorder();
    } else {
      mMediaRecorder.reset();
    }
    mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
    mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
    mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
    mMediaRecorder.setOutputFile(mVideoFile.getAbsolutePath());

    mMediaRecorder.setVideoEncodingBitRate(10000000);
    mMediaRecorder.setVideoFrameRate(30);
    mMediaRecorder.setVideoSize(mVideoSize.getWidth(), mVideoSize.getHeight());
    mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
    mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);

    mMediaRecorder.setOrientationHint(mSensorOrientation);

    mMediaRecorder.prepare();
  }

  private void startRecordingVideo() {
    createRecordSession();
  }

  private void stopRecordingVideo() {
    mMediaRecorder.stop();

    Activity activity = getActivity();
    if (null != activity) {
      Toast.makeText(activity, "Video saved: " + mVideoFile.toString(),
          Toast.LENGTH_SHORT).show();
      Log.d(TAG, "Video saved: " + mVideoFile.toString());
    }

    pushVideoFile();
    mVideoFile = null;

    startPreview();
  }

  // TODO - CONTROL PREVIEW ///////////////////////////////////////////////////////////////////////

  private CaptureRequest.Builder mCaptureRequestBuilder;

  private void startPreview() {
    createPreviewSession();
  }

  private void updatePreview() {
    mCaptureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
    try {
      mSession.setRepeatingRequest(mCaptureRequestBuilder.build(), null, mBackgroundHandler);
    } catch (CameraAccessException e) {
      e.printStackTrace();
    }
  }

  // TODO - CREATE SESSION ////////////////////////////////////////////////////////////////////////

  private CameraCaptureSession mSession;

  private void createPreviewSession() {
    try {
      // Surface texture
      SurfaceTexture texture = mTextureView.getSurfaceTexture();
      assert texture != null;
      texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
      Surface previewSurface = new Surface(texture);

      mCaptureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
      mCaptureRequestBuilder.addTarget(previewSurface);

      closeSession();
      mCameraDevice.createCaptureSession(Arrays.asList(previewSurface),
          mPreviewSessionStateCallback, mBackgroundHandler);

    } catch (CameraAccessException e) {
      e.printStackTrace();
    }
  }

  CameraCaptureSession.StateCallback mPreviewSessionStateCallback =
      new CameraCaptureSession.StateCallback() {

        @Override
        public void onConfigured(@NonNull CameraCaptureSession previewSession) {
          mSession = previewSession;
          updatePreview();
        }

        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession previewSession) {}
      };


  private void createRecordSession() {
    try {
      Log.e(TAG, "createRecordSession" + (Looper.myLooper() == Looper.getMainLooper()));
      setUpMediaRecorder();

      // Surface texture
      SurfaceTexture texture = mTextureView.getSurfaceTexture();
      assert texture != null;
      texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
      Surface previewSurface = new Surface(texture);

      // Recorder surface
      Surface recorderSurface = mMediaRecorder.getSurface();

      List<Surface> surfaces = new ArrayList<>();
      surfaces.add(previewSurface);
      surfaces.add(recorderSurface);

      mCaptureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
      mCaptureRequestBuilder.addTarget(previewSurface);
      mCaptureRequestBuilder.addTarget(recorderSurface);

      // noinspection ArraysAsListWithZeroOrOneArgument
      closeSession();
      mCameraDevice.createCaptureSession(surfaces, mRecordSessionStateCallback, mBackgroundHandler);
    } catch (CameraAccessException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }


  CameraCaptureSession.StateCallback mRecordSessionStateCallback =
      new CameraCaptureSession.StateCallback() {

        @Override
        public void onConfigured(@NonNull CameraCaptureSession recordSession) {
          mSession = recordSession;
          updatePreview();

          mMediaRecorder.start();
        }

        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession recordSession) {}
      };

  private void closeSession() {

    if (null != mSession) {
      mSession.close();
      mSession = null;
    }
  }

  // STEP - PERMISSION FOR CAMERA /////////////////////////////////////////////////////////////////

  private static final String PERMISSION_DIALOG = "permissionDialog";

  private static final int REQUEST_VIDEO_PERMISSIONS = 1;

  private static final String[] VIDEO_PERMISSIONS = {
      Manifest.permission.CAMERA,
      Manifest.permission.RECORD_AUDIO,
      Manifest.permission.WRITE_EXTERNAL_STORAGE
  };

  public static class ErrorDialog extends DialogFragment {
    private static final String ARG_MESSAGE = "permissionDeniedMessage";

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
      Activity activity = getActivity();
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
          .setMessage(R.string.video_request_permission)
          .setPositiveButton(android.R.string.ok, (dialog, i) ->
              requestPermissions(VIDEO_PERMISSIONS, REQUEST_VIDEO_PERMISSIONS)
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

  private boolean shouldShowRequestPermissionsRationale(String[] permissions) {
    for (String permission : permissions) {
      if (shouldShowRequestPermissionRationale(permission)) {
        return true;
      }
    }
    return false;
  }

  private void requestVideoPermissions() {
    if (shouldShowRequestPermissionsRationale(VIDEO_PERMISSIONS)) {
      ConfirmationDialog.newInstance()
          .show(getChildFragmentManager(), PERMISSION_DIALOG);

    } else {
      requestPermissions(VIDEO_PERMISSIONS, REQUEST_VIDEO_PERMISSIONS);
    }
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                         @NonNull int[] grantResults) {

    if (requestCode == REQUEST_VIDEO_PERMISSIONS) {
      if (grantResults.length == VIDEO_PERMISSIONS.length) {
        for (int result : grantResults) {
          if (result != PackageManager.PERMISSION_GRANTED) {
            ErrorDialog.newInstance(getString(R.string.video_request_permission))
                .show(getChildFragmentManager(), PERMISSION_DIALOG);
          }
        }

      } else {
        ErrorDialog.newInstance(getString(R.string.video_request_permission))
            .show(getChildFragmentManager(), PERMISSION_DIALOG);
      }
    } else {
      super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }
  }

  private boolean hasPermissionsGranted(String[] permissions) {
    for (String permission : permissions) {
      if (ActivityCompat.checkSelfPermission(getActivity(), permission)
          != PackageManager.PERMISSION_GRANTED) {
        return false;
      }
    }
    return true;
  }
}
