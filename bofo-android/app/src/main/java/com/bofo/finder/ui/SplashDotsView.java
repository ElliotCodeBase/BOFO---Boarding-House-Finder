package com.bofo.finder.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.PathInterpolator;

/** Three white dots that fade up and rise one after another (original splash: 450 ms, 160 ms apart, ease in-out, ping-pong). */
public class SplashDotsView extends View {
    private static final PathInterpolator BOTH = new PathInterpolator(0.42f, 0f, 0.58f, 1f);
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float d;
    private long startMs;
    private boolean running;

    public SplashDotsView(Context c) { this(c, null); }
    public SplashDotsView(Context c, AttributeSet a) {
        super(c, a);
        d = c.getResources().getDisplayMetrics().density;
        p.setColor(0xFFFFFFFF);
    }

    @Override protected void onMeasure(int wSpec, int hSpec) { setMeasuredDimension(Math.round(50 * d), Math.round(24 * d)); }

    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); running = true; startMs = SystemClock.uptimeMillis(); postInvalidateOnAnimation(); }
    @Override protected void onDetachedFromWindow() { running = false; super.onDetachedFromWindow(); }

    @Override protected void onDraw(Canvas c) {
        long now = SystemClock.uptimeMillis() - startMs;
        for (int i = 0; i < 3; i++) {
            float x = (5 + i * 20) * d;
            float phase = Math.max(0, now - i * 160L) / 450f;      // 0..1 up, 1..2 down, ...
            float tri = phase % 2f;
            if (tri > 1f) tri = 2f - tri;
            float e = BOTH.getInterpolation(tri);
            p.setAlpha(Math.round((0.3f + 0.7f * e) * 255f));
            float y = (12f + 2f - 7f * e) * d;                       // translateY 2 -> -5
            c.drawCircle(x, y, 5f * d, p);
        }
        if (running) postInvalidateOnAnimation();
    }
}
