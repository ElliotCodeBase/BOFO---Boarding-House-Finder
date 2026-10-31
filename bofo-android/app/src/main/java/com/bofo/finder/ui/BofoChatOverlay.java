package com.bofo.finder.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.graphics.Typeface;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bofo.finder.R;
import com.bofo.finder.app.MainActivity;
import com.bofo.finder.databinding.ItemBofoChipsBinding;
import com.bofo.finder.databinding.ItemBofoCompareBinding;
import com.bofo.finder.databinding.ItemBofoResultBinding;
import com.bofo.finder.databinding.ItemMsgMineBinding;
import com.bofo.finder.databinding.ItemMsgTheirsBinding;
import com.bofo.finder.databinding.ViewBofoChatBinding;
import com.bofo.finder.model.BoardingHouse;
import com.bofo.finder.model.BofoAnswer;
import com.bofo.finder.service.BhService;
import com.bofo.finder.service.BofoAiService;
import com.bofo.finder.service.SupabaseClient;
import com.bofo.finder.util.Async;
import com.bofo.finder.util.BrowseState;
import com.bofo.finder.util.BofoIntentLocal;
import com.bofo.finder.util.ChatRules;
import com.bofo.finder.util.Geo;
import com.bofo.finder.util.ListRules;
import com.bofo.finder.util.Money;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.google.android.material.chip.Chip;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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

    /** The host (ShellFragment) runs the actions: it owns navigation. The chat card closes itself first. */
    public interface BofoActionListener { void onAction(BofoAnswer.Action action, BofoAnswer answer); }

    public void setOnAction(@Nullable BofoActionListener l) { actionListener = l; }

    /** Result of asking for the phone position: ok = a position is now known; reasonRes = a string explaining why not (0 = none). */
    public interface GateResult { void done(boolean ok, int reasonRes); }
    /** The host (ShellFragment) turns location on when a question needs it: permission, phone Location popup, then one position read. */
    public interface LocationGate { void ensure(GateResult r); }
    private LocationGate locationGate;
    public void setLocationGate(@Nullable LocationGate g) { locationGate = g; }

    private static final java.util.regex.Pattern WANTS_LOCATION = java.util.regex.Pattern.compile(
            "near|closest|malapit|lapit|layo|\\bfar\\b|distance|walk|to me\\b|from me\\b|my location|sa akin|sa akon", java.util.regex.Pattern.CASE_INSENSITIVE);
    private static boolean haveLocation() {
        return (BrowseState.nearMe && BrowseState.userLat != null && BrowseState.userLng != null) || (BrowseState.deviceLat != null && BrowseState.deviceLng != null);
    }

    private final List<BofoAiService.Turn> history = new ArrayList<>();     // last 6 turns, memory only
    private BofoActionListener actionListener;
    private View typing, chipsRow;

    private void ask() {
        if (waiting) return;
        String q = ChatRules.outgoing(b.input.getText().toString(), 300);
        if (q.isEmpty()) return;
        b.input.setText("");
        send(q);
    }

    private void send(String q) {
        if (waiting) return;
        if (chipsRow != null) { b.chatBox.removeView(chipsRow); chipsRow = null; }      // old chips are stale once the user goes on
        say(q, true);
        final List<BofoAiService.Turn> past = new ArrayList<>(history);
        remember(true, q, null);
        final List<String> lastIds = lastIds(past);
        waiting = true;
        b.mascot.idle(BofoMascot.Idle.THINKING);
        showTyping();
        final int my = ++token;
        if (locationGate != null && BrowseState.collegeId == null && !haveLocation() && WANTS_LOCATION.matcher(q).find()) {   // a chosen college is the start point: no waiting for the phone
            // "nearest ..." with no position yet: turn location on now (permission + phone popup), then ask.
            locationGate.ensure((ok, reasonRes) -> {
                if (my != token) return;
                if (!ok && reasonRes != 0 && BrowseState.collegeId == null) { finish(); reply(getContext().getString(reasonRes)); return; }   // nothing to measure from
                call(q, past, lastIds, my);                                                                                             // position found, or a campus is chosen
            });
        } else call(q, past, lastIds, my);
    }

    private void call(String q, List<BofoAiService.Turn> past, List<String> lastIds, int my) {
        final String college = BrowseState.collegeId;
        Async.run(() -> new BofoAiService().ask(q, past, college), (BofoAnswer a) -> {
            if (my != token) return;
            if (a.reply().isEmpty() && a.results().isEmpty()) offline(q, lastIds, my); else show(a);
        }, e -> {
            if (my != token) return;
            if (e instanceof SupabaseClient.ApiException ex && ex.status == 429) { finish(); reply(getContext().getString(R.string.bofo_rate_limit)); }
            else offline(q, lastIds, my);                          // no network / function down: rule-based answer, then the FAQ
        });
    }

    /** Offline or server error: answer from the list the app already has (BofoIntentLocal), else the built-in FAQ. */
    private void offline(String q, List<String> lastIds, int my) {
        Async.run(() -> {
            List<BoardingHouse> list = new BhService().list(BhService.Tab.NEW, BrowseState.refLat(), BrowseState.refLng(), null, null);
            return BofoIntentLocal.answer(q, list, lastIds);
        }, (BofoAnswer a) -> {
            if (my != token) return;
            if (a == null) { finish(); reply(BofoAiService.faq(q)); } else show(a);
        }, e2 -> {
            if (my != token) return;
            finish(); reply(BofoAiService.faq(q));
        });
    }

    private void finish() {
        waiting = false;
        b.mascot.endIdle();
        if (typing != null) { b.chatBox.removeView(typing); typing = null; }
    }

    private void reply(String text) { say(text, false); remember(false, text, null); }

    private void show(BofoAnswer a) {
        finish();
        String text = a.reply().isEmpty() ? BofoAiService.faq("") : a.reply();
        say(text, false);
        LayoutInflater in = LayoutInflater.from(getContext());
        for (BofoAnswer.Result r : a.results()) addResult(in, r, a);
        if (a.compare() != null && a.compare().rows().size() > 0) addCompare(in, a);
        if (a.degraded()) {
            TextView t = new TextView(getContext());
            t.setText(R.string.bofo_degraded); t.setTextSize(11); t.setTextColor(getContext().getColor(R.color.bofo_muted));
            t.setPadding(Math.round(14 * dp), Math.round(2 * dp), Math.round(14 * dp), 0);
            b.chatBox.addView(t);
        }
        addChips(in, a);
        remember(false, text, a.resultIds());
        b.scroll.post(() -> b.scroll.fullScroll(View.FOCUS_DOWN));
        // "show me the path / directions": after a moment to read the answer, go straight to the map and draw it (no extra tap)
        if ("directions".equals(a.intent()) && !a.results().isEmpty()) {
            for (BofoAnswer.Action act : a.actions()) {
                if (!"directions".equals(act.type())) continue;
                final int mine = token;
                postDelayed(() -> { if (mine == token && isAttachedToWindow()) fire(act, a); }, 1200);
                break;
            }
        }
    }

    private void fire(BofoAnswer.Action action, BofoAnswer a) { if (actionListener != null) actionListener.onAction(action, a); }

    private void addResult(LayoutInflater in, BofoAnswer.Result r, BofoAnswer a) {
        ItemBofoResultBinding v = ItemBofoResultBinding.inflate(in, b.chatBox, false);
        v.name.setText(r.name());
        v.price.setText(Money.perMonth(r.price()));
        List<String> meta = new ArrayList<>();
        if (r.distanceKm() != null) meta.add(Geo.walkText(r.distanceKm()));
        meta.add(r.reviewCount() > 0 ? "\u2605 " + ListRules.ratingText(r.rating(), r.reviewCount()) : getContext().getString(R.string.bofo_no_reviews));
        meta.add(r.availableBeds() == 1 ? getContext().getString(R.string.bofo_bed_one) : getContext().getString(R.string.bofo_beds, r.availableBeds()));
        v.meta.setText(String.join(" \u00B7 ", meta));
        v.verified.setVisibility(r.verified() ? View.VISIBLE : View.GONE);
        String url = r.photoUrl();
        Glide.with(v.photo).load(url == null || url.isEmpty() ? (String) null : url)
                .transform(new CenterCrop(), new RoundedCorners(Math.round(10 * dp))).placeholder(R.color.bofo_skeleton).into(v.photo);
        v.getRoot().setOnClickListener(x -> fire(new BofoAnswer.Action("open_detail", r.id(), new ArrayList<>()), a));
        b.chatBox.addView(v.getRoot());
    }

    /** Side-by-side table: first column = row label, then one column per place; the best cell of a row is highlighted. Scrolls sideways if too wide. */
    private void addCompare(LayoutInflater in, BofoAnswer a) {
        ItemBofoCompareBinding v = ItemBofoCompareBinding.inflate(in, b.chatBox, false);
        List<String> ids = a.compare().ids().isEmpty() ? a.resultIds() : a.compare().ids();
        LinearLayout head = new LinearLayout(getContext());
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.addView(cell("", 72, false, false));
        for (String id : ids) { BofoAnswer.Result r = a.result(id); head.addView(cell(r == null ? "" : r.name(), 88, true, false)); }
        v.table.addView(head);
        for (BofoAnswer.Row row : a.compare().rows()) {
            LinearLayout line = new LinearLayout(getContext());
            line.setOrientation(LinearLayout.HORIZONTAL);
            TextView label = cell(row.label(), 72, false, false);
            label.setTextColor(getContext().getColor(R.color.bofo_text_2)); label.setTextSize(11);
            line.addView(label);
            for (int i = 0; i < ids.size(); i++) line.addView(cell(i < row.values().size() ? row.values().get(i) : "", 88, false, i == row.best()));
            v.table.addView(line);
        }
        b.chatBox.addView(v.getRoot());
    }

    private TextView cell(String text, int widthDp, boolean bold, boolean best) {
        TextView t = new TextView(getContext());
        t.setLayoutParams(new LinearLayout.LayoutParams(Math.round(widthDp * dp), ViewGroup.LayoutParams.WRAP_CONTENT));
        t.setText(text);
        t.setTextSize(12);
        t.setPadding(Math.round(6 * dp), Math.round(5 * dp), Math.round(6 * dp), Math.round(5 * dp));
        t.setTextColor(getContext().getColor(best ? R.color.bofo_primary : R.color.bofo_text));
        t.setTypeface(null, bold || best ? Typeface.BOLD : Typeface.NORMAL);
        if (best) t.setBackgroundResource(R.drawable.bg_bofo_best);
        return t;
    }

    /** Action chips (Show on map / Directions / Open / Compare ...) then the suggested questions. */
    private void addChips(LayoutInflater in, BofoAnswer a) {
        ItemBofoChipsBinding v = ItemBofoChipsBinding.inflate(in, b.chatBox, false);
        Set<String> seen = new LinkedHashSet<>();
        for (BofoAnswer.Action act : a.actions()) {
            int label = switch (act.type()) {
                case "show_on_map" -> R.string.bofo_act_map;
                case "directions" -> R.string.bofo_act_directions;
                case "open_detail" -> R.string.bofo_act_open;
                case "apply_filters" -> R.string.bofo_act_filters;
                default -> 0;
            };
            if (label == 0 || !seen.add(getContext().getString(label))) continue;
            chip(v.chips, getContext().getString(label), () -> fire(act, a));
        }
        String cmp = getContext().getString(R.string.bofo_compare_them);
        if (a.results().size() >= 2 && a.compare() == null && seen.add(cmp)) chip(v.chips, getContext().getString(R.string.bofo_act_compare), () -> send(cmp));
        for (String s : a.suggestions()) {
            if (s.isEmpty() || s.equalsIgnoreCase(cmp) || !seen.add(s)) continue;
            chip(v.chips, s, () -> send(s));
        }
        if (v.chips.getChildCount() == 0) return;
        chipsRow = v.getRoot();
        b.chatBox.addView(chipsRow);
    }

    private void chip(ViewGroup row, String text, Runnable onTap) {
        Chip c = Chips.create(row, text, false, false);
        c.setClickable(true);
        c.setOnClickListener(x -> onTap.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMarginEnd(Math.round(6 * dp));
        row.addView(c, lp);
    }

    private void showTyping() {
        ItemMsgTheirsBinding m = ItemMsgTheirsBinding.inflate(LayoutInflater.from(getContext()), b.chatBox, false);
        m.body.setText(R.string.bofo_typing);
        m.body.setTextColor(getContext().getColor(R.color.bofo_muted));
        m.meta.setVisibility(View.GONE);
        typing = m.getRoot();
        b.chatBox.addView(typing);
        b.scroll.post(() -> b.scroll.fullScroll(View.FOCUS_DOWN));
    }

    private void remember(boolean mine, String text, List<String> ids) {
        history.add(new BofoAiService.Turn(mine, text, ids));
        while (history.size() > 6) history.remove(0);
    }

    private static List<String> lastIds(List<BofoAiService.Turn> past) {
        for (int i = past.size() - 1; i >= 0; i--) { List<String> ids = past.get(i).resultIds(); if (ids != null && !ids.isEmpty()) return ids; }
        return new ArrayList<>();
    }

    /** **word** becomes bold text; leftover markdown marks (an unmatched **, backticks) are dropped so no symbols show. */
    private static CharSequence bold(String t) {
        String[] parts = t.replace("`", "").split("\\*\\*", -1);
        android.text.SpannableStringBuilder sb = new android.text.SpannableStringBuilder();
        for (int i = 0; i < parts.length; i++) {
            int start = sb.length();
            sb.append(parts[i]);
            boolean closed = i % 2 == 1 && i < parts.length - 1;       // between a pair of ** **
            if (closed) sb.setSpan(new android.text.style.StyleSpan(Typeface.BOLD), start, sb.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return sb;
    }

    private void say(String text, boolean mine) {
        LayoutInflater in = LayoutInflater.from(getContext());
        View root; TextView body;
        if (mine) { ItemMsgMineBinding m = ItemMsgMineBinding.inflate(in, b.chatBox, false); root = m.getRoot(); body = m.body; m.meta.setVisibility(View.GONE); }
        else { ItemMsgTheirsBinding m = ItemMsgTheirsBinding.inflate(in, b.chatBox, false); root = m.getRoot(); body = m.body; m.meta.setVisibility(View.GONE); }
        body.setText(mine ? text : bold(text));                   // the AI marks names and prices with **...**: show them bold, never the asterisks
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
