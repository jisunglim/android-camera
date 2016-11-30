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

  /**
   * The id of default drawable resource.
   */
  private static final int DEFAULT_TRASH_BIN_REF = R.drawable.stack_bar_trash_bin;

  /**
   * The default height of stack bar.
   */
  private static final int DEFAULT_BAR_HEIGHT = 15;

  /**
   * The stack which is for storing intervals.
   */
  private Stack<Segment> mSegmentsStack;



  private int mMaximumTime;
  private int mBackgroundColor;
  private int mStackColor;
  private int mDividerColor;
  private Drawable mTrashBin;

  private int mBarHeight;
  private int mButtonHeight;

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

      mBackgroundColor = attrArray.getColor(R.styleable.StackBar_backgroundColor,
          DEFAULT_BACKGROUND_COLOR);

      mStackColor = attrArray.getColor(R.styleable.StackBar_stackColor, DEFAULT_STACK_COLOR);

      mDividerColor = attrArray.getColor(R.styleable.StackBar_dividerColor, DEFAULT_DIVIDER_COLOR);

      mTrashBin = attrArray.getDrawable(R.styleable.StackBar_trashBin);
      if (mTrashBin == null) {
        Log.e(TAG, "mTrashBin is null");
        mTrashBin = getResources().getDrawable(DEFAULT_TRASH_BIN_REF, null);
      }
      mButtonHeight = mTrashBin.getIntrinsicHeight();

      mBarHeight = attrArray.getDimensionPixelSize(R.styleable.StackBar_barHeight,
          (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, DEFAULT_BAR_HEIGHT,
              getResources().getDisplayMetrics()));

    } finally {
      attrArray.recycle();

    }

    initPaint();
  }

  private Paint mBackgroundPaint;

  private Paint mStackPaint;

  private Paint mDividerPaint;

  private void initPaint() {
    mBackgroundPaint = new Paint();
    mBackgroundPaint.setColor(mBackgroundColor);

    mStackPaint = new Paint();
    mStackPaint.setColor(mStackColor);

    mDividerPaint = new Paint();
    mDividerPaint.setColor(mDividerColor);
    mDividerPaint.setStrokeWidth(5.0f);
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), mBarHeight + mButtonHeight);
  }

  @Override
  protected void onDraw(Canvas canvas) {
    int paddingLeft = getPaddingLeft();
    int paddingTop = getPaddingTop();
    int paddingRight = getPaddingRight();
    int paddingBottom = getPaddingBottom();

    int contentWidth = getWidth() - paddingLeft - paddingRight;
    int contentHeight = getHeight() - paddingTop - paddingBottom;

    RectF mBackgroundRectF = new RectF(0.0f, 0.0f, contentWidth, mBarHeight);

    float curOffset = contentWidth/7.0f;
    RectF mStackRectF = new RectF(0.0f, 0.0f, curOffset, mBarHeight);


    Log.i(TAG, "draw background : {" + 0.0f + ", " + 0.0f + ", " + contentWidth + ", " + mBarHeight + "}");
    canvas.drawRect(mBackgroundRectF, mBackgroundPaint);
    Log.i(TAG, "draw stack : {" + 0.0f + ", " + 0.0f + ", " + curOffset + ", " + mBarHeight + "}");
    canvas.drawRect(mStackRectF, mStackPaint);
    Log.i(TAG, "draw divider : {" + curOffset + "}");
    canvas.drawLine(curOffset, 0.0f, curOffset, mBarHeight, mDividerPaint);

    int width = mTrashBin.getIntrinsicWidth();

    Log.i(TAG, "draw trashbin : {" +
        (curOffset - width/2.0) + ", " +
        mBarHeight + ", " +
        (curOffset +  width/2.0) + ", " +
        mBarHeight + mButtonHeight + "}");

    mTrashBin.setBounds((int) (curOffset - width/2.0), mBarHeight,
        (int) (curOffset + width/2.0), mBarHeight + mButtonHeight);

    mTrashBin.draw(canvas);
  }

  /**
   * Segment class
   */
  private class Segment {

    private float mStart;
    private float mEnd;

    Segment(float offset) {
      // Set start with offset value
      mStart = offset;

      // Set end with maximum time
      mEnd = (float) mMaximumTime;

    }

  }
}
