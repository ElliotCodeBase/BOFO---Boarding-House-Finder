package com.bofo.finder.ui;

import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.bofo.finder.R;
import com.bofo.finder.databinding.FragmentAdminListBinding;
import com.bofo.finder.service.SupabaseClient;
import com.bofo.finder.util.AdminRules;
import com.bofo.finder.util.BanRules;
import com.bofo.finder.util.Async;
import com.bofo.finder.util.BoostRules;
import com.bofo.finder.util.Connectivity;
import com.bofo.finder.util.ErrorText;
import com.bofo.finder.util.SuspendRules;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Admin > Reports (v2.1.8): reports sent by users (table reports, read through the admin-only view reports_admin_v), newest first.
 * The title is the status filter (tap it). Tap a row: who reported whom, the place, the reason and details; a waiting report can be
 * dismissed or answered with a suspension of a chosen length (admin_review_report). Take down a place stays available.
 * Chat blocks (blocks_reports) are private to the two users and are no longer listed here.
 */
public class ReportsFragment extends Fragment {

    /** v2.2.2: query part per entry of the array rep_filters (same order). 3 = Banned users: opens that screen instead of filtering. */
    private static final String[] FILTERS = {"status=eq.pending&", "status=in.(reviewed,dismissed,actioned)&", "action=eq.suspend&", "",
            "action=eq.dismiss&", "action=eq.takedown&", ""};
    private static final int BANNED_USERS = 3;
    /** Value of reports.action in the order of the array rep_outcomes. */
    private static final String[] OUTCOMES = {"suspend", "ban", "dismiss", "takedown"};

    private FragmentAdminListBinding b;
    private final List<JSONObject> items = new ArrayList<>();
    private ArrayAdapter<String> rows;
    private int token;
    private int filter = 0;          // index into FILTERS: starts on "pending"
    private boolean busy;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        b = FragmentAdminListBinding.inflate(inflater, container, false);
        b.title.setOnClickListener(v -> pickFilter());
        showTitle();
        rows = new ArrayAdapter<>(requireContext(), android.R.layout.simple_list_item_2, android.R.id.text1, new ArrayList<String>()) {
            @NonNull @Override public View getView(int pos, @Nullable View v, @NonNull ViewGroup p) {
                View r = super.getView(pos, v, p);
                if (pos < 0 || pos >= items.size()) return r;
                JSONObject x = items.get(pos);
                ((TextView) r.findViewById(android.R.id.text1)).setText(ReportDialog.reasonLabel(requireContext(), x.optString("reason")) + ": " + reported(x));
                String bh = x.optString("bh_name", "");
                ((TextView) r.findViewById(android.R.id.text2)).setText(BoostRules.day(x.optString("created_at", ""), ZoneId.systemDefault())
                        + "  -  " + outcomeLabel(x)
                        + (bh.isEmpty() || "null".equals(bh) ? "" : "  -  " + bh));
                return r;
            }
        };
        b.list.setAdapter(rows);
        b.list.setOnItemClickListener((p, v, pos, id) -> { if (pos >= 0 && pos < items.size()) details(items.get(pos)); });
        b.swipe.setColorSchemeResources(R.color.bofo_primary);
        b.swipe.setOnRefreshListener(this::reload);
        return b.getRoot();
    }

    @Override
    public void onResume() { super.onResume(); reload(); }

    private void showTitle() {
        String[] labels = getResources().getStringArray(R.array.rep_filters);
        b.title.setText(getString(R.string.rep_title, labels[Math.min(filter, labels.length - 1)]));
    }

    private void pickFilter() {
        new MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.rep_filter_title)
                .setItems(R.array.rep_filters, (d, which) -> {
                    if (which == BANNED_USERS) { NavHostFragment.findNavController(this).navigate(R.id.bannedUsersFragment); return; }     // v2.2.2: moved here from Profile
                    filter = which; showTitle(); reload();
                }).show();
    }

    /** v2.2.2: how a report was answered (Suspended / Banned / Dismissed / Taken down); falls back to the status for old rows. */
    private String outcomeLabel(JSONObject x) {
        String act = nullToEmpty(x.optString("action", ""));
        String[] labels = getResources().getStringArray(R.array.rep_outcomes);
        for (int i = 0; i < OUTCOMES.length && i < labels.length; i++) if (OUTCOMES[i].equals(act)) return labels[i];
        return statusLabel(x.optString("status"));
    }

    private String statusLabel(String key) {
        int i = SuspendRules.statusIndex(key);
        String[] labels = getResources().getStringArray(R.array.rep_status_labels);
        return i >= 0 && i < labels.length ? labels[i] : key;
    }

    private static String reported(JSONObject x) { return AdminRules.who(nullToEmpty(x.optString("reported_name", ""))); }
    private static String reporter(JSONObject x) { return AdminRules.who(nullToEmpty(x.optString("reporter_name", ""))); }
    private static String nullToEmpty(String s) { return "null".equals(s) ? "" : s; }

    private void reload() {
        if (b == null) return;
        final int my = ++token;
        final String part = FILTERS[filter];
        b.swipe.setRefreshing(true);
        Async.run(() -> SupabaseClient.get().select("reports_admin_v", part
                + "order=created_at.desc&limit=100&select=id,reporter_id,reported_user_id,bh_id,reason,details,status,created_at,reviewed_at,admin_notes,reporter_name,reported_name,bh_name,reported_suspended_until,action"),
                (JSONArray a) -> {
            if (my != token || b == null) return;
            b.swipe.setRefreshing(false);
            items.clear();
            for (int i = 0; i < a.length(); i++) { JSONObject o = a.optJSONObject(i); if (o != null) items.add(o); }
            rows.clear();
            for (int i = 0; i < items.size(); i++) rows.add("");
            rows.notifyDataSetChanged();
            b.message.setText(R.string.rep_empty);
            b.message.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
        }, e -> {
            if (my != token || b == null) return;
            b.swipe.setRefreshing(false);
            ListArea.toast(this, Connectivity.isNetworkError(e) ? getString(R.string.list_offline) : ErrorText.full(getString(R.string.rep_error), e));
        });
    }

    private void details(JSONObject x) {
        ZoneId z = ZoneId.systemDefault();
        String bh = nullToEmpty(x.optString("bh_name", ""));
        String why = nullToEmpty(x.optString("details", ""));
        StringBuilder msg = new StringBuilder(getString(R.string.rep_detail2, reporter(x), reported(x),
                bh.isEmpty() ? getString(R.string.rep_no_place) : bh,
                ReportDialog.reasonLabel(requireContext(), x.optString("reason")),
                why.isEmpty() ? getString(R.string.rep_no_reason) : why,
                SuspendRules.when(x.optString("created_at", ""), z), outcomeLabel(x)));
        String notes = nullToEmpty(x.optString("admin_notes", ""));
        if (!notes.isEmpty()) msg.append(getString(R.string.rep_line_notes, notes));
        String until = x.optString("reported_suspended_until", "");
        if (SuspendRules.active(until, Instant.now())) msg.append(getString(R.string.rep_line_suspended, SuspendRules.when(until, z)));

        final String target = x.optString("reported_user_id");
        MaterialAlertDialogBuilder d = new MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.rep_kind_report).setMessage(msg.toString());
        if ("pending".equals(x.optString("status"))) {
            // v2.1.9: four choices no longer fit three dialog buttons: one "Actions" button opens a list (Suspend, Ban, Dismiss, Take down, Close)
            d.setPositiveButton(R.string.rep_actions_btn, (dd, w) -> actions(x, target)).setNegativeButton(R.string.common_close, null);
        } else {
            d.setPositiveButton(R.string.rep_takedown, (dd, w) -> pickPlace(target, null)).setNegativeButton(R.string.common_close, null);
        }
        d.show();
    }

    /** v2.1.9: the decision list of a waiting report. */
    private void actions(JSONObject x, String target) {
        new MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.rep_actions_title)
                .setItems(R.array.rep_actions, (dd, which) -> {
                    switch (which) {
                        case 0: pickDuration(x); break;
                        case 1: askBanReason(x); break;
                        case 2: dismiss(x); break;
                        case 3: pickPlace(target, x.optString("id")); break;
                        default: break;                                   // Close
                    }
                }).show();
    }

    /* ---------- ban: reason -> plain confirmation -> admin_review_report 'ban' ---------- */
    private void askBanReason(JSONObject x) {
        if (!"pending".equals(x.optString("status"))) return;
        ReasonDialog.ask(this, R.string.rep_ban_title, R.string.rep_suspend_next, true, reason -> confirmBan(x, BanRules.reason(reason)));
    }

    private void confirmBan(JSONObject x, String reason) {
        if (reason == null || reason.isEmpty()) return;
        new MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.rep_ban_title)
                .setMessage(getString(R.string.rep_ban_confirm, reported(x), reason))
                .setNegativeButton(R.string.common_cancel, null)
                .setPositiveButton(R.string.rep_ban, (d, w) -> review(x, "ban", null, reason, null)).show();
    }

    /* ---------- dismiss ---------- */
    private void dismiss(JSONObject x) {
        ReasonDialog.ask(this, R.string.rep_dismiss_title, R.string.rep_dismiss, false, notes -> review(x, "dismiss", null, null, notes));
    }

    /* ---------- suspend: how long -> why -> confirm ---------- */
    private void pickDuration(JSONObject x) {
        new MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.rep_suspend_for)
                .setItems(R.array.rep_durations, (d, which) -> {
                    if (which < SuspendRules.PRESET_DAYS.length) askReason(x, SuspendRules.PRESET_DAYS[which]);
                    else askCustomDays(x);
                }).show();
    }

    private void askCustomDays(JSONObject x) {
        EditText in = new EditText(requireContext());
        in.setInputType(InputType.TYPE_CLASS_NUMBER);
        in.setHint(getString(R.string.rep_custom_hint, SuspendRules.MAX_DAYS));
        FrameLayout box = new FrameLayout(requireContext());
        int m = Math.round(20 * getResources().getDisplayMetrics().density);
        box.setPadding(m, m / 2, m, 0);
        box.addView(in);
        androidx.appcompat.app.AlertDialog d = new MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.rep_custom_title).setView(box)
                .setNegativeButton(R.string.common_cancel, null).setPositiveButton(R.string.rep_suspend_next, null).create();
        d.setOnShowListener(s -> d.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            Integer days = SuspendRules.parseDays(in.getText().toString());
            if (days == null) { in.setError(getString(R.string.rep_custom_bad, SuspendRules.MAX_DAYS)); return; }
            d.dismiss();
            askReason(x, days);
        }));
        d.show();
    }

    private void askReason(JSONObject x, int days) {
        ReasonDialog.ask(this, R.string.rep_suspend_title, R.string.rep_suspend_next, true, reason -> confirmSuspend(x, days, reason));
    }

    private void confirmSuspend(JSONObject x, int days, String reason) {
        if (reason == null || reason.isEmpty()) return;
        new MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.rep_suspend_title)
                .setMessage(getString(R.string.rep_suspend_confirm, reported(x), days, SuspendRules.endsAt(days, Instant.now(), ZoneId.systemDefault()), reason))
                .setNegativeButton(R.string.common_cancel, null)
                .setPositiveButton(R.string.rep_suspend, (d, w) -> review(x, "suspend", SuspendRules.hours(days), reason, null)).show();
    }

    /** One call to admin_review_report; the server checks the admin, the length and that the report is still waiting. */
    private void review(JSONObject x, String act, Integer hrs, String reason, String notes) {
        if (busy) return;
        busy = true;
        final String rid = x.optString("id");
        Async.run(() -> {
            JSONObject a = new JSONObject().put("rid", rid).put("act", act);
            if (hrs != null) a.put("hrs", hrs);
            if (reason != null && !reason.isEmpty()) a.put("reason", reason);
            if (notes != null && !notes.isEmpty()) a.put("notes", notes);
            return SupabaseClient.get().rpc("admin_review_report", a);
        }, r -> {
            busy = false;
            if (b == null) return;
            if ("ban".equals(act)) {
                // v2.1.9: tell the admin plainly when there was no ID number on file (only the account was banned)
                boolean idBanned = r != null && r.optBoolean("id_banned", false);
                ListArea.toast(this, getString(idBanned ? R.string.rep_banned_done : R.string.rep_banned_no_id));
            } else {
                ListArea.toast(this, getString("suspend".equals(act) ? R.string.rep_suspended_done : R.string.rep_dismissed_done));
            }
            reload();
        }, e -> {
            busy = false;
            if (b == null) return;
            ListArea.toast(this, Connectivity.isNetworkError(e) ? getString(R.string.list_offline) : ErrorText.full(getString(R.string.rep_review_error), e));
            reload();
        });
    }

    /* ---------- take down a place (unchanged since 8h) ---------- */
    /** The reported user's places that can still be taken down. */
    /** reportId != null: the take down also answers that waiting report (admin_report_takedown); null: plain admin_takedown. */
    private void pickPlace(String userId, String reportId) {
        if (AdminRules.inList(java.util.Collections.singletonList(userId)).isEmpty()) return;
        Async.run(() -> SupabaseClient.get().select("bh_cards", "owner_id=eq." + userId + "&select=id,name,status&order=created_at.desc"), (JSONArray a) -> {
            if (b == null) return;
            List<String> labels = new ArrayList<>();
            List<String> ids = new ArrayList<>();
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i);
                if (o == null || !AdminRules.canTakeDown(o.optString("status", "active"))) continue;
                labels.add(o.optString("name")); ids.add(o.optString("id"));
            }
            if (ids.isEmpty()) { ListArea.toast(this, getString(R.string.rep_no_places)); return; }
            new MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.rep_pick_place)
                    .setItems(labels.toArray(new String[0]), (d, which) -> takeDown(ids.get(which), labels.get(which), reportId)).show();
        }, e -> ListArea.toast(this, Connectivity.isNetworkError(e) ? getString(R.string.list_offline) : ErrorText.full(getString(R.string.rep_error), e)));
    }

    private void takeDown(String bhId, String bhName, String reportId) {
        ReasonDialog.ask(this, R.string.rep_takedown_title, R.string.rep_takedown_do, true, reason -> {
            if (!AdminRules.takedownOk(reason)) return;
            Async.run(() -> reportId == null
                    ? SupabaseClient.get().rpc("admin_takedown", new JSONObject().put("bh", bhId).put("reason", reason))
                    : SupabaseClient.get().rpc("admin_report_takedown", new JSONObject().put("rid", reportId).put("bh", bhId).put("reason", reason)), r -> {
                        ListArea.toast(this, getString(R.string.rep_done, bhName));
                        reload();                                   // the report moves to Answered: Taken down
                    },
                    e -> ListArea.toast(this, Connectivity.isNetworkError(e) ? getString(R.string.list_offline) : ErrorText.full(getString(R.string.rep_takedown_error), e)));
        });
    }

    @Override
    public void onDestroyView() { token++; b = null; super.onDestroyView(); }
}
