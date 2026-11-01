package com.bofo.finder.ui;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.view.View;

/** v2.0.114: the "BOFO is typing" indicator: three dots that bounce and light up one after the other (replaces the text "● ○ ○"). */
public class TypingDotsDrawable extends Drawable implements Animatable {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float dp;
    private boolean running;
    private long t0;
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!running) return;
            Callback cb = getCallback();
            if (cb instanceof View && !((View) cb).isAttachedToWindow()) { running = false; return; }   // the bubble was removed: stop
            invalidateSelf();
            scheduleSelf(this, SystemClock.uptimeMillis() + 16);
        }
    };

    public TypingDotsDrawable(int color, float density) {
        dp = density;
        p.setColor(color);
        t0 = SystemClock.uptimeMillis();
    }

    @Override public int getIntrinsicWidth() { return Math.round(46 * dp); }
    @Override public int getIntrinsicHeight() { return Math.round(20 * dp); }

    @Override public void draw(Canvas c) {
        Rect b = getBounds();
        long t = SystemClock.uptimeMillis() - t0;
        for (int i = 0; i < 3; i++) {
            double ph = (t % 1200) / 1200.0 - i * 0.15;
            if (ph < 0) ph += 1;
            float v = ph < 0.5 ? (float) Math.sin(ph / 0.5 * Math.PI) : 0f;       // 0..1 while this dot is "up"
            p.setAlpha(Math.round(90 + 165 * v));
            c.drawCircle(b.exactCenterX() + (i - 1) * 13 * dp, b.exactCenterY() - v * 4 * dp, (4f + 1.2f * v) * dp, p);
        }
    }

    @Override public void start() { if (running) return; running = true; t0 = SystemClock.uptimeMillis(); scheduleSelf(tick, t0); }
    @Override public void stop() { running = false; unscheduleSelf(tick); }
    @Override public boolean isRunning() { return running; }
    @Override public void setAlpha(int a) { }
    @Override public void setColorFilter(ColorFilter f) { }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
