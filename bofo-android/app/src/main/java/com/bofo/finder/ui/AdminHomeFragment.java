package com.bofo.finder.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.bofo.finder.R;
import com.bofo.finder.databinding.FragmentAdminHomeBinding;
import com.bofo.finder.service.AdminCountsService;
import com.bofo.finder.service.AdminCountsService.Counts;
import com.bofo.finder.service.AdminWeeklyService;
import com.bofo.finder.service.AdminWeeklyService.Weekly;
import com.bofo.finder.util.InsightRules;
import com.bofo.finder.util.AdminHomeRules;
import com.bofo.finder.util.AdminListRules;
import com.bofo.finder.util.Async;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;

/** v2.4.10: Admin > Home. One card per thing that needs the admin, each with a count and (where it makes sense) how long the oldest item has waited. Tap a card to open it. */
public class AdminHomeFragment extends Fragment {

    private FragmentAdminHomeBinding b;
    private int token;
    private View cIds, cReports, cAi, cPay, cSusp, cBan, cDown, cWeek;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        b = FragmentAdminHomeBinding.inflate(inflater, container, false);
        b.homeDate.setText(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).format(LocalDate.now()));
        cIds = card(inflater, R.string.admin_card_ids, v -> openTab(R.id.tab_queue, AdminListRules.WAITING));
        cReports = card(inflater, R.string.admin_card_reports, v -> openTab(R.id.tab_reports, 0));
        cPay = card(inflater, R.string.admin_card_payments, v -> openTab(R.id.tab_admin_payments, AdminListRules.WAITING));
        cAi = card(inflater, R.string.admin_card_ai, v -> go(R.id.aiUnansweredFragment));
        cSusp = card(inflater, R.string.admin_card_suspended, v -> openTab(R.id.tab_reports, 2));      // Reports > Answered: Suspended
        cBan = card(inflater, R.string.admin_card_banned, v -> go(R.id.bannedUsersFragment));
        cDown = card(inflater, R.string.admin_card_takendown, v -> openTab(R.id.tab_reports, 5));       // Reports > Answered: Taken down
        cWeek = card(inflater, R.string.admin_week_title, null);                                    // v2.8.0: last 7 days vs the 7 before (not tappable)
        cWeek.setClickable(false);
        cWeek.setFocusable(false);
        ((TextView) cWeek.findViewById(R.id.card_count)).setText("7d");
        View log = card(inflater, R.string.act_log_title, v -> go(R.id.activityLogFragment));         // v2.4.14: the admin's own recent actions
        ((TextView) log.findViewById(R.id.card_count)).setText("\u203A");
        ((TextView) log.findViewById(R.id.card_sub)).setText(R.string.act_log_sub);
        log.setContentDescription(getString(R.string.act_log_title) + ". " + getString(R.string.act_log_sub));
        View exp = card(inflater, R.string.export_csv, v -> pickExport());                       // v2.9.7: the ONE place to export CSV files
        ((TextView) exp.findViewById(R.id.card_count)).setText("\u203A");
        ((TextView) exp.findViewById(R.id.card_sub)).setText(R.string.export_csv_sub);
        exp.setContentDescription(getString(R.string.export_csv) + ". " + getString(R.string.export_csv_sub));
        b.swipe.setColorSchemeResources(R.color.bofo_primary);
        b.swipe.setOnRefreshListener(this::reload);
        return b.getRoot();
    }

    @Override
    public void onResume() { super.onResume(); reload(); }

    private View card(LayoutInflater inflater, int titleRes, View.OnClickListener click) {
        View v = inflater.inflate(R.layout.item_admin_home_card, b.cards, false);
        ((TextView) v.findViewById(R.id.card_title)).setText(titleRes);
        v.setOnClickListener(click);
        b.cards.addView(v);
        return v;
    }

    /** v2.9.7: Export CSV lives only here. Everything in the table (not the tab's current filter): id, date, reason or amount, status. No names, places or details. */
    private void pickExport() {
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.export_csv)
                .setItems(new CharSequence[]{getString(R.string.tab_reports), getString(R.string.tab_admin_payments)}, (d, which) -> exportCsv(which == 0))
                .show();
    }

    private void exportCsv(boolean reports) {
        Async.run(() -> {
            java.util.List<String[]> out = new java.util.ArrayList<>();
            if (reports) {
                org.json.JSONArray a = com.bofo.finder.service.SupabaseClient.get().select("reports_admin_v", "order=created_at.desc&limit=1000&select=id,reason,status,created_at");
                for (int i = 0; i < a.length(); i++) { org.json.JSONObject x = a.getJSONObject(i); out.add(new String[]{x.optString("id"), InsightRules.dayOf(x.optString("created_at", "")), x.optString("reason"), x.optString("status")}); }
            } else {
                org.json.JSONArray a = new com.bofo.finder.service.BoostService().allPayments();
                for (int i = 0; i < a.length(); i++) { org.json.JSONObject x = a.getJSONObject(i); out.add(new String[]{x.optString("id"), InsightRules.dayOf(x.optString("created_at", "")), String.format(java.util.Locale.ROOT, "%.2f", x.optDouble("amount_php", 0)), x.optString("status")}); }
            }
            return out;
        }, (java.util.List<String[]> out) -> {
            if (b == null || !isAdded()) return;
            if (out.isEmpty()) { ListArea.toast(this, getString(R.string.export_empty)); return; }
            if (reports) CsvShare.share(this, "reports.csv", com.bofo.finder.util.CsvRules.document(new String[]{"id", "date", "reason", "status"}, out));
            else CsvShare.share(this, "payments.csv", com.bofo.finder.util.CsvRules.document(new String[]{"id", "date", "amount_php", "status"}, out));
        }, e -> { if (b != null && isAdded()) ListArea.toast(this, getString(R.string.export_failed)); });
    }

    private void openTab(int id, int filter) {
        if (getParentFragment() instanceof ShellFragment s) s.openAdminTab(id, filter);
    }

    private void go(int dest) {
        NavHostFragment.findNavController(this).navigate(dest);
    }

    private void reload() {
        if (b == null) return;
        final int my = ++token;
        b.swipe.setRefreshing(true);
        Async.run(() -> new AdminWeeklyService().load(), (Weekly w) -> {
            if (my != token || b == null) return;
            showWeek(w);
        }, e -> {
            if (my != token || b == null) return;
            ((TextView) cWeek.findViewById(R.id.card_sub)).setText(R.string.admin_card_unknown);
        });
        Async.run(() -> new AdminCountsService().load(), (Counts c) -> {
            if (my != token || b == null) return;
            b.swipe.setRefreshing(false);
            show(c);
            if (getParentFragment() instanceof ShellFragment s) s.setAdminBadges(c.ids, c.reports);
        }, e -> {
            if (my != token || b == null) return;
            b.swipe.setRefreshing(false);
            b.homeError.setVisibility(View.VISIBLE);
        });
    }

    private void showWeek(Weekly w) {
        int cap = AdminWeeklyService.CAP;
        String s = getString(R.string.admin_week_body,
                InsightRules.countText(w.usersNow, cap), InsightRules.countText(w.usersBefore, cap),
                InsightRules.countText(w.listingsNow, cap), InsightRules.countText(w.listingsBefore, cap),
                InsightRules.countText(w.resolvedNow, cap), InsightRules.countText(w.resolvedBefore, cap));
        ((TextView) cWeek.findViewById(R.id.card_sub)).setText(s);
        cWeek.setContentDescription(getString(R.string.admin_week_title) + ". " + s.replace('\n', '.'));
    }

    private void show(Counts c) {
        bind(cIds, c.ids, waiting(c.idsOldest), 0);
        bind(cReports, c.reports, waiting(c.reportsOldest), 0);
        bind(cPay, c.payments, c.payments > 0 ? waiting(c.paymentsOldest) : null, R.string.admin_card_payments_hint);
        bind(cAi, c.aiWeek, null, R.string.admin_card_ai_hint);
        bind(cSusp, c.suspended, null, R.string.admin_card_now_suspended);
        bind(cBan, c.banned, null, R.string.admin_card_total);
        bind(cDown, c.takenDown, null, R.string.admin_card_total);
        boolean anyUnknown = c.ids < 0 || c.reports < 0 || c.payments < 0 || c.aiWeek < 0 || c.suspended < 0 || c.banned < 0 || c.takenDown < 0;
        b.homeError.setVisibility(anyUnknown ? View.VISIBLE : View.GONE);
        b.homeCaughtUp.setVisibility(AdminHomeRules.allCaughtUp(c.ids, c.reports, c.payments) ? View.VISIBLE : View.GONE);
    }

    /** count < 0 = could not load (shown as "-" with a text, not just a colour). */
    private void bind(View card, int count, @Nullable String waiting, int hintRes) {
        TextView n = card.findViewById(R.id.card_count), sub = card.findViewById(R.id.card_sub);
        n.setText(count < 0 ? "-" : String.valueOf(count));
        n.setTextColor(requireContext().getColor(count > 0 ? R.color.bofo_primary : R.color.bofo_text_2));
        String s;
        if (count < 0) s = getString(R.string.admin_card_unknown);
        else if (waiting != null && count > 0) s = waiting;
        else if (hintRes != 0) s = getString(hintRes);
        else s = getString(R.string.admin_card_none);
        sub.setText(s);
        card.setContentDescription(((TextView) card.findViewById(R.id.card_title)).getText() + ": " + n.getText() + ". " + s);
    }

    @Nullable
    private String waiting(@Nullable String iso) {
        long h = AdminHomeRules.waitingHours(iso, System.currentTimeMillis());
        if (h < 0) return null;
        if (h < 1) return getString(R.string.admin_waiting_lt_hour);
        if (h < 24) return getResources().getQuantityString(R.plurals.admin_waiting_hours, (int) h, (int) h);
        int d = (int) (h / 24);
        return getResources().getQuantityString(R.plurals.admin_waiting_days, d, d);
    }

    @Override
    public void onDestroyView() { token++; b = null; super.onDestroyView(); }
}
