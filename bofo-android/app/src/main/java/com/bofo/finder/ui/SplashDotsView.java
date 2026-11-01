package com.bofo.finder.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.PathInterpolator;

/** Three dots (lavender to white) on one smooth sine wave. */
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
        for (int i = 0; i < 3; i++) {                                // v2.0.115: one smooth wave (no pause), each dot a little behind the one before
            float x = (5 + i * 20) * d;
            float e = (float) (Math.sin(now / 900.0 * Math.PI * 2 - i * 0.95) + 1) / 2f;
            p.setColor(blend(0xFFB9A8FF, 0xFFFFFFFF, e));
            p.setAlpha(Math.round((0.35f + 0.65f * e) * 255f));
            c.drawCircle(x, (14f - 7f * e) * d, (4f + 1.8f * e) * d, p);
        }
        if (running) postInvalidateOnAnimation();
    }

    private static int blend(int a, int b, float t) {
        int r = Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t), g = Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t), bl = Math.round((a & 255) * (1 - t) + (b & 255) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }
}
