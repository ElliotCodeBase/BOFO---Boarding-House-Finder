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
 * Same behaviour as the JavaFX button: a white round button with the {@link BofoMascot} (a port of the JavaFX Mascot, same drawing and
 * same animations) that bobs up and down; after a quiet spell (4-10 s) it plays one of the four idle poses (thinking, sitting, running,
 * sleeping), never the same twice in a row, then rests again. A tap, a drag, the chat opening, hiding or system animations off end it at once.
 * Tap = performClick (the shell opens the chat overlay).
 */
public class FloatingBofoView extends FrameLayout {
    /** Remembered between screens of the same sign-in (the shell is rebuilt when you come back from a detail screen). */
    private static float savedTx, savedTy;
    private static boolean savedMoved;
    public static void forgetPosition() { savedMoved = false; savedTx = 0f; savedTy = 0f; }

    private final FrameLayout bobber;           // white circle + mascot: this is what floats
    private final BofoMascot face;              // fixed 46 dp box inside the 56 dp button; poses overflow it without resizing anything
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random rnd = new Random();
    private final float dp;
    private final int slop, edge;
    private int boundTop, boundBottom;          // px kept free above the view / between the view and the bottom of the parent
    private ObjectAnimator bob;
    private ValueAnimator snap;
    private BofoMascot.Idle last;
    private boolean ready, pressing, dragging, chatOpen, restored, idling;
    private float downX, downY, startTx, startTy;
    private final Runnable idleTick = this::playIdle;
    private final Runnable idleEnd = () -> { endIdle(); startFloat(); scheduleIdle(); };

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
        bobber.setBackgroundResource(R.drawable.bg_bofo_fab);
        bobber.setElevation(6 * dp);
        bobber.setCameraDistance(8000f * dp);
        bobber.setClipChildren(false);
        addView(bobber, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        face = new BofoMascot(c);
        face.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        int box = Math.round(46 * dp);
        bobber.addView(face, new LayoutParams(box, box, Gravity.CENTER));
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
        bob = ObjectAnimator.ofFloat(bobber, View.TRANSLATION_Y, 0f, -6 * dp);       // JavaFX: TranslateTransition 1.2 s, by -6, auto-reverse
        bob.setDuration(1200); bob.setRepeatMode(ValueAnimator.REVERSE); bob.setRepeatCount(ValueAnimator.INFINITE);
        bob.setInterpolator(new AccelerateDecelerateInterpolator());
        bob.start();
    }

    private void stopFloat() {
        if (bob != null) { bob.cancel(); bob = null; }
        bobber.setTranslationY(0f);
        bobber.setRotation(0f);
    }

    private boolean canIdle() { return ready && isAttachedToWindow() && isShown() && !chatOpen && !pressing && !dragging && animsOn(); }

    private void scheduleIdle() {
        handler.removeCallbacks(idleTick);
        if (canIdle()) handler.postDelayed(idleTick, 4000 + rnd.nextInt(6000));
    }

    /** Same pick and same lengths as BofoPanel.startIdle(): never the same pose twice in a row. */
    private void playIdle() {
        if (!canIdle() || idling) return;
        BofoMascot.Idle[] all = BofoMascot.Idle.values();
        BofoMascot.Idle pick;
        do { pick = all[rnd.nextInt(all.length)]; } while (pick == last);
        last = pick;
        stopFloat();
        face.idle(pick);
        idling = true;
        long ms;
        switch (pick) {
            case SLEEPING: ms = 12000 + rnd.nextInt(4000); break;
            case RUNNING:  ms = 5400; break;
            case THINKING: ms = 7000; break;
            default:       ms = 8000; break;
        }
        handler.postDelayed(idleEnd, ms);
    }

    /** Ends the current pose at once (touch, chat open, hidden). The caller restarts the float if it still wants it. */
    private void endIdle() {
        handler.removeCallbacks(idleTick);
        handler.removeCallbacks(idleEnd);
        if (idling) { idling = false; face.endIdle(); }
        resetPose();
    }

    private void resetPose() {
        float w = bobber.getWidth() > 0 ? bobber.getWidth() : 56 * dp;
        bobber.setPivotX(w / 2f); bobber.setPivotY(w / 2f);
        bobber.setTranslationX(0f); bobber.setTranslationY(0f);
        bobber.setRotation(0f); bobber.setRotationY(0f);
        bobber.setScaleX(1f); bobber.setScaleY(1f); bobber.setAlpha(1f);
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
