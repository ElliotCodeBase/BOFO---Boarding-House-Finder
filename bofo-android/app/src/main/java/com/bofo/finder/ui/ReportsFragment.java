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
import com.bofo.finder.util.AdminListRules;
import com.bofo.finder.util.AdminRules;
import com.bofo.finder.util.BanRules;
import com.bofo.finder.util.Async;
import com.bofo.finder.util.BoostRules;
import com.bofo.finder.util.Connectivity;
import com.bofo.finder.util.ErrorText;
import com.bofo.finder.util.CsvRules;
import com.bofo.finder.util.InsightRules;
import com.bofo.finder.util.AdminErrorRules;
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
    private final List<JSONObject> all = new ArrayList<>(), items = new ArrayList<>();
    private final java.util.Map<String, List<JSONObject>> groupOf = new java.util.HashMap<>();     // v2.4.13: waiting reports about the same person + place share one row
    private final java.util.Map<String, String> photos = new java.util.HashMap<>();
    private AdminRowAdapter rows;
    private AdminListChrome chrome;
    private int token;
    private int filter = 0;          // index into FILTERS: starts on "pending"
    private boolean busy;
    private String person, personName = "";      // v2.8.0: set = only the reports about this person (title shows it; picking a filter from the title clears it)

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        b = FragmentAdminListBinding.inflate(inflater, container, false);
        b.title.setOnClickListener(v -> pickFilter());
        b.title.setMinHeight(Math.round(48 * getResources().getDisplayMetrics().density));
        b.title.setContentDescription(getString(R.string.rep_more_filters));
        if (getArguments() != null) filter = Math.max(0, Math.min(FILTERS.length - 1, getArguments().getInt("filter", 0)));     // Admin Home preselects
        showTitle();
        rows = new AdminRowAdapter(requireContext());
        b.list.setAdapter(rows);
        chrome = new AdminListChrome(b, AdminListRules.reportsChip(filter), new AdminListChrome.Host() {
            @Override public void onFilter(int chip) { filter = AdminListRules.reportsFilter(chip); showTitle(); reload(); }
            @Override public void onSearch(String query) { applyFilter(); }
            @Override public void onRetry() { reload(); }
        });
        b.list.setOnItemClickListener((p, v, pos, id) -> { if (pos >= 0 && pos < items.size()) details(items.get(pos)); });
        b.list.setOnItemLongClickListener((p, v, pos, id) -> { if (pos >= 0 && pos < items.size()) quick(items.get(pos)); return true; });
        b.swipe.setColorSchemeResources(R.color.bofo_primary);
        b.swipe.setOnRefreshListener(this::reload);
        return b.getRoot();
    }

    @Override
    public void onResume() { super.onResume(); reload(); }

    private void showTitle() {
        String[] labels = getResources().getStringArray(R.array.rep_filters);
        b.title.setText((person != null ? getString(R.string.rep_person_title, AdminRules.who(personName))
                : getString(R.string.rep_title, labels[Math.min(filter, labels.length - 1)])) + " \u25BE");
    }

    private void pickFilter() {
        new MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.rep_filter_title)
                .setItems(R.array.rep_filters, (d, which) -> {
                    if (which == BANNED_USERS) { NavHostFragment.findNavController(this).navigate(R.id.bannedUsersFragment); return; }     // v2.2.2: moved here from Profile
                    person = null; filter = which; showTitle(); chrome.setChip(AdminListRules.reportsChip(which)); reload();
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
        final String part = (person == null ? "" : "reported_user_id=eq." + person + "&") + FILTERS[filter];
        final String order = filter == 0 ? "order=created_at.asc&limit=200" : "order=created_at.desc&limit=100";      // waiting = oldest first
        chrome.loading(!items.isEmpty());
        Async.run(() -> {
            JSONArray a = SupabaseClient.get().select("reports_admin_v", part + order + "&select=id,reporter_id,reported_user_id,bh_id,reason,details,status,created_at,reviewed_at,admin_notes,reporter_name,reported_name,bh_name,reported_suspended_until,action");
            List<JSONObject> l = new ArrayList<>();
            List<String> bhIds = new ArrayList<>();
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i);
                if (o == null) continue;
                l.add(o);
                String id = nullToEmpty(o.optString("bh_id", ""));
                if (!id.isEmpty()) bhIds.add(id);
            }
            java.util.Map<String, String> ph = new java.util.HashMap<>();
            String in = AdminRules.inList(bhIds);
            if (!in.isEmpty()) {
                try {                                          // the listing photo is a bonus: a failure still shows the reports
                    JSONArray c = SupabaseClient.get().select("bh_cards", "id=in.(" + in + ")&select=id,photo_url");
                    for (int i = 0; i < c.length(); i++) ph.put(c.getJSONObject(i).getString("id"), c.getJSONObject(i).optString("photo_url", ""));
                } catch (Exception ignored) { /* rows without a picture */ }
            }
            return new Object[]{l, ph};
        }, (Object[] r) -> {
            if (my != token || b == null) return;
            @SuppressWarnings("unchecked") List<JSONObject> l = (List<JSONObject>) r[0];
            @SuppressWarnings("unchecked") java.util.Map<String, String> ph = (java.util.Map<String, String>) r[1];
            all.clear(); all.addAll(l);
            photos.clear(); photos.putAll(ph);
            applyFilter();
        }, e -> {
            if (my != token || b == null) return;
            chrome.failed(Connectivity.isNetworkError(e), !items.isEmpty());
        });
    }

    private boolean matchesQuery(JSONObject x) {
        return AdminListRules.matches(chrome.query(), reported(x), reporter(x), nullToEmpty(x.optString("bh_name", "")),
                ReportDialog.reasonLabel(requireContext(), x.optString("reason")), nullToEmpty(x.optString("details", "")));
    }

    /** v2.8.0: show only the reports about one person (all statuses). */
    private void showPerson(String userId, String name) {
        person = userId; personName = name; filter = 6;
        chrome.setChip(AdminListRules.ALL);
        showTitle();
        reload();
    }

    /** Search, then group the waiting reports about the same person + place into one row; show the place (name + photo) and the start of the details inline. */
    private void applyFilter() {
        if (b == null) return;
        items.clear(); groupOf.clear();
        List<JSONObject> match = new ArrayList<>();
        for (JSONObject x : all) {
            if (matchesQuery(x)) match.add(x);
        }
        List<List<JSONObject>> groups = AdminListRules.groupBy(match, x -> "pending".equals(x.optString("status"))
                ? "g|" + x.optString("reported_user_id") + "|" + nullToEmpty(x.optString("bh_id", "")) : "r|" + x.optString("id"));
        List<AdminRowAdapter.Row> out = new ArrayList<>();
        for (List<JSONObject> g : groups) {
            JSONObject x = g.get(0);
            items.add(x); groupOf.put(x.optString("id"), g);
            boolean pending = "pending".equals(x.optString("status"));
            String title = ReportDialog.reasonLabel(requireContext(), x.optString("reason")) + ": " + reported(x)
                    + (g.size() > 1 ? "  (" + getResources().getQuantityString(R.plurals.admin_report_count, g.size(), g.size()) + ")" : "");
            String bh = nullToEmpty(x.optString("bh_name", ""));
            String why = nullToEmpty(x.optString("details", ""));
            if (why.length() > 70) why = why.substring(0, 70) + "...";
            StringBuilder sub = new StringBuilder(pending ? AdminRowAdapter.waiting(requireContext(), x.optString("created_at", ""))
                    : BoostRules.day(x.optString("created_at", ""), ZoneId.systemDefault()));
            if (!bh.isEmpty()) sub.append(" - ").append(bh);
            if (!why.isEmpty()) sub.append(" - ").append(why);
            String act = nullToEmpty(x.optString("action", ""));
            int tone = pending ? AdminRowAdapter.WAITING : ("suspend".equals(act) || "ban".equals(act) || "takedown".equals(act)) ? AdminRowAdapter.BAD
                    : "dismiss".equals(act) || "reviewed".equals(x.optString("status")) ? AdminRowAdapter.NEUTRAL : AdminRowAdapter.OK;
            out.add(new AdminRowAdapter.Row(title, sub.toString(), pending ? getString(R.string.admin_st_waiting) : outcomeLabel(x), tone,
                    photos.get(nullToEmpty(x.optString("bh_id", "")))));
        }
        rows.set(out);
        chrome.loaded(!items.isEmpty(), filter == 0 ? R.string.admin_empty_waiting : R.string.rep_empty);
    }

    /** After a suspend / ban / dismiss: the other waiting reports about the same person + place are marked handled too, so the row really goes away. */
    private void afterReview(JSONObject x, String act) {
        List<JSONObject> g = groupOf.get(x.optString("id"));
        if (g == null || g.size() < 2 || !("dismiss".equals(act) || "suspend".equals(act) || "ban".equals(act))) { reload(); return; }
        final String sibling = "dismiss".equals(act) ? "dismiss" : "reviewed";
        Async.run(() -> {
            int n = 0;
            for (JSONObject o : g) {
                if (o == x) continue;
                try { SupabaseClient.get().rpc("admin_review_report", new JSONObject().put("rid", o.optString("id")).put("act", sibling)); n++; }
                catch (Exception ignored) { /* stays waiting: the list shows it again */ }
            }
            return n;
        }, n -> {
            if (b == null) return;
            if (n > 0) ListArea.toast(this, getString(R.string.admin_group_handled, n));
            reload();
        }, e -> reload());
    }

    /** v2.4.12: long-press a row: Open / Dismiss / Actions (waiting reports); plain tap still opens the details. */
    private void quick(JSONObject x) {
        boolean pending = "pending".equals(x.optString("status"));
        String[] labels = pending ? new String[]{getString(R.string.act_open), getString(R.string.rep_dismiss), getString(R.string.rep_actions_btn)}
                                  : new String[]{getString(R.string.act_open)};
        new MaterialAlertDialogBuilder(requireContext()).setTitle(reported(x)).setItems(labels, (d, which) -> {
            if (which == 0) details(x);
            else if (which == 1) dismiss(x);
            else actions(x, x.optString("reported_user_id"));
        }).show();
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

        List<JSONObject> grp = groupOf.get(x.optString("id"));
        if (grp != null && grp.size() > 1) {
            msg.append("\n\n").append(getString(R.string.admin_group_lines, grp.size() - 1));
            for (JSONObject o : grp) {
                if (o == x) continue;
                msg.append('\n').append(getString(R.string.admin_group_line, ReportDialog.reasonLabel(requireContext(), o.optString("reason")), reporter(o), SuspendRules.when(o.optString("created_at", ""), z)));
            }
        }
        final String target = x.optString("reported_user_id");
        final String base = msg.toString();
        MaterialAlertDialogBuilder d = new MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.rep_kind_report).setMessage(base);
        if (InsightRules.isUuid(target) && !target.equals(person)) d.setNeutralButton(R.string.rep_person_btn, (dd, w) -> showPerson(target, reported(x)));
        if ("pending".equals(x.optString("status"))) {
            // v2.1.9: four choices no longer fit three dialog buttons: one "Actions" button opens a list (Suspend, Ban, Dismiss, Take down, Close)
            d.setPositiveButton(R.string.rep_actions_btn, (dd, w) -> actions(x, target)).setNegativeButton(R.string.common_close, null);
        } else {
            d.setPositiveButton(R.string.rep_takedown, (dd, w) -> pickPlace(target, null)).setNegativeButton(R.string.common_close, null);
        }
        final androidx.appcompat.app.AlertDialog dlg = d.show();
        if (InsightRules.isUuid(target)) personCount(dlg, base, target, "pending".equals(x.optString("status")));
    }

    /** v2.8.0: adds "N other waiting reports about this person" to the open details dialog once the count is read (nothing is added when it fails or is 0). */
    private void personCount(androidx.appcompat.app.AlertDialog dlg, String base, String userId, boolean thisWaiting) {
        Async.run(() -> SupabaseClient.get().select("reports_admin_v", "reported_user_id=eq." + userId + "&status=eq.pending&select=id&limit=200").length(), (Integer n) -> {
            if (b == null || !dlg.isShowing()) return;
            int more = InsightRules.otherWaiting(n, thisWaiting);
            if (more > 0) dlg.setMessage(base + "\n\n" + getResources().getQuantityString(R.plurals.rep_person_more, more, more));
        }, e -> { /* the count is a bonus */ });
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
                final String uid = x.optString("reported_user_id");
                ListArea.undo(this, getString(idBanned ? R.string.rep_banned_done : R.string.rep_banned_no_id), () ->       // v2.4.12: the server has admin_unban, so a ban can be undone
                        Async.run(() -> { new com.bofo.finder.service.BannedUsersService().unban(uid, null); return Boolean.TRUE; },
                                ok -> { ListArea.toast(this, getString(R.string.act_ban_undone)); reload(); },
                                e -> ListArea.toast(this, Connectivity.isNetworkError(e) ? getString(R.string.list_offline) : AdminErrorRules.plain(getString(R.string.ban_unban_error), e))));
            } else {
                ListArea.toast(this, getString("suspend".equals(act) ? R.string.rep_suspended_done : R.string.rep_dismissed_done));
            }
            afterReview(x, act);
        }, e -> {
            busy = false;
            if (b == null) return;
            ListArea.toast(this, Connectivity.isNetworkError(e) ? getString(R.string.list_offline) : AdminErrorRules.plain(getString(R.string.rep_review_error), e));
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
        }, e -> ListArea.toast(this, Connectivity.isNetworkError(e) ? getString(R.string.list_offline) : AdminErrorRules.plain(getString(R.string.rep_error), e)));
    }

    /** v2.4.12: reason, then ONE plain confirmation that names the place (taking a place down cannot be undone from the app). */
    private void takeDown(String bhId, String bhName, String reportId) {
        ReasonDialog.ask(this, R.string.rep_takedown_title, R.string.rep_takedown_do, true, reason -> {
            if (!AdminRules.takedownOk(reason)) return;
            new MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.rep_takedown_title)
                    .setMessage(getString(R.string.act_takedown_confirm, bhName, reason))
                    .setNegativeButton(R.string.common_cancel, null)
                    .setPositiveButton(R.string.rep_takedown_do, (d, w) -> runTakeDown(bhId, bhName, reportId, reason)).show();
        });
    }

    private void runTakeDown(String bhId, String bhName, String reportId, String reason) {
        Async.run(() -> reportId == null
                ? SupabaseClient.get().rpc("admin_takedown", new JSONObject().put("bh", bhId).put("reason", reason))
                : SupabaseClient.get().rpc("admin_report_takedown", new JSONObject().put("rid", reportId).put("bh", bhId).put("reason", reason)), r -> {
                    ListArea.toast(this, getString(R.string.rep_done, bhName));
                    reload();                                   // the report moves to Answered: Taken down
                },
                e -> ListArea.toast(this, Connectivity.isNetworkError(e) ? getString(R.string.list_offline) : AdminErrorRules.plain(getString(R.string.rep_takedown_error), e)));
    }

    @Override
    public void onDestroyView() { token++; if (chrome != null) chrome.destroy(); chrome = null; b = null; super.onDestroyView(); }
}
