package com.bofo.finder.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.Keyframe;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.Property;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.bofo.finder.R;

import java.util.Random;

/**
 * Floating "Ask BOFO" button (original: BofoPanel.java). Drag it anywhere, it snaps to the nearest side edge on release.
 * It always floats (soft bob + tilt) and, after a quiet spell, plays a random idle move (wiggle, hop, look around, flip, loop-the-loop, think, sleep),
 * never the same one twice in a row. Idle stops on touch, while the chat is open, when hidden / in the background, or when system animations are off.
 * Tap = performClick (the shell opens the chat overlay). Transform and alpha animators only.
 */
public class FloatingBofoView extends FrameLayout {
    private enum Idle { WIGGLE, HOP, SWAY, FLIP, DASH, THINK, SLEEP }

    /** Remembered between screens of the same sign-in (the shell is rebuilt when you come back from a detail screen). */
    private static float savedTx, savedTy;
    private static boolean savedMoved;
    public static void forgetPosition() { savedMoved = false; savedTx = 0f; savedTy = 0f; }

    private final FrameLayout bobber;           // white circle + mascot: this is what floats and plays the idle moves
    private final TextView zzz;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random rnd = new Random();
    private final float dp;
    private final int slop, edge;
    private int boundTop, boundBottom;          // px kept free above the view / between the view and the bottom of the parent
    private ObjectAnimator bob, tilt;
    private Animator idleAnim;
    private ValueAnimator snap;
    private Idle last;
    private boolean ready, pressing, dragging, chatOpen, restored;
    private float downX, downY, startTx, startTy;
    private final Runnable idleTick = this::playIdle;

    public FloatingBofoView(Context c) { this(c, null); }

    public FloatingBofoView(Context c, AttributeSet a) {
        super(c, a);
        dp = c.getResources().getDisplayMetrics().density;
        slop = ViewConfiguration.get(c).getScaledTouchSlop();
        edge = Math.round(20 * dp);
        boundTop = Math.round(8 * dp);
        boundBottom = Math.round(90 * dp);
        setClipChildren(false);
        setClipToPadding(false);
        setClickable(true);
        setContentDescription(c.getString(R.string.cd_mascot_ask));

        bobber = new FrameLayout(c);
        bobber.setBackgroundResource(R.drawable.bg_circle_white);
        bobber.setElevation(6 * dp);
        bobber.setCameraDistance(8000f * dp);
        addView(bobber, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        MascotView face = new MascotView(c);
        int pad = Math.round(4 * dp);
        face.setPadding(pad, pad, pad, pad);
        face.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        bobber.addView(face, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        zzz = new TextView(c);
        zzz.setText("Zz");
        zzz.setTextSize(11);
        zzz.setTypeface(null, Typeface.BOLD);
        zzz.setTextColor(ContextCompat.getColor(c, R.color.bofo_primary));
        zzz.setAlpha(0f);
        zzz.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        addView(zzz, new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.END));
        ready = true;
    }

    // ------------------------------------------------------------------ public API

    /** Top and bottom space (px) the button must keep clear: the bottom value is the space taken by the tab bar. */
    public void setBounds(int topPx, int bottomPx) {
        boundTop = topPx; boundBottom = bottomPx;
        clampCurrent();
    }

    /** Chat open: the button fades away (the mascot lives in the chat header) and comes back with a pop when it closes. */
    public void setChatOpen(boolean open) {
        if (chatOpen == open) return;
        chatOpen = open;
        animate().cancel();
        if (open) {
            pause();
            animate().alpha(0f).scaleX(.6f).scaleY(.6f).setDuration(140)
                    .withEndAction(() -> { if (chatOpen) setVisibility(INVISIBLE); }).start();
        } else {
            setVisibility(VISIBLE);
            setAlpha(0f); setScaleX(.6f); setScaleY(.6f);
            animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(240).setInterpolator(new OvershootInterpolator(2f)).start();
            resume();
        }
    }

    // ------------------------------------------------------------------ lifecycle

    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); resume(); }
    @Override protected void onDetachedFromWindow() { pause(); if (savedMoved) remember(); super.onDetachedFromWindow(); }
    @Override protected void onVisibilityChanged(View changed, int v) { super.onVisibilityChanged(changed, v); if (v == VISIBLE) resume(); else pause(); }
    @Override protected void onWindowVisibilityChanged(int v) { super.onWindowVisibilityChanged(v); if (v == VISIBLE) resume(); else pause(); }

    private void resume() {
        if (!ready || !isAttachedToWindow() || !isShown() || chatOpen) return;
        startFloat();
        scheduleIdle();
    }

    private void pause() {
        if (!ready) return;
        handler.removeCallbacks(idleTick);
        endIdle();
        stopFloat();
    }

    // ------------------------------------------------------------------ floating + idle moves

    private boolean animsOn() { return ValueAnimator.areAnimatorsEnabled(); }

    private void startFloat() {
        if (bob != null || !animsOn() || !isShown() || chatOpen) return;
        bob = ObjectAnimator.ofFloat(bobber, View.TRANSLATION_Y, 0f, -6 * dp);
        bob.setDuration(1200); bob.setRepeatMode(ValueAnimator.REVERSE); bob.setRepeatCount(ValueAnimator.INFINITE);
        bob.setInterpolator(new AccelerateDecelerateInterpolator());
        tilt = ObjectAnimator.ofFloat(bobber, View.ROTATION, -2.5f, 2.5f);
        tilt.setDuration(1900); tilt.setRepeatMode(ValueAnimator.REVERSE); tilt.setRepeatCount(ValueAnimator.INFINITE);
        tilt.setInterpolator(new AccelerateDecelerateInterpolator());
        bob.start(); tilt.start();
    }

    private void stopFloat() {
        if (bob != null) { bob.cancel(); bob = null; }
        if (tilt != null) { tilt.cancel(); tilt = null; }
        bobber.setTranslationY(0f);
        bobber.setRotation(0f);
    }

    private boolean canIdle() { return ready && isAttachedToWindow() && isShown() && !chatOpen && !pressing && !dragging && animsOn(); }

    private void scheduleIdle() {
        handler.removeCallbacks(idleTick);
        if (canIdle()) handler.postDelayed(idleTick, 4000 + rnd.nextInt(6000));
    }

    private void playIdle() {
        if (!canIdle() || idleAnim != null) return;
        Idle[] all = Idle.values();
        Idle pick;
        do { pick = all[rnd.nextInt(all.length)]; } while (pick == last);
        last = pick;
        stopFloat();
        resetPose();
        final Animator a = build(pick);
        idleAnim = a;
        a.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled;
            @Override public void onAnimationCancel(Animator an) { cancelled = true; }
            @Override public void onAnimationEnd(Animator an) {
                if (cancelled) return;
                idleAnim = null;
                resetPose();
                startFloat();
                scheduleIdle();
            }
        });
        a.start();
    }

    /** Ends the current move at once (touch, chat open, hidden). The caller restarts the float if it still wants it. */
    private void endIdle() {
        handler.removeCallbacks(idleTick);
        if (idleAnim != null) { Animator a = idleAnim; idleAnim = null; a.cancel(); }
        resetPose();
    }

    private void resetPose() {
        float w = bobber.getWidth() > 0 ? bobber.getWidth() : 52 * dp;
        bobber.setPivotX(w / 2f); bobber.setPivotY(w / 2f);
        bobber.setTranslationX(0f); bobber.setTranslationY(0f);
        bobber.setRotation(0f); bobber.setRotationY(0f);
        bobber.setScaleX(1f); bobber.setScaleY(1f); bobber.setAlpha(1f);
        zzz.setAlpha(0f); zzz.setTranslationY(0f);
    }

    private Animator build(Idle p) {
        final float w = bobber.getWidth() > 0 ? bobber.getWidth() : 52 * dp;
        final TimeInterpolator lin = new LinearInterpolator(), soft = new AccelerateDecelerateInterpolator();
        switch (p) {
            case WIGGLE: {                                                   // quick shake, twice
                ObjectAnimator r = ObjectAnimator.ofFloat(bobber, View.ROTATION, 0f, -14f, 12f, -9f, 6f, 0f);
                r.setDuration(850); r.setRepeatCount(1);
                return r;
            }
            case HOP: {                                                      // two hops, squash and stretch from the feet
                bobber.setPivotY(w);
                float[] f = {0f, .12f, .38f, .62f, .9f, 1f};
                AnimatorSet s = new AnimatorSet();
                s.playTogether(
                        kf(bobber, View.TRANSLATION_Y, f, new float[]{0, 0, -16 * dp, 0, 0, 0}, 650, 1, lin),
                        kf(bobber, View.SCALE_Y, f, new float[]{1f, .88f, 1.1f, 1f, .9f, 1f}, 650, 1, lin),
                        kf(bobber, View.SCALE_X, f, new float[]{1f, 1.08f, .95f, 1f, 1.07f, 1f}, 650, 1, lin));
                return s;
            }
            case SWAY: {                                                     // looks left, then right
                float[] f = {0f, .25f, .75f, 1f};
                AnimatorSet s = new AnimatorSet();
                s.playTogether(
                        kf(bobber, View.TRANSLATION_X, f, new float[]{0, -6 * dp, 6 * dp, 0}, 1500, 0, soft),
                        kf(bobber, View.ROTATION, f, new float[]{0, -7, 7, 0}, 1500, 0, soft));
                return s;
            }
            case FLIP: {                                                     // one coin flip around the vertical axis
                ObjectAnimator r = ObjectAnimator.ofFloat(bobber, View.ROTATION_Y, 0f, 360f);
                r.setDuration(900); r.setInterpolator(new DecelerateInterpolator());
                return r;
            }
            case DASH: {                                                     // two small loops above its spot (original: "runs a circle")
                final float rad = 9 * dp;
                ValueAnimator v = ValueAnimator.ofFloat(0f, (float) (2 * Math.PI));
                v.setDuration(1000); v.setInterpolator(lin); v.setRepeatCount(1);
                v.addUpdateListener(a -> {
                    double t = (Float) a.getAnimatedValue();
                    bobber.setTranslationX((float) (Math.sin(t) * rad));
                    bobber.setTranslationY((float) (-(1 - Math.cos(t)) * rad));
                    bobber.setRotation((float) (Math.sin(t) * 10));
                });
                return v;
            }
            case THINK: {                                                    // head tilt, slow pulse
                ObjectAnimator in = ObjectAnimator.ofFloat(bobber, View.ROTATION, 0f, -10f);
                in.setDuration(300); in.setInterpolator(new DecelerateInterpolator());
                ObjectAnimator sx = ObjectAnimator.ofFloat(bobber, View.SCALE_X, 1f, 1.07f);
                ObjectAnimator sy = ObjectAnimator.ofFloat(bobber, View.SCALE_Y, 1f, 1.07f);
                for (ObjectAnimator o : new ObjectAnimator[]{sx, sy}) { o.setDuration(450); o.setRepeatMode(ValueAnimator.REVERSE); o.setRepeatCount(5); }
                AnimatorSet pulse = new AnimatorSet();
                pulse.playTogether(sx, sy);
                ObjectAnimator out = ObjectAnimator.ofFloat(bobber, View.ROTATION, -10f, 0f);
                out.setDuration(300);
                AnimatorSet s = new AnimatorSet();
                s.playSequentially(in, pulse, out);
                return s;
            }
            default: {                                                       // SLEEP: dims, breathes slowly, "Zz" rises
                ObjectAnimator down = ObjectAnimator.ofFloat(bobber, View.ALPHA, 1f, .7f);
                down.setDuration(250);
                ObjectAnimator sx = ObjectAnimator.ofFloat(bobber, View.SCALE_X, 1f, 1.06f);
                ObjectAnimator sy = ObjectAnimator.ofFloat(bobber, View.SCALE_Y, 1f, 1.06f);
                for (ObjectAnimator o : new ObjectAnimator[]{sx, sy}) { o.setDuration(1000); o.setRepeatMode(ValueAnimator.REVERSE); o.setRepeatCount(3); }
                ObjectAnimator rise = ObjectAnimator.ofFloat(zzz, View.TRANSLATION_Y, 0f, -16 * dp);
                rise.setDuration(2000); rise.setRepeatCount(1); rise.setInterpolator(lin);
                ObjectAnimator fade = kf(zzz, View.ALPHA, new float[]{0f, .2f, .8f, 1f}, new float[]{0f, 1f, 1f, 0f}, 2000, 1, lin);
                AnimatorSet mid = new AnimatorSet();
                mid.playTogether(sx, sy, rise, fade);
                ObjectAnimator up = ObjectAnimator.ofFloat(bobber, View.ALPHA, .7f, 1f);
                up.setDuration(250);
                AnimatorSet s = new AnimatorSet();
                s.playSequentially(down, mid, up);
                return s;
            }
        }
    }

    private static ObjectAnimator kf(View target, Property<View, Float> prop, float[] at, float[] val, long ms, int repeat, TimeInterpolator ti) {
        Keyframe[] k = new Keyframe[at.length];
        for (int i = 0; i < at.length; i++) k[i] = Keyframe.ofFloat(at[i], val[i]);
        ObjectAnimator o = ObjectAnimator.ofPropertyValuesHolder(target, PropertyValuesHolder.ofKeyframe(prop, k));
        o.setDuration(ms); o.setRepeatCount(repeat); o.setInterpolator(ti);
        return o;
    }

    // ------------------------------------------------------------------ touch: press, drag, snap to the nearest edge, tap

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                pressing = true; dragging = false;
                cancelSnap();
                endIdle(); startFloat();
                downX = e.getRawX(); downY = e.getRawY();
                startTx = getTranslationX(); startTy = getTranslationY();
                bobber.animate().scaleX(.92f).scaleY(.92f).setDuration(90).start();
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            case MotionEvent.ACTION_MOVE: {
                float dx = e.getRawX() - downX, dy = e.getRawY() - downY;
                if (!dragging && Math.hypot(dx, dy) > slop) {
                    dragging = true;
                    bobber.animate().scaleX(1.1f).scaleY(1.1f).setDuration(120).start();
                }
                if (dragging) {
                    setTranslationX(clampX(startTx + dx));
                    setTranslationY(clampY(startTy + dy));
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                boolean wasDrag = dragging, up = e.getActionMasked() == MotionEvent.ACTION_UP;
                pressing = false; dragging = false;
                bobber.animate().scaleX(1f).scaleY(1f).setDuration(140).start();
                if (wasDrag) { savedMoved = true; snapToEdge(); }
                else if (up) performClick();
                scheduleIdle();
                return true;
            }
            default:
                return super.onTouchEvent(e);
        }
    }

    @Override public boolean performClick() { return super.performClick(); }

    private float clampX(float tx) {
        if (!(getParent() instanceof View)) return tx;
        View p = (View) getParent();
        float min = edge - getLeft(), max = p.getWidth() - edge - getWidth() - getLeft();
        if (max < min) max = min;
        return Math.max(min, Math.min(max, tx));
    }

    private float clampY(float ty) {
        if (!(getParent() instanceof View)) return ty;
        View p = (View) getParent();
        float min = boundTop - getTop(), max = p.getHeight() - boundBottom - getHeight() - getTop();
        if (max < min) max = min;
        return Math.max(min, Math.min(max, ty));
    }

    private void clampCurrent() {
        if (!savedMoved || getWidth() == 0) return;
        setTranslationX(clampX(getTranslationX()));
        setTranslationY(clampY(getTranslationY()));
    }

    private void snapToEdge() {
        if (!(getParent() instanceof View)) return;
        View p = (View) getParent();
        float centre = getLeft() + getTranslationX() + getWidth() / 2f;
        float target = centre < p.getWidth() / 2f ? edge - getLeft() : p.getWidth() - edge - getWidth() - getLeft();
        if (!animsOn()) { setTranslationX(target); remember(); return; }
        snap = ValueAnimator.ofFloat(getTranslationX(), target);
        snap.setDuration(220); snap.setInterpolator(new OvershootInterpolator(1.1f));
        snap.addUpdateListener(a -> setTranslationX((Float) a.getAnimatedValue()));
        snap.addListener(new AnimatorListenerAdapter() { @Override public void onAnimationEnd(Animator an) { remember(); } });
        snap.start();
    }

    private void cancelSnap() { if (snap != null) { snap.cancel(); snap = null; } }

    private void remember() { savedTx = getTranslationX(); savedTy = getTranslationY(); }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        if (!restored) {
            restored = true;
            if (savedMoved) { setTranslationX(clampX(savedTx)); setTranslationY(clampY(savedTy)); }
        } else if (changed) {
            clampCurrent();                                     // rotation / insets: keep it on screen
        }
    }
}
