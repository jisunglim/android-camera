package io.jaylim.study.myapplication;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.widget.Button;

import butterknife.BindView;
import butterknife.ButterKnife;
import io.jaylim.study.myapplication.camera1.CameraOneActivity;
import io.jaylim.study.myapplication.camera1.TextureCameraActivity;
import io.jaylim.study.myapplication.camera2.CameraPreviewActivity;

/**
 * Created by jaylim on 11/13/2016.
 */

public class MainActivity extends SingleFragmentActivity {
    @Override
    public Fragment createDefaultFragment() {
        return MainFragment.newInstance();
    }
}
