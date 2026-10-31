package com.bofo.finder.ui;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import androidx.appcompat.widget.AppCompatImageView;

import com.bofo.finder.R;

/** The BOFO mascot (vector from design/mascot) with four moods done by property animators: sitting, thinking, running in circles, sleeping. */
public class MascotView extends AppCompatImageView {
    public enum Mood { SITTING, THINKING, RUNNING, SLEEPING }

    private Animator anim;
    private Mood mood = Mood.SITTING;

    public MascotView(Context c) { this(c, null); }
    public MascotView(Context c, AttributeSet a) {
        super(c, a);
        setImageResource(R.drawable.mascot_happy);
        setContentDescription(c.getString(R.string.cd_mascot_ask));
        setMood(Mood.SITTING);
    }

    public Mood mood() { return mood; }

    public void setMood(Mood m) {
        mood = m;
        stop();
        switch (m) {
            case THINKING: {                                   // slow head tilt left and right
                ObjectAnimator r = ObjectAnimator.ofFloat(this, View.ROTATION, -8f, 8f);
                r.setDuration(650); r.setRepeatMode(ObjectAnimator.REVERSE); r.setRepeatCount(ObjectAnimator.INFINITE);
                start(r); break;
            }
            case RUNNING: {                                    // small circle: x and y offsets a quarter turn apart
                float rad = getResources().getDisplayMetrics().density * 10f;
                ObjectAnimator x = ObjectAnimator.ofFloat(this, "mascotAngle", 0f, 360f);
                x.setDuration(900); x.setInterpolator(new LinearInterpolator()); x.setRepeatCount(ObjectAnimator.INFINITE);
                circle = rad;
                start(x); break;
            }
            case SLEEPING: {                                   // faded, slowly "breathing"
                setAlpha(0.6f);
                AnimatorSet s = new AnimatorSet();
                ObjectAnimator sx = ObjectAnimator.ofFloat(this, View.SCALE_X, 1f, 1.05f), sy = ObjectAnimator.ofFloat(this, View.SCALE_Y, 1f, 1.05f);
                for (ObjectAnimator o : new ObjectAnimator[]{sx, sy}) { o.setDuration(1600); o.setRepeatMode(ObjectAnimator.REVERSE); o.setRepeatCount(ObjectAnimator.INFINITE); }
                s.playTogether(sx, sy);
                start(s); break;
            }
            default: break;                                    // SITTING: still
        }
    }

    private float circle;

    /** Animated by the RUNNING mood (degrees). */
    @SuppressWarnings("unused")
    public void setMascotAngle(float deg) {
        double t = Math.toRadians(deg);
        setTranslationX((float) (Math.cos(t) * circle));
        setTranslationY((float) (Math.sin(t) * circle));
    }

    private void start(Animator a) { anim = a; a.start(); }

    private void stop() {
        if (anim != null) { anim.cancel(); anim = null; }
        setRotation(0f); setScaleX(1f); setScaleY(1f); setTranslationX(0f); setTranslationY(0f); setAlpha(1f);
    }

    @Override protected void onDetachedFromWindow() { stop(); super.onDetachedFromWindow(); }
}
