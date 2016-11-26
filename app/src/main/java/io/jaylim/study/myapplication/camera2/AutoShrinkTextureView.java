package io.jaylim.study.myapplication.camera2;

import android.content.Context;
import android.util.AttributeSet;
import android.view.TextureView;
import android.view.View;

/**
 * Created by jaylim on 11/22/2016.
 */

public class AutoShrinkTextureView extends TextureView {

    private int mRatioWidth = 0;
    private int mRatioHeight = 0;

    public AutoShrinkTextureView(Context context) {
        this(context, null);
    }

    public AutoShrinkTextureView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AutoShrinkTextureView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    /**
     * Sets the aspect ratio for this view. The size of the view will be measured based on
     * the ratio calculated from the parameters.
     * <p>
     * This may help get the ratio of width to height from previewSize.
     *
     * @param width  Relative horizontal size
     * @param height Relative vertical size
     */
    public void setAspectRatio(int width, int height) {
        if (width < 0 || height < 0) {
            throw new IllegalArgumentException("Size cannot be negative.");
        }
        mRatioWidth = width;
        mRatioHeight = height;
        requestLayout();
    }

    /*
     * Implementing onMeasure() is your opportunity to tell Android how big you want your
     * custom view to be dependent the layout constraints provided by the parent.
     * It is also your custom view's opportunity to learn what those layout constraints are
     * (in case you want to behave differently in a match_parent situation that a wrap_content
     * situation). Those constraints are packaged up into the MeasureSpec values that are passed
     * into the method. Here is a rough correlation of the mode values:
     *
     * * EXACTLY means the layout_width or layout_height value was set to a specific value.
     *           You should probably make your view this size. This can also get triggered when
     *           match_parent is used, to set the size exactly to the parent view. (this is
     *           layout dependent in the framework).
     *
     * * AT_MOST typically means the layout_width or layout_height value was set to match_parent
     *           or wrap_content where a maximum size is needed (this is layout dependent in the
     *           framework), and the size of the parent dimension is the value. You should not be
     *           any larger than this size.
     *
     * * UNSPECIFIED typically means the layout_width or layout_height value was set to wrap_content
     *               with no restrictions. You can be whatever size you would like. Some layouts
     *               also use this callback to figure out your desired size before determine what
     *               specs to actually pass you again in a second measure request.
     *
     * The contract that exists with onMeasure() is that setMeasuredDimension() MUST be called at
     * the end with the size you would like the view to be. This method is called by all the
     * framework implementations, including the default implementation found in View, which is why
     * it is safe to call super instead if that fits your use case.
     *
     * Granted, because the framework does apply a default implementation, it may not be necessary
     * for you to override this method, but you may see clipping in cases where the view space is
     * smaller than your content if you do not, and if you lay out your custom view with wrap_content
     * in both directions, your view may not show up at all because the framework doesn't know how
     * large it is!
     *
     * Generally, if you are overriding View and not another existing widget, it is probably a good
     * idea to provide an implementation, even if it is as simple as something like this:
     *
     */
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // Set layout as default
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        // It is not necessary to get mode ( EXACTLY / AT_MOST / UNSPECIFIED ) if you set the
        // layout_width or layout_height as a match_parent.
        // - Since the metrics are defined relatively, the mode EXACTLY is not the case.
        // - Since the size is set to be match_parent, the mode UNSPECIFIED is not expected.
        int widthMode = View.MeasureSpec.getMode(widthMeasureSpec);
        int heightMode = View.MeasureSpec.getMode(heightMeasureSpec);
        if (!(widthMode == View.MeasureSpec.AT_MOST) || !(heightMode == View.MeasureSpec.AT_MOST)) {
            return;
        }

        // Get layout size from its parent view.
        int widthSize = View.MeasureSpec.getSize(widthMeasureSpec);
        int heightSize = View.MeasureSpec.getSize(heightMeasureSpec);

        // If aspect ratio aren't set yet, set the size of this view as a default size.
        if (0 == mRatioWidth || 0 == mRatioHeight) {
            setMeasuredDimension(widthSize, heightSize);
        } else {
            if (widthSize < heightSize * mRatioWidth / mRatioHeight) {
                setMeasuredDimension(widthSize, widthSize * mRatioHeight / mRatioWidth);
            } else {
                setMeasuredDimension(heightSize * mRatioWidth / mRatioHeight, heightSize);
            }
        }
    }
}
