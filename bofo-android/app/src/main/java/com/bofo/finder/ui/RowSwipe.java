package com.bofo.finder.ui;

import android.animation.ValueAnimator;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.ListView;

import com.bofo.finder.util.AdminUndoRules;

/**
 * v2.5.0: swipe a ListView row sideways to commit an action (Reports: dismiss). Only rows the host allows move; a short or vertical drag behaves as before
 * (scroll, tap, long-press). The row slides back when the swipe is too short. No slide animation when the system has animations off.
 */
final class RowSwipe implements View.OnTouchListener {

    interface Host {
        boolean canSwipe(int position);
        void onSwiped(int position);
    }

    private final ListView list;
    private final Host host;
    private final int slop;
    private float downX, downY, dx;
    private int pos = -1;
    private View row;
    private boolean swiping;

    RowSwipe(ListView list, Host host) {
        this.list = list;
        this.host = host;
        this.slop = ViewConfiguration.get(list.getContext()).getScaledTouchSlop();
    }

    @Override
    public boolean onTouch(View v, MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getX(); downY = e.getY(); dx = 0; swiping = false; row = null;
                pos = list.pointToPosition((int) downX, (int) downY);
                if (pos >= 0 && host.canSwipe(pos)) row = list.getChildAt(pos - list.getFirstVisiblePosition());
                else pos = -1;
                return false;
            case MotionEvent.ACTION_MOVE: {
                if (row == null) return false;
                float mx = e.getX() - downX, my = e.getY() - downY;
                if (!swiping) {
                    if (Math.abs(mx) > slop * 2 && Math.abs(mx) > Math.abs(my) * 1.5f) {
                        swiping = true;
                        list.requestDisallowInterceptTouchEvent(true);          // the pull-to-refresh parent must not take over
                        MotionEvent c = MotionEvent.obtain(e);                  // the list must forget the press: no tap and no long-press menu
                        c.setAction(MotionEvent.ACTION_CANCEL);
                        list.onTouchEvent(c);
                        c.recycle();
                    } else {
                        return false;
                    }
                }
                dx = mx;
                row.setTranslationX(dx);
                row.setAlpha(1f - Math.min(1f, Math.abs(dx) / Math.max(1f, row.getWidth() * 1.2f)));
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                final View r = row;
                final int p = pos;
                boolean was = swiping;
                row = null; swiping = false; pos = -1;
                if (r == null || !was) return false;
                final boolean commit = e.getActionMasked() == MotionEvent.ACTION_UP && AdminUndoRules.swipeCommits(dx, r.getWidth());
                final float target = commit ? Math.signum(dx) * r.getWidth() : 0f;
                final Runnable end = () -> { if (commit) host.onSwiped(p); };
                if (ValueAnimator.areAnimatorsEnabled()) {
                    r.animate().translationX(target).alpha(commit ? 0f : 1f).setDuration(150).withEndAction(end).start();
                } else {
                    r.setTranslationX(target);
                    r.setAlpha(commit ? 0f : 1f);
                    end.run();
                }
                return true;
            }
            default:
                return false;
        }
    }
}
