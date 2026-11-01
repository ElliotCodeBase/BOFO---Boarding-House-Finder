package com.bofo.finder.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

/**
 * Port of the JavaFX {@code Mascot.java} (src/main/java/com/bofo/finder/ui/Mascot.java): the SAME shapes on the same 200x200 grid
 * (rounded body, face screen, house-roof hat, feet, arms) and the SAME animations, with the same numbers:
 * the four idle poses THINKING / SITTING / RUNNING / SLEEPING (each its own drawing) and the squash-hop that swaps between poses.
 * Nothing here is an image: every frame is computed from the clock, like the Timelines in JavaFX.
 * Overflow (z's, thought bubble, hop) is drawn outside the view's box, so the parent chain needs clipChildren=false.
 * Colors are the Android mascot colors (design/mascot), not the JavaFX re-theme.
 */
public class BofoMascot extends View {
    /** HAPPY = standing smile, THINKING = bubble, WAVING = ^ ^ eyes + open smile and a small greeting tilt. */
    public enum Expression { HAPPY, THINKING, WAVING }
    public enum Idle { THINKING, SITTING, RUNNING, SLEEPING }

    // v2.0.110: same colours as the splash mascot (white body, blue screen, lavender roof / feet, white face). Names kept so the drawing code is unchanged.
    private static final int PURPLE = 0xFFFFFFFF, DARK = 0xFFC4ABEE, FEET = 0xFFE4D9F7, AMBER = 0xFFD9C8F5, AMBER_DARK = 0xFFC4ABEE,
            TINT = 0xFF5B3BF0, INK = 0xFFFFFFFF, WHITE = 0xFF3A22B8;

    private Expression expr = Expression.HAPPY;
    private Idle pose;                       // null = standing
    private long poseStart;                  // uptime (ms) when the current pose began
    private Runnable pending;                // pose change waiting for the top of the hop (latest wins)
    private long hopStart = -1;
    private boolean hopChanged;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path path = new Path();

    public BofoMascot(Context c) { this(c, null); }
    public BofoMascot(Context c, AttributeSet a) { super(c, a); poseStart = SystemClock.uptimeMillis(); }

    // ------------------------------------------------------------------ public API (same names as the JavaFX class)

    /** Immediate change of face (cancels a hop in progress). */
    public void setExpression(Expression e) {
        pending = null; hopStart = -1;
        pose = null; expr = e; poseStart = SystemClock.uptimeMillis();
        invalidate();
    }

    /** Starts one idle pose: BOFO squashes, hops, changes pose in the air and lands. */
    public void idle(Idle a) {
        swap(() -> {
            pose = a;
            expr = a == Idle.RUNNING ? Expression.WAVING : a == Idle.THINKING ? Expression.THINKING : Expression.HAPPY;
            poseStart = SystemClock.uptimeMillis();
        });
    }

    /** Back to the normal happy face (same hop). Does nothing if nothing is playing. */
    public void endIdle() {
        if (pose == null && hopStart < 0 && pending == null) return;
        swap(() -> { pose = null; expr = Expression.HAPPY; poseStart = SystemClock.uptimeMillis(); });
    }

    private void swap(Runnable change) {
        pending = change;
        if (hopStart >= 0) return;                                   // already hopping: picked up when it reaches the top
        if (!isAttachedToWindow() || !isShown() || !ValueAnimator.areAnimatorsEnabled()) {
            Runnable c = pending; pending = null; hopStart = -1;
            if (c != null) c.run();
            invalidate();
            return;
        }
        hopStart = SystemClock.uptimeMillis(); hopChanged = false;
        invalidate();
    }

    // ------------------------------------------------------------------ JavaFX interpolators

    /** 0 = LINEAR, 1 = EASE_BOTH, 2 = EASE_IN, 3 = EASE_OUT (same curves as javafx.animation.Interpolator). */
    private static double ease(int kind, double t) {
        if (t <= 0) return 0;
        if (t >= 1) return 1;
        if (kind == 0) return t;
        double a = (kind == 1 || kind == 2) ? 0.2 : 0, d = (kind == 1 || kind == 3) ? 0.2 : 0;
        double v = 1 / (1 - a / 2 - d / 2);
        if (t < a) return v * t * t / (2 * a);
        if (t > 1 - d) return 1 - v * (1 - t) * (1 - t) / (2 * d);
        return v * (t - a / 2);
    }

    /** KeyFrames: es[i] is the interpolator of the segment that ENDS at ts[i]. */
    private static double kf(double t, double[] ts, double[] vs, int[] es) {
        if (t <= ts[0]) return vs[0];
        for (int i = 1; i < ts.length; i++) {
            if (t <= ts[i]) {
                double f = (t - ts[i - 1]) / (ts[i] - ts[i - 1]);
                return vs[i - 1] + (vs[i] - vs[i - 1]) * ease(es[i], f);
            }
        }
        return vs[vs.length - 1];
    }

    /** Timeline with autoReverse + INDEFINITE: a -> b over {@code half} ms, then back. */
    private static double pingpong(double t, double half, double a, double b, int e) {
        double ph = (t % (2 * half)) / half;
        if (ph > 1) ph = 2 - ph;
        return a + (b - a) * ease(e, ph);
    }

    // ------------------------------------------------------------------ drawing

    private void rr(Canvas c, float x, float y, float w, float h, float r, int color, float alpha) {
        paint.setStyle(Paint.Style.FILL); paint.setColor(color); paint.setAlpha(Math.round(255 * alpha));
        rect.set(x, y, x + w, y + h);
        c.drawRoundRect(rect, r, r, paint);
    }
    private void rr(Canvas c, float x, float y, float w, float h, float r, int color) { rr(c, x, y, w, h, r, color, 1f); }

    private void dot(Canvas c, float cx, float cy, float r, int color, float alpha) {
        paint.setStyle(Paint.Style.FILL); paint.setColor(color); paint.setAlpha(Math.round(255 * alpha));
        c.drawCircle(cx, cy, r, paint);
    }

    private void arc(Canvas c, float x1, float y1, float cx, float cy, float x2, float y2, int color, float w) {
        path.reset(); path.moveTo(x1, y1); path.quadTo(cx, cy, x2, y2);
        paint.setStyle(Paint.Style.STROKE); paint.setColor(color); paint.setAlpha(255);
        paint.setStrokeWidth(w); paint.setStrokeCap(Paint.Cap.ROUND); paint.setStrokeJoin(Paint.Join.ROUND);
        c.drawPath(path, paint);
    }

    /** One arm: a rounded bar hanging from the shoulder (sx, sy), swung by {@code deg} around that point. */
    private void arm(Canvas c, float sx, float sy, float len, float deg) {
        c.save();
        c.rotate(deg, sx, sy);
        rr(c, sx - 7, sy - 7, 14, len, 7, DARK);
        c.restore();
    }

    @Override
    protected void onDraw(Canvas c) {
        final float side = Math.min(getWidth(), getHeight());
        if (side <= 0) return;
        final long now = SystemClock.uptimeMillis();

        // ---- hop between poses (JavaFX swap()) ----
        float poseX = 1, poseY = 1, hopDy = 0;
        boolean again = false;
        if (hopStart >= 0) {
            double h = now - hopStart, up = -side * 0.22;
            if (h < 220) {
                poseX = (float) kf(h, new double[]{0, 90, 220}, new double[]{1, 1.12, 0.93}, new int[]{0, 1, 3});
                poseY = (float) kf(h, new double[]{0, 90, 220}, new double[]{1, 0.80, 1.10}, new int[]{0, 1, 3});
                hopDy = (float) kf(h, new double[]{0, 90, 220}, new double[]{0, 0, up}, new int[]{0, 1, 3});
            } else {
                if (!hopChanged) { hopChanged = true; Runnable ch = pending; pending = null; if (ch != null) ch.run(); }
                double l = h - 220;
                if (l >= 380) { hopStart = -1; again = pending != null; }
                else {
                    poseX = (float) kf(l, new double[]{0, 150, 210, 380}, new double[]{0.93, 0.97, 1.14, 1}, new int[]{0, 2, 3, 3});
                    poseY = (float) kf(l, new double[]{0, 150, 210, 380}, new double[]{1.10, 1.05, 0.80, 1}, new int[]{0, 2, 3, 3});
                    hopDy = (float) kf(l, new double[]{0, 150}, new double[]{up, 0}, new int[]{0, 2});
                }
            }
        }
        final double pt = now - poseStart;
        final boolean low = pose == Idle.SITTING || pose == Idle.SLEEPING;

        // ---- pose parameters (all in 200-grid units, degrees) ----
        float tilt = 0, torsoDy = 0, torsoRot = 0, hatRot = 0, rotL = 0, rotR = 0;
        float footLx = 0, footLy = 0, footRx = 0, footRy = 0, eyeX = 0, eyeY = 0, eyeSy = 1, mouthSx = 1, mouthSy = 1;
        float thoughtA = 1, thoughtDy = 0, handDy = 0;
        if (pose == null) {
            if (expr == Expression.WAVING) tilt = (float) pingpong(pt, 450, -5, 5, 1);
        } else switch (pose) {
            case THINKING: {
                tilt = (float) pingpong(pt, 1800, -5, 6, 1);
                double m = pt % 3000;
                double[] ts = {0, 700, 1700, 2500, 3000};
                int[] es = {1, 1, 1, 1, 1};
                eyeX = (float) kf(m, ts, new double[]{0, -7, 8, 0, 0}, es);
                eyeY = (float) kf(m, ts, new double[]{0, -8, -8, 0, 0}, es);
                thoughtA = (float) pingpong(pt, 900, 1.0, 0.35, 1);
                thoughtDy = (float) pingpong(pt, 900, 0, -6, 1);
                handDy = (float) pingpong(pt, 380, 0, -4, 1);
                footRy = (float) pingpong(pt, 520, 0, -5, 1);
                break;
            }
            case SITTING: {
                torsoDy = pt < 420
                        ? (float) kf(pt, new double[]{0, 260, 340, 420}, new double[]{-34, 0, -5, 0}, new int[]{0, 2, 3, 2})   // plops down
                        : (float) pingpong(pt - 420, 1500, 0, -2, 1);                                                        // breathes
                float ph = (float) pingpong(pt, 650, 0, 1, 1);
                footLy = -4 * ph; footRy = -4 * (1 - ph);                                                                   // feet swing
                eyeSy = (float) kf(pt % 3400, new double[]{0, 2600, 2700, 2850, 3400}, new double[]{1, 1, 0.1, 1, 1}, new int[]{0, 0, 0, 0, 0});
                eyeX = (float) kf(pt % 4000, new double[]{0, 1200, 1700, 2700, 3300, 4000}, new double[]{0, 0, -6, 6, 0, 0}, new int[]{1, 1, 1, 1, 1, 1});
                rotL = 16; rotR = -16;
                break;
            }
            case RUNNING: {
                torsoRot = 11;
                double a = (pt % 520) / 520 * Math.PI * 2, s = Math.sin(a), co = Math.cos(a);
                footLx = (float) (12 * s);  footLy = (float) (-9 * Math.max(0, co));
                footRx = (float) (-12 * s); footRy = (float) (-9 * Math.max(0, -co));
                rotL = (float) (55 * s); rotR = (float) (-55 * s);
                torsoDy = (float) (-4 * Math.abs(s));
                break;
            }
            default: {                                                            // SLEEPING
                torsoRot = (float) pingpong(pt, 2400, 3, 15, 1);
                hatRot = (float) pingpong(pt, 2400, 0, 6, 1);
                torsoDy = (float) pingpong(pt, 1800, 0, -2, 1);
                mouthSx = (float) pingpong(pt, 1800, 0.8, 1.3, 1);
                mouthSy = (float) pingpong(pt, 1800, 0.8, 1.5, 1);
                rotL = 6; rotR = -6;
            }
        }

        // ---- canvas: node rotate, squash about the feet, hop, then the 200 grid ----
        final float k = side / 200f, ox = (getWidth() - side) / 2f, oy = (getHeight() - side) / 2f;
        c.save();
        c.translate(ox, oy);
        c.rotate(tilt, side / 2f, side / 2f);
        c.scale(poseX, poseY, side / 2f, side * 0.86f);
        c.translate(0, hopDy);
        c.scale(k, k);

        if (!low) {                                                               // standing feet are under the body
            c.save(); c.translate(footLx, footLy); rr(c, 60, 156, 30, 16, 8, FEET); c.restore();
            c.save(); c.translate(footRx, footRy); rr(c, 110, 156, 30, 16, 8, FEET); c.restore();
        }

        // torso: body, face screen, glass highlight, status light, hat, face, arms
        c.save();
        c.translate(0, (low ? 12 : 0) + torsoDy);
        c.rotate(torsoRot, 100, 162);
        rr(c, 40, 50, 120, 112, 32, PURPLE);
        rr(c, 56, 70, 88, 68, 22, WHITE);
        rr(c, 66, 77, 36, 5, 2.5f, TINT);
        rr(c, 86, 146, 28, 6, 3, AMBER);
        c.save();                                                                 // hat: chimney + roof + brim
        c.rotate(hatRot, 100, 35.5f);
        rr(c, 119, 18, 13, 24, 4, AMBER_DARK);
        path.reset(); path.moveTo(57, 48); path.lineTo(100, 16); path.lineTo(143, 48); path.close();
        paint.setStyle(Paint.Style.FILL_AND_STROKE); paint.setColor(AMBER); paint.setAlpha(255);
        paint.setStrokeWidth(8); paint.setStrokeJoin(Paint.Join.ROUND);
        c.drawPath(path, paint);
        rr(c, 38, 45, 124, 14, 7, AMBER_DARK);
        c.restore();

        if (pose == Idle.SLEEPING) {
            arc(c, 72, 106, 83, 118, 94, 106, INK, 5);                            // shut eyes
            arc(c, 106, 106, 117, 118, 128, 106, INK, 5);
            c.save(); c.scale(mouthSx, mouthSy, 100, 127.5f); rr(c, 94, 124, 12, 7, 3.5f, INK); c.restore();
        } else if (expr == Expression.THINKING) {
            c.save(); c.translate(eyeX, eyeY);
            rr(c, 80, 90, 14, 18, 7, INK); rr(c, 110, 90, 14, 18, 7, INK);
            c.restore();
            rr(c, 88, 119, 24, 5, 2.5f, INK);
        } else if (expr == Expression.WAVING) {
            arc(c, 72, 104, 81, 88, 90, 104, INK, 5);
            arc(c, 110, 104, 119, 88, 128, 104, INK, 5);
            path.reset(); path.moveTo(84, 113); path.quadTo(100, 136, 116, 113);
            paint.setStyle(Paint.Style.FILL_AND_STROKE); paint.setColor(INK); paint.setAlpha(255);
            paint.setStrokeWidth(4); paint.setStrokeJoin(Paint.Join.ROUND);
            c.drawPath(path, paint);                                              // open smile
        } else {
            c.save(); c.translate(eyeX, eyeY);
            c.save(); c.scale(1, eyeSy, 83, 103); rr(c, 76, 92, 14, 22, 7, INK); c.restore();
            c.save(); c.scale(1, eyeSy, 117, 103); rr(c, 110, 92, 14, 22, 7, INK); c.restore();
            c.restore();
            arc(c, 86, 122, 100, 134, 114, 122, INK, 5);
        }

        if (pose == Idle.RUNNING) { arm(c, 40, 112, 38, rotL); arm(c, 160, 112, 38, rotR); }
        else if (low) { arm(c, 40, 112, 40, rotL); arm(c, 160, 112, 40, rotR); }
        c.restore();

        if (low) {                                                                // sitting feet are in FRONT of the body
            c.save(); c.translate(footLx, footLy); rr(c, 18, 164, 46, 20, 10, FEET); c.restore();
            c.save(); c.translate(footRx, footRy); rr(c, 136, 164, 46, 20, 10, FEET); c.restore();
        }

        if (pose == Idle.THINKING) {
            arm(c, 36, 114, 38, 8);                                               // spare arm hangs down
            c.save(); c.translate(0, handDy);                                     // other arm: hand under the chin, fingers drum
            path.reset(); path.moveTo(160, 130); path.lineTo(138, 138);
            paint.setStyle(Paint.Style.STROKE); paint.setColor(DARK); paint.setAlpha(255);
            paint.setStrokeWidth(14); paint.setStrokeCap(Paint.Cap.ROUND); paint.setStrokeJoin(Paint.Join.ROUND);
            c.drawPath(path, paint);
            dot(c, 132, 138, 9, AMBER, 1f);
            c.restore();
        }
        if (expr == Expression.THINKING) {                                        // thought bubble
            c.save(); c.translate(0, thoughtDy);
            dot(c, 168, 40, 5, AMBER, thoughtA); dot(c, 180, 26, 7, AMBER, thoughtA); rr(c, 166, 2, 30, 20, 10, AMBER, thoughtA);
            c.restore();
        }

        // ---- effects layer: speed lines and z's ----
        if (pose == Idle.RUNNING) {
            for (int i = 0; i < 3; i++) {
                double t = pt - 130L * i;
                if (t < 0) continue;
                double f = (t % 420) / 420;
                rr(c, 2 - (float) (14 * f), 92 + i * 20, 30 - i * 4, 5, 2.5f, AMBER, (float) (0.9 * (1 - f)));
            }
        } else if (pose == Idle.SLEEPING) {
            final float[] sz = {44, 54, 64};
            paint.setTypeface(Typeface.DEFAULT_BOLD);
            for (int i = 0; i < 3; i++) {
                double t = pt - 800L * i;
                if (t < 0) continue;
                double m = t % 2400;
                float a = (float) kf(m, new double[]{0, 500, 2400}, new double[]{0, 1, 0}, new int[]{0, 0, 0});
                float dy = (float) (-44 * m / 2400), dx = (float) (16 * m / 2400);
                paint.setStyle(Paint.Style.FILL); paint.setColor(DARK); paint.setAlpha(Math.round(255 * a));
                paint.setTextSize(sz[i]);
                c.drawText("z", 150 + dx, 48 + dy, paint);
            }
        }
        c.restore();

        if (again) { Runnable n = pending; pending = null; if (n != null) swap(n); }
        if (pose != null || hopStart >= 0 || expr == Expression.WAVING) postInvalidateOnAnimation();
    }
}
