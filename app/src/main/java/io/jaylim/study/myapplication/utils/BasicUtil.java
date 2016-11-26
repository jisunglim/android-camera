package io.jaylim.study.myapplication.utils;

import android.app.Activity;
import android.content.Context;
import android.widget.Toast;

/**
 * Created by jaylim on 11/16/2016.
 */

public class BasicUtil {

    public static <T> T checkNull(T nullable) {
        return (nullable != null) ? nullable : null;
    }

    /* FUNC - utils */
    public static void showToast(Activity activity, final String text) {
        activity.runOnUiThread(() -> Toast.makeText(activity, text, Toast.LENGTH_SHORT).show());
    }
}
