package io.jaylim.study.myapplication.capture;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.Window;
import android.view.WindowManager;

import io.jaylim.study.myapplication.SingleFragmentActivity;

/**
 * Created by jaylim on 11/22/2016.
 */

public class CaptureActivity extends SingleFragmentActivity {

    public static Intent newIntent(Context packageContext) {
        return new Intent(packageContext, CaptureActivity.class);
    }

    @Override
    public Fragment createDefaultFragment() {
        return CaptureFragment.newInstance();
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
