package com.bofo.finder.ui;

import android.animation.AnimatorSet;
import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.os.Build;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bofo.finder.R;

import java.util.ArrayList;
import java.util.List;

/**
 * The bottom tab bar, ported from the JavaFX Shell: a floating white bar, outline icons with a label under each,
 * a lavender highlight behind the active tab that stretches over to the tab you press (the leading edge moves first,
 * the trailing edge follows) while the icon and label fade from pale to strong.
 * One tab can be an "action" tab (owners: Create): a raised dark square with a plus. It runs the action instead of switching content.
 * Student, owner and admin use the same view with different tab lists (see ShellFragment).
 */
public class DockBarView extends ViewGroup {

    /** One tab. icon = a stroke-only vector drawable, tinted by this view. */
    public static final class Tab {
        final int id;
        final int icon;
        final String label;
        final boolean action, chat;

        public Tab(int id, @DrawableRes int icon, @NonNull String label, boolean action, boolean chat) {
            this.id = id; this.icon = icon; this.label = label; this.action = action; this.chat = chat;
        }
    }

    public interface Listener {
        /** The person pressed a normal tab (the bar has already moved its highlight). */
        void onTab(int id);
        /** The person pressed the action tab (Create). */
        void onAction(int id);
    }

    private final int IDLE, ACTIVE, CREATE_LABEL;      // v2.0.117: from colors.xml so they follow the theme (were static literals)
    private static final ArgbEvaluator ARGB = new ArgbEvaluator();
    private static final PathInterpolator LEAD = new PathInterpolator(0.2f, 0.8f, 0.2f, 1f);
    private static final PathInterpolator TRAIL = new PathInterpolator(0.4f, 0f, 0.2f, 1f);

    private final float dp;
    private final float barH, tabH, pad, inset, minTab, maxTab, actionTop, actionH;
    private float top;                                   // room above the bar (more when there is a raised Create button)
    private float tabW, barW, barLeft;
    private final View bar, highlight;
    private final List<Tab> tabs = new ArrayList<>();
    private final List<FrameLayout> cells = new ArrayList<>();
    private ImageView[] icons = new ImageView[0];
    private TextView[] labels = new TextView[0];
    private float[] progress = new float[0];
    private boolean[] bold = new boolean[0];
    private ValueAnimator[] anims = new ValueAnimator[0];
    private TextView badge;
    private int current = -1;                            // index of the active tab, -1 = none yet
    private float hl, hr;                                // left / right edge of the highlight
    private AnimatorSet hlAnim;
    private Listener listener;

    public DockBarView(@NonNull Context context) { this(context, null); }

    public DockBarView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        IDLE = androidx.core.content.ContextCompat.getColor(context, R.color.bofo_idle);
        ACTIVE = androidx.core.content.ContextCompat.getColor(context, R.color.bofo_ink);
        CREATE_LABEL = ACTIVE;
        dp = context.getResources().getDisplayMetrics().density;
        barH = 68 * dp; tabH = 54 * dp; pad = 8 * dp; inset = 3 * dp; minTab = 54 * dp; maxTab = 84 * dp;
        actionTop = 4 * dp; actionH = 72 * dp;
        top = 6 * dp;
        setClipChildren(false);
        setClipToPadding(false);

        bar = new View(context);
        bar.setBackgroundResource(R.drawable.bg_dock_bar);
        bar.setElevation(6 * dp);
        bar.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
        if (Build.VERSION.SDK_INT >= 28) {               // soft lavender glow, like the JavaFX bar
            bar.setOutlineSpotShadowColor(0x66000000);
            bar.setOutlineAmbientShadowColor(0x14000000);
        }
        addView(bar);

        highlight = new View(context);
        highlight.setBackgroundResource(R.drawable.bg_dock_highlight);
        // Same elevation as the bar (so it is drawn after it, in child order) but no shadow of its own.
        highlight.setElevation(10 * dp);
        highlight.setOutlineProvider(null);
        highlight.setVisibility(INVISIBLE);
        addView(highlight);
    }

    public void setListener(@Nullable Listener l) { listener = l; }

    private int px(float v) { return Math.round(v * dp); }

    /** Height of the bar area in pixels (valid after setTabs). The shell uses it to keep the Ask BOFO button above the bar. */
    public int barHeightPx() { return Math.round(top + barH); }

    public int selectedId() { return current >= 0 && current < tabs.size() ? tabs.get(current).id : 0; }

    /** Replaces all tabs. Call once, before the first select. */
    public void setTabs(@NonNull List<Tab> list) {
        if (hlAnim != null) hlAnim.cancel();
        for (ValueAnimator a : anims) if (a != null) a.cancel();
        for (View c : cells) removeView(c);
        cells.clear(); tabs.clear(); tabs.addAll(list);
        int n = tabs.size();
        icons = new ImageView[n]; labels = new TextView[n]; progress = new float[n]; bold = new boolean[n]; anims = new ValueAnimator[n];
        badge = null;
        boolean hasAction = false;
        Context ctx = getContext();
        for (int i = 0; i < n; i++) {
            final Tab t = tabs.get(i);
            final int idx = i;
            FrameLayout cell = new FrameLayout(ctx);
            cell.setClipChildren(false);
            // The bar has elevation 10 dp. A sibling with lower elevation is drawn UNDER it, which hid every icon and label.
            // Equal elevation = drawn in child order (bar, highlight, cells). No background means no extra shadow.
            cell.setElevation(10 * dp);
            cell.setClickable(true);
            cell.setFocusable(true);
            cell.setContentDescription(t.label);

            LinearLayout col = new LinearLayout(ctx);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setClipChildren(false);

            TextView label = new TextView(ctx);
            label.setText(t.label);
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            label.setSingleLine(true);
            label.setIncludeFontPadding(false);
            labels[i] = label;

            if (t.action) {
                hasAction = true;
                col.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
                FrameLayout square = new FrameLayout(ctx);
                square.setBackgroundResource(R.drawable.bg_dock_create);
                ImageView plus = new ImageView(ctx);
                plus.setImageResource(t.icon);
                plus.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
                square.addView(plus, new FrameLayout.LayoutParams(px(24), px(24), Gravity.CENTER));
                col.addView(square, new LinearLayout.LayoutParams(px(50), px(50)));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.topMargin = px(4);
                label.setTextColor(CREATE_LABEL);
                label.setTypeface(null, Typeface.BOLD);
                col.addView(label, lp);
            } else {
                col.setGravity(Gravity.CENTER);
                ImageView icon = new ImageView(ctx);
                icon.setImageResource(t.icon);
                icon.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
                icons[i] = icon;
                col.addView(icon, new LinearLayout.LayoutParams(px(24), px(24)));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.topMargin = px(3);
                col.addView(label, lp);
                applyProgress(i, 0f);
            }
            cell.addView(col, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            if (t.chat) {                                // unread count, top-right of the Chats tab
                TextView b = new TextView(ctx);
                b.setBackgroundResource(R.drawable.bg_dock_badge);
                b.setTextColor(0xFFFFFFFF);
                b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
                b.setTypeface(null, Typeface.BOLD);
                b.setGravity(Gravity.CENTER);
                b.setMinWidth(px(18));
                b.setPadding(px(4), px(1), px(4), px(1));
                b.setIncludeFontPadding(false);
                b.setVisibility(GONE);
                FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.END);
                bl.topMargin = px(2);
                bl.setMarginEnd(px(12));
                cell.addView(b, bl);
                badge = b;
            }

            cell.setOnClickListener(v -> onCellTap(idx));
            cells.add(cell);
            addView(cell);
        }
        top = (hasAction ? 18 : 6) * dp;
        current = -1;
        highlight.setVisibility(INVISIBLE);
        requestLayout();
    }

    /** Unread count on the Chats tab (0 hides it). */
    public void setBadge(int n) {
        if (badge == null) return;
        if (n <= 0) { badge.setVisibility(GONE); return; }
        badge.setText(n > 9 ? "9+" : String.valueOf(n));
        badge.setVisibility(VISIBLE);
    }

    /** Moves the highlight to the tab with this id. Never calls the listener (the caller shows the page). Action tabs cannot be selected. */
    public void select(int id, boolean animate) {
        int idx = -1;
        for (int i = 0; i < tabs.size(); i++) if (tabs.get(i).id == id && !tabs.get(i).action) { idx = i; break; }
        if (idx < 0 || idx == current) return;
        boolean anim = animate && current >= 0;
        for (int j = 0; j < tabs.size(); j++) setActive(j, j == idx, anim && (j == idx || j == current));
        highlight.setVisibility(VISIBLE);
        current = idx;
        moveHighlight(idx, anim);
    }

    private void onCellTap(int idx) {
        Tab t = tabs.get(idx);
        if (t.action) { if (listener != null) listener.onAction(t.id); return; }
        if (idx == current) return;
        select(t.id, true);
        if (listener != null) listener.onTab(t.id);
    }

    // ---- tab colours: icon and label fade from pale to strong ----------------------------------------------------

    private void setActive(int i, boolean on, boolean animate) {
        if (tabs.get(i).action) return;
        if (anims[i] != null) anims[i].cancel();
        cells.get(i).setSelected(on);
        float to = on ? 1f : 0f;
        if (!animate) { applyProgress(i, to); return; }
        final int idx = i;
        ValueAnimator a = ValueAnimator.ofFloat(progress[i], to);
        a.setDuration(260);
        a.setInterpolator(new AccelerateDecelerateInterpolator());
        a.addUpdateListener(v -> applyProgress(idx, (float) v.getAnimatedValue()));
        anims[i] = a;
        a.start();
    }

    private void applyProgress(int i, float p) {
        progress[i] = p;
        if (icons[i] == null) return;
        int c = (int) (Integer) ARGB.evaluate(p, IDLE, ACTIVE);
        icons[i].setColorFilter(c, PorterDuff.Mode.SRC_IN);
        labels[i].setTextColor(c);
        boolean b = p > 0.5f;
        if (b != bold[i]) { bold[i] = b; labels[i].setTypeface(null, b ? Typeface.BOLD : Typeface.NORMAL); }
    }

    // ---- highlight: liquid move, the edge in the direction of travel goes first and fast ---------------------------

    private float edgeL(int i) { return barLeft + pad + i * tabW + inset; }

    private float edgeR(int i) { return barLeft + pad + (i + 1) * tabW - inset; }

    private void moveHighlight(int idx, boolean animate) {
        float nl = edgeL(idx), nr = edgeR(idx);
        if (hlAnim != null) hlAnim.cancel();
        if (!animate || getWidth() == 0) { hl = nl; hr = nr; placeHighlight(); return; }
        boolean toRight = nl > hl;
        ValueAnimator a = ValueAnimator.ofFloat(hl, nl);
        a.setDuration(toRight ? 340 : 190);
        a.setInterpolator(toRight ? TRAIL : LEAD);
        a.addUpdateListener(v -> { hl = (float) v.getAnimatedValue(); placeHighlight(); });
        ValueAnimator b = ValueAnimator.ofFloat(hr, nr);
        b.setDuration(toRight ? 190 : 340);
        b.setInterpolator(toRight ? LEAD : TRAIL);
        b.addUpdateListener(v -> { hr = (float) v.getAnimatedValue(); placeHighlight(); });
        AnimatorSet set = new AnimatorSet();
        set.playTogether(a, b);
        hlAnim = set;
        set.start();
    }

    private void placeHighlight() {
        if (current < 0) return;
        int t = Math.round(top + (barH - tabH) / 2f);
        highlight.layout(Math.round(hl), t, Math.round(Math.max(hl, hr)), t + Math.round(tabH));
    }

    // ---- layout: bar width fits the tabs (54 to 84 dp each) and stays centred ------------------------------------

    private void fit(int width) {
        int n = tabs.size();
        tabW = n == 0 ? minTab : Math.max(minTab, Math.min(maxTab, (width - 2 * pad) / n));
        barW = 2 * pad + n * tabW;
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int w = MeasureSpec.getSize(widthSpec);
        fit(w);
        setMeasuredDimension(w, Math.round(top + barH));
        bar.measure(MeasureSpec.makeMeasureSpec(Math.round(barW), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(Math.round(barH), MeasureSpec.EXACTLY));
        highlight.measure(MeasureSpec.makeMeasureSpec(Math.round(tabW - 2 * inset), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(Math.round(tabH), MeasureSpec.EXACTLY));
        for (int i = 0; i < cells.size(); i++) {
            int h = Math.round(tabs.get(i).action ? actionH : tabH);
            cells.get(i).measure(MeasureSpec.makeMeasureSpec(Math.round(tabW), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int w = r - l;
        fit(w);
        barLeft = (w - barW) / 2f;
        bar.layout(Math.round(barLeft), Math.round(top), Math.round(barLeft + barW), Math.round(top + barH));
        float rowY = top + (barH - tabH) / 2f;
        for (int i = 0; i < cells.size(); i++) {
            float x = barLeft + pad + i * tabW;
            float y = tabs.get(i).action ? actionTop : rowY;
            float h = tabs.get(i).action ? actionH : tabH;
            cells.get(i).layout(Math.round(x), Math.round(y), Math.round(x + tabW), Math.round(y + h));
        }
        if (current >= 0 && (hlAnim == null || !hlAnim.isRunning())) { hl = edgeL(current); hr = edgeR(current); }
        placeHighlight();
    }
}
