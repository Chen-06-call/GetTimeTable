package com.local.weekday;

import android.content.Context;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.FrameLayout;

/** A scroll-friendly schedule surface whose content can be resized with a pinch gesture. */
public final class ZoomableScheduleLayout extends FrameLayout {
  private static final float MIN_ZOOM = 0.80f;
  private static final float MAX_ZOOM = 1.60f;

  private final ScaleGestureDetector scaleDetector;
  private View content;
  private OnZoomChangedListener zoomChangedListener;
  private float zoom = 1f;
  private int contentWidth;
  private int contentHeight;

  public ZoomableScheduleLayout(Context context) {
    super(context);
    setClipChildren(false);
    scaleDetector =
        new ScaleGestureDetector(
            context,
            new ScaleGestureDetector.SimpleOnScaleGestureListener() {
              @Override
              public boolean onScaleBegin(ScaleGestureDetector detector) {
                return true;
              }

              @Override
              public boolean onScale(ScaleGestureDetector detector) {
                setZoom(zoom * detector.getScaleFactor());
                return true;
              }
            });
  }

  public void setContent(View view) {
    removeAllViews();
    content = view;
    addView(view, new FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
    requestLayout();
  }

  public float getZoom() {
    return zoom;
  }

  public void setOnZoomChangedListener(OnZoomChangedListener listener) {
    zoomChangedListener = listener;
  }

  public void setZoom(float value) {
    float next = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, value));
    if (Math.abs(next - zoom) < 0.001f) return;
    zoom = next;
    requestLayout();
    invalidate();
    if (zoomChangedListener != null) zoomChangedListener.onZoomChanged(zoom);
  }

  @Override
  public boolean dispatchTouchEvent(MotionEvent event) {
    scaleDetector.onTouchEvent(event);
    return super.dispatchTouchEvent(event);
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    if (content == null) {
      super.onMeasure(widthMeasureSpec, heightMeasureSpec);
      return;
    }
    content.measure(
        MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
        MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
    contentWidth = content.getMeasuredWidth();
    contentHeight = content.getMeasuredHeight();
    setMeasuredDimension(
        Math.max(1, Math.round(contentWidth * zoom)),
        Math.max(1, Math.round(contentHeight * zoom)));
  }

  @Override
  protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
    if (content == null) return;
    content.layout(0, 0, contentWidth, contentHeight);
    content.setPivotX(0f);
    content.setPivotY(0f);
    content.setScaleX(zoom);
    content.setScaleY(zoom);
  }

  public interface OnZoomChangedListener {
    void onZoomChanged(float zoom);
  }
}
