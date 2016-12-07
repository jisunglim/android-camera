package io.jaylim.study.myapplication;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.support.annotation.UiThread;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;

import java.util.Stack;


/**
 * {@link StackBar} is a horizontal bar that visualizes the progress in the way of real-time and
 * responsive animation. A total amount of time, which will represent a length of the horizontal
 * bar, can be defined by setting attribute {@link R.styleable#StackBar_maximumTime}.
 *
 * User can create new interval by doing some action which is specified by start and end.
 *
 */
@UiThread
public class StackBar extends View {

  private static final String TAG = StackBar.class.getSimpleName();

  /**
   * Default maximum time which is measured by unit second (1s). When you consume all of your
   * given time, the stack bar will be fully (or completely) filled with bars of different sizes.
   */
  private static final int DEFAULT_MAXIMUM_TIME = 15;

  /**
   * Default minimum time which is measured by unit second (1s). It will be marked on the bar
   * so that it indicates the minimum time threshold.
   */
  private static final int DEFAULT_MINIMUM_TIME = 5;

  /**
   * The default background color.
   */
  private static final int DEFAULT_BACKGROUND_COLOR = 0x7fd7dadb;

  /**
   * The default color of bars.
   */
  private static final int DEFAULT_STACK_COLOR = 0xffffffff;

  /**
   * The default color of divider, which is a kind of vertical line that separate two different
   * sub bars positioning between them.
   */
  private static final int DEFAULT_DIVIDER_COLOR = 0xffe8474e;

  private int mMaximumTime;
  private int mMinimumTime;

  private int mBackgroundColor;
  private int mStackColor;
  private int mDividerColor;


  public StackBar(Context context) {
    this(context, null);
  }

  public StackBar(Context context, AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public StackBar(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);

    final TypedArray attrArray = getContext().obtainStyledAttributes(
        attrs, R.styleable.StackBar, defStyle, 0);

    try {
      mMaximumTime = attrArray.getInt(R.styleable.StackBar_maximumTime, DEFAULT_MAXIMUM_TIME);

      mMinimumTime = attrArray.getInt(R.styleable.StackBar_minimumTime, DEFAULT_MINIMUM_TIME);


      mBackgroundColor = attrArray.getColor(R.styleable.StackBar_backgroundColor,
          DEFAULT_BACKGROUND_COLOR);

      mStackColor = attrArray.getColor(R.styleable.StackBar_stackColor, DEFAULT_STACK_COLOR);

      mDividerColor = attrArray.getColor(R.styleable.StackBar_dividerColor, DEFAULT_DIVIDER_COLOR);

    } finally {
      attrArray.recycle();

    }

    initPaint();
  }

  private Paint mThresholdPaint;

  private Paint mBackgroundPaint;

  private Paint mStackPaint;

  private Paint mDividerPaint;

  private void initPaint() {
    mThresholdPaint = new Paint();
    mThresholdPaint.setColor(mDividerColor);
    mThresholdPaint.setStrokeWidth(5.0f);

    mBackgroundPaint = new Paint();
    mBackgroundPaint.setColor(mBackgroundColor);

    mStackPaint = new Paint();
    mStackPaint.setColor(mStackColor);

    mDividerPaint = new Paint();
    mDividerPaint.setColor(mDividerColor);
    mDividerPaint.setStrokeWidth(5.0f);
  }

  @Override
  protected void onDraw(Canvas canvas) {
    int paddingLeft = getPaddingLeft();
    int paddingTop = getPaddingTop();
    int paddingRight = getPaddingRight();
    int paddingBottom = getPaddingBottom();

    int contentWidth = getWidth() - paddingLeft - paddingRight;
    int contentHeight = getHeight() - paddingTop - paddingBottom;

    RectF mBackgroundRectF = new RectF(0.0f, 0.0f, contentWidth, contentHeight);

    float minThreshold = contentWidth * mMinimumTime / mMaximumTime;
    float curOffset = contentWidth/7.0f;
    RectF mStackRectF = new RectF(0.0f, 0.0f, curOffset, contentHeight);


    Log.i(TAG, "draw background : {" + 0.0f + ", " + 0.0f + ", " + contentWidth + ", " + contentHeight + "}");
    canvas.drawRect(mBackgroundRectF, mBackgroundPaint);
    canvas.drawLine(minThreshold, 0.0f, minThreshold, contentHeight, mThresholdPaint);

    Log.i(TAG, "draw stack : {" + 0.0f + ", " + 0.0f + ", " + curOffset + ", " + contentHeight + "}");
    canvas.drawRect(mStackRectF, mStackPaint);

    Log.i(TAG, "draw divider : {" + curOffset + "}");
    canvas.drawLine(curOffset, 0.0f, curOffset, contentHeight, mDividerPaint);
  }
}
