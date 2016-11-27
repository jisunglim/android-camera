package io.jaylim.study.myapplication;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.app.AppCompatActivity;

import io.jaylim.study.myapplication.preview.PreviewFragment;

/**
 * Created by jaylim on 11/22/2016.
 */

public abstract class SingleFragmentActivity extends AppCompatActivity {

    public abstract Fragment createDefaultFragment();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_single_fragment);
        if (null == savedInstanceState) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, createDefaultFragment())
                    .commit();
        }
    }
}
