package io.jaylim.study.myapplication;

import android.app.Activity;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.Unbinder;
import io.jaylim.study.myapplication.capture.CaptureActivity;
import io.jaylim.study.myapplication.preview.PreviewActivity;
import io.jaylim.study.myapplication.utils.BasicUtil;

/**
 * Created by jaylim on 11/24/2016.
 */

public class MainFragment extends Fragment {

  Unbinder mUnbinder;
  @BindView(R.id.button_camera1_surface)
  Button mCameraPreviewButton;
  @BindView(R.id.button_camera1_texture)
  Button mCameraCaptureButton;
  @BindView(R.id.button_camera2_surface)
  Button mCameraVideoButton;
  @BindView(R.id.button_camera2_texture)
  Button mCameraButton;

  public static Fragment newInstance() {
    return new MainFragment();
  }

  @Nullable
  @Override
  public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
    return inflater.inflate(R.layout.fragment_main, container, false);

  }

  @Override
  public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);
    // Bind view with ButterKnife
    mUnbinder = ButterKnife.bind(this, view);

    Activity activity = getActivity();

    // Set onClickListeners
    mCameraPreviewButton.setOnClickListener(v ->
        startActivity(PreviewActivity.newIntent(activity)));

    mCameraCaptureButton.setOnClickListener(v ->
        startActivity(CaptureActivity.newIntent(activity)));

    mCameraVideoButton.setOnClickListener(v ->
        BasicUtil.showToast(getActivity(), "Unavailable"));

    mCameraButton.setOnClickListener(v ->
        BasicUtil.showToast(getActivity(), "Unavailable"));
  }

  @Override
  public void onDestroyView() {
    mUnbinder.unbind();
    super.onDestroyView();
  }
}
