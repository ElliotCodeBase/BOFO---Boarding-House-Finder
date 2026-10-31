package com.bofo.finder.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bofo.finder.R;
import com.bofo.finder.app.MainActivity;
import com.bofo.finder.databinding.ItemMsgMineBinding;
import com.bofo.finder.databinding.ItemMsgTheirsBinding;
import com.bofo.finder.databinding.ViewBofoChatBinding;
import com.bofo.finder.service.BofoAiService;
import com.bofo.finder.util.Async;
import com.bofo.finder.util.BrowseState;
import com.bofo.finder.util.ChatRules;

/**
 * Ask BOFO as a floating card over the screen (original: the chat card of BofoPanel). It pops out of the floating button and shrinks back into it;
 * tap outside, the X or Back closes it. Same logic as the old bottom sheet: Edge Function bofo-chat, built-in FAQ when offline or refused,
 * the mascot thinks while waiting, the conversation is not saved.
 */
public class BofoChatOverlay extends FrameLayout {
    private final ViewBofoChatBinding b;
    private final float dp;
    private int bottomOffset;
    private boolean shown, greeted, waiting;
    private int token;
    private Runnable onClosed;

    public BofoChatOverlay(@NonNull Context c) { this(c, null); }

    public BofoChatOverlay(@NonNull Context c, @Nullable AttributeSet a) {
        super(c, a);
        dp = c.getResources().getDisplayMetrics().density;
        bottomOffset = Math.round(90 * dp);
        b = ViewBofoChatBinding.inflate(LayoutInflater.from(c), this);
        b.scrim.setOnClickListener(v -> close());
        b.close.setOnClickListener(v -> close());
        b.send.setOnClickListener(v -> ask());
        b.input.setOnEditorActionListener((v, action, ev) -> {
            if (action == EditorInfo.IME_ACTION_SEND) { ask(); return true; }
            return false;
        });
        b.card.setClickable(true);                               // taps on the card never reach the scrim
    }

    public boolean isOpen() { return shown; }

    private void release() { MainActivity.keepAppStill = false; MainActivity.imeListener = null; androidx.core.view.ViewCompat.requestApplyInsets(this); }

    
    public void setOnClosed(@Nullable Runnable r) { onClosed = r; }

    /** Space (px) the card keeps clear at the bottom: the tab bar. */
    public void setBottomOffset(int px) { bottomOffset = px; applyCardSize(getWidth(), getHeight()); }

    /** Pops the card out of the button centre (cx, cy in this view's own coordinates). */
    public void open(float cx, float cy) {
        if (shown) return;
        shown = true;
        setVisibility(VISIBLE); MainActivity.keepAppStill = true; MainActivity.imeListener = () -> applyCardSize(getWidth(), getHeight()); androidx.core.view.ViewCompat.requestApplyInsets(this);
        if (!greeted) { greeted = true; say(getContext().getString(R.string.bofo_hello), false); }
        b.card.setPivotX(cx - b.card.getLeft());
        b.card.setPivotY(cy - b.card.getTop());
        b.card.setScaleX(.15f); b.card.setScaleY(.15f); b.card.setAlpha(0f);
        b.scrim.setAlpha(0f);
        b.card.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(260).setInterpolator(new OvershootInterpolator(.8f)).start();
        b.scrim.animate().alpha(1f).setDuration(200).start();
    }

    public void close() {
        if (!shown) return;
        shown = false;
        InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(getWindowToken(), 0);
        b.input.clearFocus();
        b.card.animate().scaleX(.15f).scaleY(.15f).alpha(0f).setDuration(180).setInterpolator(new AccelerateInterpolator())
                .withEndAction(() -> {
                    if (shown) return;
                    setVisibility(INVISIBLE); release();
                    if (onClosed != null) onClosed.run();
                }).start();
        b.scrim.animate().alpha(0f).setDuration(180).start();
    }

    // ------------------------------------------------------------------ size

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        applyCardSize(w, h);
    }

    /** Same sizing as the original: at most 380 x 500, 12 dp from the sides, never taller than the free space above the tab bar. */
    private void applyCardSize(int w, int h) {
        if (w <= 0 || h <= 0) return;
        int cw = Math.round(Math.min(380 * dp, w - 24 * dp));
        int bm = bottomOffset; if (shown && MainActivity.imeBottom > 0) { int[] loc = new int[2]; getLocationInWindow(loc); int over = loc[1] + h - (getRootView().getHeight() - MainActivity.imeBottom); if (over > 0) bm = Math.max(bm, over + Math.round(8 * dp)); } int ch = Math.round(Math.max(220 * dp, Math.min(500 * dp, h - bm - 24 * dp)));
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) b.card.getLayoutParams();
        if (lp.width != cw || lp.height != ch || lp.bottomMargin != bm) {
            lp.width = cw; lp.height = ch; lp.bottomMargin = bm;
            b.card.setLayoutParams(lp);
        }
    }

    // ------------------------------------------------------------------ chat (moved from BofoPanelSheet)

    private void ask() {
        if (waiting) return;
        String q = ChatRules.outgoing(b.input.getText().toString(), 300);
        if (q.isEmpty()) return;
        b.input.setText("");
        say(q, true);
        waiting = true;
        b.mascot.setMood(MascotView.Mood.THINKING);
        final int my = ++token;
        String college = BrowseState.collegeId;
        Async.run(() -> new BofoAiService().ask(q, college).reply(), (String reply) -> {
            if (my != token) return;
            done(reply.isEmpty() ? BofoAiService.faq(q) : reply);
        }, e -> {
            if (my != token) return;
            done(BofoAiService.faq(q));                          // offline / rate-limited
        });
    }

    private void done(String text) {
        waiting = false;
        b.mascot.setMood(MascotView.Mood.SITTING);
        say(text, false);
    }

    private void say(String text, boolean mine) {
        LayoutInflater in = LayoutInflater.from(getContext());
        View root; TextView body;
        if (mine) { ItemMsgMineBinding m = ItemMsgMineBinding.inflate(in, b.chatBox, false); root = m.getRoot(); body = m.body; m.meta.setVisibility(View.GONE); }
        else { ItemMsgTheirsBinding m = ItemMsgTheirsBinding.inflate(in, b.chatBox, false); root = m.getRoot(); body = m.body; m.meta.setVisibility(View.GONE); }
        body.setText(text);
        b.chatBox.addView(root);
        b.scroll.post(() -> b.scroll.fullScroll(View.FOCUS_DOWN));
    }

    @Override
    protected void onDetachedFromWindow() {
        token++;                                                  // a late answer must not touch a dead screen
        waiting = false;
        MainActivity.keepAppStill = false; MainActivity.imeListener = null; super.onDetachedFromWindow();
    }
}
