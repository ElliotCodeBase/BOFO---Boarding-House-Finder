package com.bofo.finder.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PointF;
import android.util.AttributeSet;
import android.view.animation.LinearInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The radar ripples and the "found you" ring of the live dot, drawn with a plain Canvas ON TOP of the map.
 * <p>
 * Why: every change to a map layer (the old ripple layers did one per frame, forever) makes MapLibre re-render the
 * whole map. Here the map stays idle (zero GPU work) and only this small dirty rectangle repaints. The dot itself stays
 * a map layer, so it is always exactly on its spot; the ripples are translucent and hidden while the camera moves,
 * then snapped back by {@link MapCardView} when the map goes idle.
 * <p>
 * Sizes copy the old layers: a style pixel is {@code unit} physical pixels (the map's pixel ratio).
 * Never takes touches.
 */
public class RadarOverlayView extends android.view.View {
    private static final long RIPPLE_MS = 2400;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float unit;
    private ValueAnimator anim;
    private float t = 0.35f, pop = 1f, found = -1f;
    private float cx, cy;
    private boolean has, moving, running;

    public RadarOverlayView(@NonNull Context c, float unit) {
        super(c);
        this.unit = unit;
        fill.setColor(0xFF2F6FED);
        fill.setStyle(Paint.Style.STROKE);                 // a ripple is drawn as a ring (disc minus the dot): nothing paints over the dot
        stroke.setColor(0xFF2F6FED);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(3f * unit);
        setClickable(false);
        setFocusable(false);
        setWillNotDraw(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    public RadarOverlayView(@NonNull Context c, @Nullable AttributeSet a) { this(c, 1f); }

    /** Screen position of the dot inside this view (null = no dot). */
    public void setCenter(@Nullable PointF p) {
        boolean was = has;
        float ox = cx, oy = cy;
        has = p != null;
        if (p != null) { cx = p.x; cy = p.y; }
        if (was != has || ox != cx || oy != cy) invalidate();
    }

    /** true while the camera moves: nothing is drawn (the dot layer keeps up on its own). */
    public void setMoving(boolean m) { if (moving != m) { moving = m; invalidate(); } }

    /** 0..1 first-fix pop (ripples grow in with it). */
    public void setPop(float v) { pop = v; }

    /** 0..1 progress of the one-off "found you" ring; negative = off. */
    public void setFound(float v) { found = v; if (!running) invalidate(); }

    /** animate = false: one still frame instead of the loop (system animations are off). */
    public void start(boolean animate) {
        if (!animate) { stop(); t = 0.35f; invalidate(); return; }
        if (anim != null) return;
        running = true;
        anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(RIPPLE_MS);
        anim.setInterpolator(new LinearInterpolator());
        anim.setRepeatCount(ValueAnimator.INFINITE);
        anim.addUpdateListener(a -> {
            t = (float) a.getAnimatedValue();
            if (has && !moving && isShown()) invalidate();     // hidden / scrolled away / camera moving: no work at all
        });
        anim.start();
    }

    public void stop() {
        running = false;
        if (anim != null) { anim.cancel(); anim = null; }
    }

    @Override protected void onDetachedFromWindow() { stop(); super.onDetachedFromWindow(); }

    @Override
    protected void onDraw(@NonNull Canvas c) {
        if (!has || moving) return;
        float s = (0.82f + 0.18f * pop) * (found >= 0 ? 1f + 0.25f * (float) Math.sin(Math.PI * found) : 1f);
        float inner = 13f * s * unit;                           // the dot's shadow radius: ripples start outside it
        ripple(c, t, s, inner);
        ripple(c, (t + 0.5f) % 1f, s, inner);
        if (found >= 0f) {
            stroke.setAlpha(Math.round(255f * 0.85f * (1f - found)));
            c.drawCircle(cx, cy, 10f * (1f + 3.5f * found) * unit, stroke);
        }
    }

    private void ripple(Canvas c, float p, float s, float inner) {
        float r = 10f * (0.5f + 2.2f * p) * s * unit;           // 0.5 -> 2.7 x, fading linearly (same numbers as the old layers)
        float a = 0.34f * (1f - p) * pop;
        if (r <= inner || a <= 0f) return;
        fill.setAlpha(Math.round(255f * a));
        fill.setStrokeWidth(r - inner);
        c.drawCircle(cx, cy, (r + inner) / 2f, fill);
    }
}
