package com.bofo.admin;

import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import com.bofo.shared.PermissionRules;

/** "More": the secondary screens a role may open (own activity, all activity, team). */
final class MoreScreen extends FrameLayout {
    MoreScreen(MainActivity a, Access access) {
        super(a);
        LinearLayout l = Ui.col(a); int p = Ui.dp(a, 16); l.setPadding(p, p, p, p);
        l.addView(Ui.text(a, "More", R.style.Text_Bofo_Title));
        l.addView(entry(a, "Two-step sign-in", () -> sub(a, new TwoStepScreen(a, null))));
        if (access.can(PermissionRules.AUDIT_VIEW_OWN)) l.addView(entry(a, "What I did", () -> sub(a, new ActivityScreen(a, false))));
        if (access.can(PermissionRules.AUDIT_VIEW_ALL)) l.addView(entry(a, "All admin activity", () -> sub(a, new ActivityScreen(a, true))));
        if (access.can(PermissionRules.BANNED_VIEW)) l.addView(entry(a, "Banned and suspended", () -> sub(a, new BannedScreen(a, access))));
        if (access.can(PermissionRules.PAYMENTS_VIEW)) l.addView(entry(a, "Payments", () -> sub(a, new PaymentsScreen(a, access))));
        if (access.can(PermissionRules.APPROVALS_DECIDE)) l.addView(entry(a, "Appeals", () -> sub(a, new AppealsScreen(a, access))));
        if (access.can(PermissionRules.AUDIT_VIEW_ALL)) l.addView(entry(a, "Unusual activity", () -> sub(a, new UnusualScreen(a))));
        if (access.can(PermissionRules.AI_ANSWER)) l.addView(entry(a, "Needs attention (AI)", () -> sub(a, new AiUnansweredScreen(a))));
        if (access.can(PermissionRules.AI_LOG_VIEW)) l.addView(entry(a, "AI chats", () -> sub(a, new AiChatLogScreen(a))));
        if (access.can(PermissionRules.AI_SETTINGS)) l.addView(entry(a, "AI settings", () -> sub(a, new AiSettingsScreen(a))));
        if (access.can(PermissionRules.CSV_EXPORT)) l.addView(entry(a, "Export CSV", () -> sub(a, new ExportScreen(a))));
        if (access.can(PermissionRules.MEMBERS_MANAGE)) l.addView(entry(a, "Team", () -> sub(a, new TeamScreen(a))));
        addView(l, new LayoutParams(-1, -1));
    }

    private LinearLayout entry(MainActivity a, String label, Runnable r) {
        LinearLayout c = Ui.card(a); c.addView(Ui.text(a, label, R.style.Text_Bofo_Section)); Ui.asButton(c); c.setOnClickListener(v -> r.run()); return c;
    }

    private void sub(MainActivity a, View v) {
        removeAllViews();
        LinearLayout col = Ui.col(a);
        android.widget.Button back = Ui.button(a, "< Back", false); back.setOnClickListener(x -> a.showMore());
        col.addView(back);
        col.addView(v, new LinearLayout.LayoutParams(-1, 0, 1f));
        addView(col, new LayoutParams(-1, -1));
    }
}
