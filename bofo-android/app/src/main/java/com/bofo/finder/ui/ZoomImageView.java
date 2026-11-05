package com.bofo.finder.ui;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

import androidx.appcompat.widget.AppCompatImageView;

/** v2.4.11: an ImageView that fits the picture, then lets the admin pinch to zoom (up to 5x), drag, and double-tap to go back. No library needed. */
public class ZoomImageView extends AppCompatImageView {
    private static final float MAX_ZOOM = 5f;
    private final Matrix m = new Matrix();
    private final RectF rect = new RectF();
    private final ScaleGestureDetector scaler;
    private final GestureDetector taps;
    private float base = 1f, lastX, lastY;

    public ZoomImageView(Context c) { this(c, null); }
    public ZoomImageView(Context c, AttributeSet a) { this(c, a, 0); }
    public ZoomImageView(Context c, AttributeSet a, int s) {
        super(c, a, s);
        setScaleType(ScaleType.MATRIX);
        scaler = new ScaleGestureDetector(c, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScale(ScaleGestureDetector d) {
                float cur = scale();
                float target = Math.max(base, Math.min(base * MAX_ZOOM, cur * d.getScaleFactor()));
                float f = target / cur;
                m.postScale(f, f, d.getFocusX(), d.getFocusY());
                apply();
                return true;
            }
        });
        taps = new GestureDetector(c, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDoubleTap(MotionEvent e) { fit(); return true; }
        });
    }

    @Override public void setImageDrawable(Drawable d) { super.setImageDrawable(d); post(this::fit); }
    @Override protected void onSizeChanged(int w, int h, int ow, int oh) { super.onSizeChanged(w, h, ow, oh); fit(); }

    private float scale() {
        float[] v = new float[9];
        m.getValues(v);
        return v[Matrix.MSCALE_X];
    }

    /** Back to "whole picture visible, centred". */
    void fit() {
        Drawable d = getDrawable();
        int vw = getWidth(), vh = getHeight();
        if (d == null || vw == 0 || vh == 0 || d.getIntrinsicWidth() <= 0 || d.getIntrinsicHeight() <= 0) return;
        float dw = d.getIntrinsicWidth(), dh = d.getIntrinsicHeight();
        base = Math.min(vw / dw, vh / dh);
        m.reset();
        m.postScale(base, base);
        m.postTranslate((vw - dw * base) / 2f, (vh - dh * base) / 2f);
        setImageMatrix(m);
    }

    /** Keeps the picture inside the view (centred when smaller than the view). */
    private void apply() {
        Drawable d = getDrawable();
        if (d == null) return;
        rect.set(0, 0, d.getIntrinsicWidth(), d.getIntrinsicHeight());
        m.mapRect(rect);
        float vw = getWidth(), vh = getHeight(), dx = 0, dy = 0;
        if (rect.width() <= vw) dx = (vw - rect.width()) / 2f - rect.left;
        else if (rect.left > 0) dx = -rect.left;
        else if (rect.right < vw) dx = vw - rect.right;
        if (rect.height() <= vh) dy = (vh - rect.height()) / 2f - rect.top;
        else if (rect.top > 0) dy = -rect.top;
        else if (rect.bottom < vh) dy = vh - rect.bottom;
        m.postTranslate(dx, dy);
        setImageMatrix(m);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        scaler.onTouchEvent(e);
        taps.onTouchEvent(e);
        boolean zoomed = scale() > base * 1.01f;
        if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(zoomed || e.getPointerCount() > 1);
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: lastX = e.getX(); lastY = e.getY(); break;
            case MotionEvent.ACTION_MOVE:
                if (zoomed && !scaler.isInProgress() && e.getPointerCount() == 1) {
                    m.postTranslate(e.getX() - lastX, e.getY() - lastY);
                    apply();
                }
                lastX = e.getX(); lastY = e.getY();
                break;
            case MotionEvent.ACTION_POINTER_UP:
                int i = e.getActionIndex() == 0 ? 1 : 0;      // the remaining finger becomes the drag finger (no jump)
                lastX = e.getX(i); lastY = e.getY(i);
                break;
            case MotionEvent.ACTION_UP: performClick(); break;
            default: break;
        }
        return true;
    }

    @Override public boolean performClick() { return super.performClick(); }
}
