package io.jaylim.study.myapplication.video;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.app.AppCompatActivity;
import android.view.Window;
import android.view.WindowManager;

import io.jaylim.study.myapplication.SingleFragmentActivity;

/**
 * Created by jaylim on 11/25/2016.
 */

public class VideoActivity extends SingleFragmentActivity{

  @Override
  public Fragment createDefaultFragment() {
    return VideoFragment.newInstance();
  }

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    // Set fullscreen preview window
    requestWindowFeature(Window.FEATURE_NO_TITLE);
    getWindow().setFlags(
        WindowManager.LayoutParams.FLAG_FULLSCREEN,
        WindowManager.LayoutParams.FLAG_FULLSCREEN
    );
    super.onCreate(savedInstanceState);
  }
}
