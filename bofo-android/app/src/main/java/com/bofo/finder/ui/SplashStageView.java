package com.bofo.finder.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.Interpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.PathInterpolator;

import androidx.core.content.ContextCompat;

import com.bofo.finder.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Loading screen mascot (port of the JavaFX SplashView + white Mascot, v2.0.20).
 * A WHITE BOFO jumps twice per 2400 ms loop: crouch, stretch, airtime, landing squash (pivot on the soles).
 * Near the top the hat POPS off with a spark burst, floats, falls back and pops on with a squash, a rebound and a second burst.
 * v2.0.113: lifelike extras: lean into the jump, feet tuck in the air, eyes look where it jumps and blink on impact, the smile grows in the air, dust puffs.
 * A soft purple ground shadow shrinks while it is in the air. Drawn on a 200 x 200 art grid; only transform and alpha change.
 * Time comes from the clock (not ValueAnimator), so it still plays when the system animation scale is 0. Stops when detached.
 */
public class SplashStageView extends View {
    private static final float CYCLE = 2400f;
    private static final float[] ANG = {-170, -145, -118, -90, -62, -35, -10};

    private static final Interpolator LIN = new LinearInterpolator();
    private static final Interpolator BOTH = new PathInterpolator(0.42f, 0f, 0.58f, 1f);
    private static final Interpolator OUT = new PathInterpolator(0.2f, 0.7f, 0.3f, 1f);
    private static final Interpolator IN = new PathInterpolator(0.7f, 0f, 0.8f, 0.3f);
    private static final Interpolator POP = new PathInterpolator(0.15f, 0.95f, 0.35f, 1f);

    /** Key frames of one value; the interpolator of a frame shapes the segment that ENDS at that frame. */
    private static final class Track {
        final List<float[]> k = new ArrayList<>();
        final List<Interpolator> e = new ArrayList<>();
        Track add(float t, float v, Interpolator ip) { k.add(new float[]{t, v}); e.add(ip == null ? LIN : ip); return this; }
        float at(float t) {
            int n = k.size();
            if (n == 0) return 0f;
            if (t <= k.get(0)[0]) return k.get(0)[1];
            for (int i = 1; i < n; i++) {
                float[] b = k.get(i);
                if (t <= b[0]) {
                    float[] a = k.get(i - 1);
                    float span = b[0] - a[0];
                    float f = span <= 0f ? 1f : (t - a[0]) / span;
                    return a[1] + (b[1] - a[1]) * e.get(i).getInterpolation(f);
                }
            }
            return k.get(n - 1)[1];
        }
    }

    private final Track bodyY = new Track(), sqX = new Track(), sqY = new Track(), shScale = new Track(), shAlpha = new Track();
    private final Track hatY = new Track(), hatAng = new Track(), hatSx = new Track(), hatSy = new Track();
    // v2.0.113 life: lean (deg), feet lift (art units), eye gaze / blink, smile depth
    private final Track lean = new Track(), feetUp = new Track(), eyeX = new Track(), eyeY = new Track(), eyeSy = new Track(), smile = new Track();
    private final List<float[]> bursts = new ArrayList<>();      // {start, duration}
    private final List<float[]> dust = new ArrayList<>();        // {start, duration, strength}

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final Path path = new Path();
    private final float density;
    // v2.0.117: every colour comes from colors.xml (mascot_* and splash_*), so the classic and orange themes both work
    private final int cBody, cOutline, cScreen, cGlass, cFace, cRoof, cRoofDark, cFeet, cLight, cShadow, cDust, cSpark;
    private long startMs;
    private boolean running;

    public SplashStageView(Context c) { this(c, null); }

    public SplashStageView(Context c, AttributeSet a) {
        super(c, a);
        density = c.getResources().getDisplayMetrics().density;
        cBody = ContextCompat.getColor(c, R.color.mascot_body);
        cOutline = ContextCompat.getColor(c, R.color.splash_mascot_outline);   // transparent in classic (no outline), a soft orange ring on the white screen
        cScreen = ContextCompat.getColor(c, R.color.mascot_screen);
        cGlass = ContextCompat.getColor(c, R.color.mascot_glass);
        cFace = ContextCompat.getColor(c, R.color.mascot_face);
        cRoof = ContextCompat.getColor(c, R.color.mascot_roof);
        cRoofDark = ContextCompat.getColor(c, R.color.mascot_roof_dark);
        cFeet = ContextCompat.getColor(c, R.color.mascot_feet);
        cLight = ContextCompat.getColor(c, R.color.mascot_light);
        cShadow = ContextCompat.getColor(c, R.color.splash_shadow);
        cDust = ContextCompat.getColor(c, R.color.splash_dust);
        cSpark = ContextCompat.getColor(c, R.color.splash_spark);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeCap(Paint.Cap.ROUND);
        line.setStrokeJoin(Paint.Join.ROUND);
        addJump(0, -44f, -28f, -9f, 0.70f);
        addJump(1100, -58f, -34f, 9f, 0.60f);
        // recover from the landing squash, then crouch again so the loop restarts in the pose jump 1 begins with
        sqX.add(2250, 1f, LIN).add(CYCLE, 1.10f, LIN);
        sqY.add(2250, 1f, LIN).add(CYCLE, 0.90f, LIN);
        shScale.add(2250, 1f, LIN).add(CYCLE, 1.05f, LIN);
        hat(CYCLE, 0, 0, 1, 1, null);
    }

    private void addJump(float t0, float height, float hatUp, float tilt, float shadowMin) {
        bodyY.add(t0, 0, LIN).add(t0 + 180, 0, LIN).add(t0 + 560, height, OUT).add(t0 + 950, 0, IN);
        sqX.add(t0, 1.10f, LIN).add(t0 + 180, 0.94f, LIN).add(t0 + 560, 1f, OUT).add(t0 + 950, 1.10f, IN);
        sqY.add(t0, 0.90f, LIN).add(t0 + 180, 1.07f, LIN).add(t0 + 560, 1f, OUT).add(t0 + 950, 0.90f, IN);
        shScale.add(t0, 1.05f, LIN).add(t0 + 560, shadowMin, OUT).add(t0 + 950, 1.05f, IN);
        shAlpha.add(t0, 1f, LIN).add(t0 + 560, 0.55f, OUT).add(t0 + 950, 1f, IN);
        //   time        y          angle        sx    sy   ease
        hat(t0,        0,          0,          1.00f, 1.00f, null);
        hat(t0 + 420,  2,          0,          1.04f, 0.94f, null);   // lag: pressed down while the body shoots up
        hat(t0 + 560,  0,          0,          1.00f, 1.00f, null);   // apex
        hat(t0 + 610,  3,          0,          1.08f, 0.88f, null);   // wind-up
        hat(t0 + 680,  hatUp - 5,  tilt * 1.2f, 0.86f, 1.24f, POP);   // POP off
        hat(t0 + 780,  hatUp + 2,  tilt * 0.5f, 1.00f, 1.00f, OUT);   // hover
        hat(t0 + 900, -3,          0,          0.96f, 1.08f, IN);     // falls back
        hat(t0 + 950,  3,          0,          1.18f, 0.80f, IN);     // lands on the head
        hat(t0 + 1015, -4,         0,          0.94f, 1.10f, OUT);    // rebound
        hat(t0 + 1070, 0,          0,          1.00f, 1.00f, OUT);    // settle
        float lk = tilt * 0.35f;                                        // lean follows the jump direction: back on the crouch, forward in the air, a nudge on landing
        lean.add(t0, 0, LIN).add(t0 + 180, -lk * 0.5f, BOTH).add(t0 + 560, lk, OUT).add(t0 + 950, -lk * 0.4f, IN).add(t0 + 1050, 0, OUT);
        feetUp.add(t0, 0, LIN).add(t0 + 180, 0, LIN).add(t0 + 400, -2f, OUT).add(t0 + 560, -7f, OUT).add(t0 + 800, -5f, BOTH).add(t0 + 950, 0, IN);   // feet tuck up in the air
        eyeX.add(t0, 0, LIN).add(t0 + 180, 0, LIN).add(t0 + 560, tilt / 3f, OUT).add(t0 + 950, 0, IN);                                                 // looks where it is jumping
        eyeY.add(t0, 0, LIN).add(t0 + 180, 2f, BOTH).add(t0 + 560, -4f, OUT).add(t0 + 950, 1f, IN).add(t0 + 1050, 0, OUT);                               // down on the crouch, up at the apex
        eyeSy.add(t0, 1f, LIN).add(t0 + 180, 0.85f, BOTH).add(t0 + 300, 1f, OUT).add(t0 + 940, 1f, LIN).add(t0 + 985, 0.12f, LIN).add(t0 + 1040, 1f, OUT);  // squint, then a blink on impact
        smile.add(t0, 134f, LIN).add(t0 + 180, 130f, BOTH).add(t0 + 560, 141f, OUT).add(t0 + 950, 136f, IN).add(t0 + 1040, 134f, OUT);                  // grin grows in the air
        dust.add(new float[]{t0 + 190, 300, 0.6f});                    // take-off puff
        dust.add(new float[]{t0 + 940, 420, 1f});                      // landing puff
        bursts.add(new float[]{t0 + 670, 270});                        // pop off
        bursts.add(new float[]{t0 + 950, 240});                        // pop on
    }

    private void hat(float t, float y, float ang, float sx, float sy, Interpolator ip) {
        Interpolator i = ip == null ? BOTH : ip;
        hatY.add(t, y, i); hatAng.add(t, ang, i); hatSx.add(t, sx, i); hatSy.add(t, sy, i);
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        running = true; startMs = SystemClock.uptimeMillis(); postInvalidateOnAnimation();
    }

    @Override protected void onDetachedFromWindow() { running = false; super.onDetachedFromWindow(); }

    @Override protected void onDraw(Canvas c) {
        float t = (SystemClock.uptimeMillis() - startMs) % (long) CYCLE;
        float d = density, w = getWidth(), h = getHeight();
        float s = 172f * d / 200f;                       // art unit -> px (mascot is 172 dp wide)
        float feetY = h - 12f * d;                       // ground line
        float ox = (w - 172f * d) / 2f, oy = feetY - 172f * s;

        // ground shadow (light purple: a black one is invisible on the dark background)
        float sc = shScale.at(t), sa = shAlpha.at(t);
        c.save();
        c.scale(sc, sc, w / 2f, h - 14f * d);
        fill.setColor(tint(0.32f * sa, cShadow));
        r.set(w / 2f - 54f * d, h - 22f * d, w / 2f + 54f * d, h - 6f * d);
        c.drawOval(r, fill);
        c.restore();

        // dust puffs at the feet on take-off and landing (stay on the ground, fade out)
        c.save();
        c.translate(ox, oy);
        c.scale(s, s);
        fill.setStyle(Paint.Style.FILL);
        for (float[] dp : dust) {
            if (t < dp[0] || t > dp[0] + dp[1]) continue;
            float e = OUT.getInterpolation((t - dp[0]) / dp[1]), k = dp[2];
            fill.setColor(tint(0.5f * k * (1f - e), cDust));
            for (int sd = -1; sd <= 1; sd += 2) {
                c.drawCircle(100f + sd * (34f + 30f * e * k), 168f - 6f * e, (6f + 5f * e) * k, fill);
                c.drawCircle(100f + sd * (22f + 18f * e * k), 170f - 2f * e, (4f + 3f * e) * k, fill);
            }
        }
        c.restore();

        // body: jump offset, then squash / stretch around the soles
        c.save();
        c.translate(ox, oy + bodyY.at(t) * d);
        c.scale(s, s);
        c.scale(sqX.at(t), sqY.at(t), 100f, 172f);
        c.rotate(lean.at(t), 100f, 172f);

        float fu = feetUp.at(t), sp = -fu * 0.7f;
        rr(c, 60 - sp, 156 + fu, 30, 16, 8, cFeet);  // feet (tuck up and spread a little in the air)
        rr(c, 110 + sp, 156 + fu, 30, 16, 8, cFeet);
        if (Color.alpha(cOutline) > 0) rr(c, 37.5f, 47.5f, 125, 117, 34.5f, cOutline);   // outline ring so the white body shows on the white (orange) screen
        rr(c, 40, 50, 120, 112, 32, cBody);              // body (white variant)
        rr(c, 56, 70, 88, 68, 22, cScreen);           // face screen
        rr(c, 66, 77, 36, 5, 2.5f, cGlass);          // glass highlight
        rr(c, 86, 146, 28, 6, 3, cLight);            // status light

        // hat: its own transform (lift, tilt about the brim centre, squash about the brim underside)
        c.save();
        c.translate(0, hatY.at(t));
        c.rotate(hatAng.at(t), 100f, 52f);
        c.scale(hatSx.at(t), hatSy.at(t), 100f, 59f);
        rr(c, 119, 18, 13, 24, 4, cRoofDark);           // chimney
        path.reset(); path.moveTo(57, 48); path.lineTo(100, 16); path.lineTo(143, 48); path.close();
        fill.setColor(cRoof); fill.setStyle(Paint.Style.FILL); c.drawPath(path, fill);
        line.setColor(cRoof); line.setStrokeWidth(8f); c.drawPath(path, line);   // roof (rounded corners)
        rr(c, 38, 45, 124, 14, 7, cRoofDark);           // brim
        c.restore();

        // eyes and smile
        float es = eyeSy.at(t);
        c.save(); c.translate(eyeX.at(t), eyeY.at(t));
        c.save(); c.scale(1f, es, 83f, 103f); rr(c, 76, 92, 14, 22, 7, cFace); c.restore();
        c.save(); c.scale(1f, es, 117f, 103f); rr(c, 110, 92, 14, 22, 7, cFace); c.restore();
        c.restore();
        path.reset(); path.moveTo(86, 122); path.quadTo(100, smile.at(t), 114, 122);
        line.setColor(cFace); line.setStrokeWidth(5f); c.drawPath(path, line);

        // pop bursts (ring + sparks), above everything, follow the jump
        for (float[] b : bursts) {
            if (t < b[0] || t > b[0] + b[1]) continue;
            float e = OUT.getInterpolation((t - b[0]) / b[1]);
            float rs = 0.4f + 2.4f * e;
            line.setColor(tint(0.9f * (1f - e), cSpark)); line.setStrokeWidth(4f * rs);
            c.drawCircle(100f, 48f, 16f * rs, line);
            fill.setStyle(Paint.Style.FILL);
            for (int i = 0; i < ANG.length; i++) {
                double a = Math.toRadians(ANG[i]);
                float dist = (i % 2 == 0 ? 56f : 42f) * e, scale = 1f - 0.7f * e;
                fill.setColor(tint(1f - e, i % 2 == 0 ? cSpark : cDust));
                c.drawCircle(100f + (float) Math.cos(a) * dist, 48f + (float) Math.sin(a) * dist, (i % 2 == 0 ? 5f : 3.5f) * scale, fill);
            }
        }
        c.restore();
        if (running) postInvalidateOnAnimation();
    }

    private void rr(Canvas c, float x, float y, float w, float h, float rad, int color) {
        fill.setStyle(Paint.Style.FILL); fill.setColor(color);
        r.set(x, y, x + w, y + h);
        c.drawRoundRect(r, rad, rad, fill);
    }

    /** The colour with its own alpha scaled by a (0..1): the animation keeps its alpha curves, the colour comes from the theme. */
    private static int tint(float a, int base) {
        return argb(a * Color.alpha(base) / 255f, Color.red(base), Color.green(base), Color.blue(base));
    }

    private static int argb(float a, int r, int g, int b) {
        return ((Math.max(0, Math.min(255, Math.round(a * 255f)))) << 24) | (r << 16) | (g << 8) | b;
    }
}
