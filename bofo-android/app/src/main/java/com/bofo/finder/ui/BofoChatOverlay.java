package com.bofo.finder.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.graphics.Typeface;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
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
import java.util.Collections;
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
        if (!greeted) { greeted = true; say(getContext().getString(R.string.bofo_hello), false); addStarters(); }
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
        int cw = Math.round(Math.min(400 * dp, w - 20 * dp));
        int bm = bottomOffset; if (shown && MainActivity.imeBottom > 0) { int[] loc = new int[2]; getLocationInWindow(loc); int over = loc[1] + h - (getRootView().getHeight() - MainActivity.imeBottom); if (over > 0) bm = Math.max(bm, over + Math.round(8 * dp)); } int ch = Math.round(Math.max(260 * dp, Math.min(620 * dp, h - bm - 24 * dp)));
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
        removeCallbacks(dotsTick); typingBody = null;
        if (typing != null) { b.chatBox.removeView(typing); typing = null; }
    }

    private void reply(String text) { say(text, false); remember(false, text, null); }

    private void show(BofoAnswer a) {
        finish();
        String text = a.reply().isEmpty() ? BofoAiService.faq("") : a.reply();
        say(text, false);
        LayoutInflater in = LayoutInflater.from(getContext());
        if (!a.results().isEmpty()) addResults(in, a);
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

    /** Small fade + slide-up when a piece of an answer appears (i = position, so the pieces come one after the other). */
    private void pop(View v, int i) {
        v.setAlpha(0f); v.setTranslationY(10 * dp);
        v.animate().alpha(1f).translationY(0f).setStartDelay(Math.min(i, 5) * 70L).setDuration(220).start();
    }

    private static boolean fromMe(BofoAnswer a) {
        for (BofoAnswer.Action x : a.actions()) if ("directions".equals(x.type()) && x.ids() != null && x.ids().contains("me")) return true;
        return false;
    }

    /** The listings as cards in a row you swipe sideways (one result = one full-width card). */
    private void addResults(LayoutInflater in, BofoAnswer a) {
        List<BofoAnswer.Result> rs = a.results();
        HorizontalScrollView hs = new HorizontalScrollView(getContext());
        hs.setHorizontalScrollBarEnabled(false);
        hs.setOverScrollMode(OVER_SCROLL_NEVER);
        hs.setClipToPadding(false);
        hs.setPadding(Math.round(12 * dp), 0, Math.round(4 * dp), 0);
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        hs.addView(row, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        int cardPx = b.card.getWidth() > 0 ? b.card.getWidth() : Math.round(320 * dp);
        int w = rs.size() == 1 ? Math.max(Math.round(236 * dp), cardPx - Math.round(28 * dp) - Math.round(16 * dp)) : Math.round(236 * dp);
        for (int i = 0; i < rs.size(); i++) row.addView(resultCard(in, row, rs.get(i), i, rs.size(), w, a));
        b.chatBox.addView(hs);
        pop(hs, 0);
    }

    private View resultCard(LayoutInflater in, ViewGroup row, BofoAnswer.Result r, int i, int n, int w, BofoAnswer a) {
        Context c = getContext();
        ItemBofoResultBinding v = ItemBofoResultBinding.inflate(in, row, false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, ViewGroup.LayoutParams.MATCH_PARENT);
        lp.setMarginEnd(Math.round(8 * dp)); lp.topMargin = Math.round(4 * dp); lp.bottomMargin = Math.round(4 * dp);
        v.getRoot().setLayoutParams(lp);

        v.name.setText(r.name());
        v.price.setText(Money.perMonth(r.price()));

        // rank badge on the photo: what the question asked for on the first card, then #2, #3 ...
        String badge = null;
        if (i == 0 && "nearest".equals(a.intent())) badge = c.getString(R.string.bofo_badge_nearest);
        else if (i == 0 && "cheapest".equals(a.intent())) badge = c.getString(R.string.bofo_badge_cheapest);
        else if (i == 0 && "best_rated".equals(a.intent())) badge = c.getString(R.string.bofo_badge_top);
        else if (n > 1) badge = "#" + (i + 1);
        v.badge.setVisibility(badge == null ? View.GONE : View.VISIBLE);
        if (badge != null) v.badge.setText(badge);
        v.verified.setVisibility(r.verified() ? View.VISIBLE : View.GONE);

        if (r.distanceKm() != null) { v.chipDist.setText(Geo.walkText(r.distanceKm())); v.chipDist.setVisibility(View.VISIBLE); }
        else v.chipDist.setVisibility(View.GONE);
        if (r.reviewCount() > 0) {
            v.chipRating.setText("\u2605 " + ListRules.ratingText(r.rating(), r.reviewCount()));
            v.chipRating.setBackgroundResource(R.drawable.bg_bofo_pill_amber);
        } else {
            v.chipRating.setText(R.string.bofo_no_reviews);
            v.chipRating.setBackgroundResource(R.drawable.bg_bofo_pill);
            v.chipRating.setTextColor(c.getColor(R.color.bofo_text_2));
        }
        v.chipBeds.setText(r.availableBeds() == 1 ? c.getString(R.string.bofo_bed_one) : c.getString(R.string.bofo_beds, r.availableBeds()));

        List<String> free = new ArrayList<>();
        if (r.water()) free.add(c.getString(R.string.bofo_amen_water));
        if (r.electricity()) free.add(c.getString(R.string.bofo_amen_power));
        if (r.wifi()) free.add(c.getString(R.string.bofo_amen_wifi));
        v.amen.setText(String.join("  ", free));
        v.amen.setVisibility(free.isEmpty() ? View.GONE : View.VISIBLE);

        String addr = r.address() == null ? "" : r.address().trim();
        v.addr.setText(addr);
        v.addr.setVisibility(addr.isEmpty() ? View.GONE : View.VISIBLE);

        String url = r.photoUrl();
        Glide.with(v.photo).load(url == null || url.isEmpty() ? (String) null : url).centerCrop().placeholder(R.color.bofo_skeleton).into(v.photo);

        v.getRoot().setOnClickListener(x -> fire(new BofoAnswer.Action("open_detail", r.id(), new ArrayList<>()), a));
        v.btnMap.setOnClickListener(x -> fire(new BofoAnswer.Action("show_on_map", r.id(), new ArrayList<>(Collections.singletonList(r.id()))), a));
        v.btnRoute.setOnClickListener(x -> fire(new BofoAnswer.Action("directions", r.id(),
                fromMe(a) ? new ArrayList<>(Collections.singletonList("me")) : new ArrayList<String>()), a));
        return v.getRoot();
    }

    /** Side-by-side table: tinted header with the place names (tap = open the place), one column per place that share the width,
     *  alternate row shading, the best cell of a row in green with a check mark, and a line saying which place wins most rows. */
    private void addCompare(LayoutInflater in, BofoAnswer a) {
        ItemBofoCompareBinding v = ItemBofoCompareBinding.inflate(in, b.chatBox, false);
        List<String> ids = a.compare().ids().isEmpty() ? a.resultIds() : a.compare().ids();
        int n = Math.max(1, ids.size());
        int labelPx = Math.round(84 * dp);
        int cardPx = b.card.getWidth() > 0 ? b.card.getWidth() : Math.round(320 * dp);
        int avail = cardPx - Math.round(28 * dp) - Math.round(24 * dp);                    // card padding + the table's own side margins
        int colPx = Math.max(Math.round(96 * dp), (avail - labelPx) / n);

        LinearLayout head = new LinearLayout(getContext());
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setBackgroundResource(R.drawable.bg_bofo_table_head);
        head.addView(cell("", labelPx, false, false, false));
        for (String id : ids) {
            BofoAnswer.Result r = a.result(id);
            TextView h = cell(r == null ? "" : r.name(), colPx, true, false, true);
            h.setTextColor(getContext().getColor(R.color.bofo_primary_dark));
            h.setMaxLines(2); h.setEllipsize(android.text.TextUtils.TruncateAt.END);
            if (r != null) {
                h.setBackgroundResource(android.R.drawable.list_selector_background);
                h.setClickable(true);
                h.setOnClickListener(x -> fire(new BofoAnswer.Action("open_detail", r.id(), new ArrayList<>()), a));
            }
            head.addView(h);
        }
        v.table.addView(head);

        int[] wins = new int[n];
        int scored = 0, shade = 0;
        for (BofoAnswer.Row row : a.compare().rows()) {
            LinearLayout line = new LinearLayout(getContext());
            line.setOrientation(LinearLayout.HORIZONTAL);
            line.setMinimumHeight(Math.round(38 * dp));
            line.setGravity(Gravity.CENTER_VERTICAL);
            if (shade++ % 2 == 1) line.setBackgroundResource(R.drawable.bg_bofo_row_alt);
            TextView label = cell(row.label(), labelPx, true, false, false);
            label.setTextColor(getContext().getColor(R.color.bofo_text_2)); label.setTextSize(11);
            line.addView(label);
            for (int i = 0; i < ids.size(); i++) {
                boolean best = i == row.best();
                String val = i < row.values().size() ? row.values().get(i) : "";
                line.addView(cell(best ? "\u2713 " + val : val, colPx, false, best, true));
            }
            if (row.best() >= 0 && row.best() < n) { wins[row.best()]++; scored++; }
            v.table.addView(line);
        }

        // who wins the most rows (only when there is a clear leader)
        int top = 0, leader = -1;
        for (int i = 0; i < n; i++) if (wins[i] > top) { top = wins[i]; leader = i; }
        int ties = 0;
        for (int i = 0; i < n; i++) if (wins[i] == top) ties++;
        BofoAnswer.Result lead = leader >= 0 && leader < ids.size() ? a.result(ids.get(leader)) : null;
        if (lead != null && top > 0 && ties == 1) {
            TextView t = new TextView(getContext());
            t.setText(getContext().getString(R.string.bofo_wins, lead.name(), top, scored));
            t.setTextSize(12); t.setTypeface(null, Typeface.BOLD);
            t.setTextColor(getContext().getColor(R.color.bofo_success));
            t.setBackgroundResource(R.drawable.bg_bofo_pill_green);
            t.setPadding(Math.round(10 * dp), Math.round(6 * dp), Math.round(10 * dp), Math.round(6 * dp));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(Math.round(8 * dp), Math.round(6 * dp), Math.round(8 * dp), Math.round(8 * dp));
            v.table.addView(t, lp);
        }
        b.chatBox.addView(v.getRoot());
        pop(v.getRoot(), 0);
    }

    private TextView cell(String text, int widthPx, boolean bold, boolean best, boolean center) {
        TextView t = new TextView(getContext());
        t.setLayoutParams(new LinearLayout.LayoutParams(widthPx, ViewGroup.LayoutParams.WRAP_CONTENT));
        t.setText(text);
        t.setTextSize(12);
        t.setGravity(center ? Gravity.CENTER : Gravity.CENTER_VERTICAL | Gravity.START);
        t.setPadding(Math.round(6 * dp), Math.round(7 * dp), Math.round(6 * dp), Math.round(7 * dp));
        t.setTextColor(getContext().getColor(best ? R.color.bofo_success : R.color.bofo_text));
        t.setTypeface(null, bold || best ? Typeface.BOLD : Typeface.NORMAL);
        if (best) t.setBackgroundResource(R.drawable.bg_bofo_best_green);
        return t;
    }

    /** First chips of an empty chat: the questions people ask most. */
    private void addStarters() {
        ItemBofoChipsBinding v = ItemBofoChipsBinding.inflate(LayoutInflater.from(getContext()), b.chatBox, false);
        int[] ids = { R.string.bofo_start_nearest, R.string.bofo_start_cheapest, R.string.bofo_start_rated, R.string.bofo_start_all };
        for (int id : ids) { final String q = getContext().getString(id); chip(v.chips, q, () -> send(q)); }
        chipsRow = v.getRoot();                                    // send() removes it as soon as the user goes on
        b.chatBox.addView(chipsRow);
        pop(chipsRow, 1);
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
        pop(chipsRow, 3);
    }

    private void chip(ViewGroup row, String text, Runnable onTap) {
        Chip c = Chips.create(row, text, false, false);
        c.setClickable(true);
        c.setOnClickListener(x -> onTap.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMarginEnd(Math.round(6 * dp));
        row.addView(c, lp);
    }

    private static final String[] DOTS = { "\u25CF  \u25CB  \u25CB", "\u25CB  \u25CF  \u25CB", "\u25CB  \u25CB  \u25CF" };
    private TextView typingBody;
    private int dotStep;
    private final Runnable dotsTick = new Runnable() {
        @Override public void run() {
            if (typingBody == null) return;
            dotStep = (dotStep + 1) % DOTS.length;
            typingBody.setText(DOTS[dotStep]);
            postDelayed(this, 380);
        }
    };

    private void showTyping() {
        ItemMsgTheirsBinding m = ItemMsgTheirsBinding.inflate(LayoutInflater.from(getContext()), b.chatBox, false);
        m.body.setText(DOTS[0]);
        m.body.setTextColor(getContext().getColor(R.color.bofo_primary));
        m.body.setTextSize(13);
        m.body.setContentDescription(getContext().getString(R.string.bofo_typing));
        m.meta.setVisibility(View.GONE);
        typingBody = m.body; dotStep = 0;
        removeCallbacks(dotsTick); postDelayed(dotsTick, 380);
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
        pop(root, 0);
        b.scroll.post(() -> b.scroll.fullScroll(View.FOCUS_DOWN));
    }

    @Override
    protected void onDetachedFromWindow() {
        token++;                                                  // a late answer must not touch a dead screen
        removeCallbacks(dotsTick);
        waiting = false;
        MainActivity.keepAppStill = false; MainActivity.imeListener = null; super.onDetachedFromWindow();
    }
}
