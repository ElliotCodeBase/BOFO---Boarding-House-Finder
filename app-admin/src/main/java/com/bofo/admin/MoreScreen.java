package com.bofo.admin;

import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.bofo.shared.PermissionRules;

/** "More": the secondary screens a role may open, grouped, each with an icon and one line of help. Scrolls (the last rows used to be cut off). */
final class MoreScreen extends FrameLayout {
    private final MainActivity act;
    private final LinearLayout l;
    private String pending;                  // group heading waiting for its first visible row (a group with no rows shows no heading)
    private boolean inSub;

    MoreScreen(MainActivity a, Access access) {
        super(a);
        act = a;
        l = Ui.col(a); int p = Ui.dp(a, 16); l.setPadding(p, p, p, p);
        l.addView(Ui.text(a, "More", R.style.Text_Bofo_Title));

        group("Your account");
        row(true, R.drawable.ic_more_lock, "Two-step sign-in", "Protect your account with a code", () -> new TwoStepScreen(a, null));
        row(access.can(PermissionRules.AUDIT_VIEW_OWN), R.drawable.ic_more_history, "What I did", "Your own recent actions", () -> new ActivityScreen(a, false));

        group("Moderation");
        row(access.can(PermissionRules.BANNED_VIEW), R.drawable.ic_more_block, "Banned and suspended", "People who are blocked right now", () -> new BannedScreen(a, access));
        row(access.can(PermissionRules.APPROVALS_DECIDE), R.drawable.ic_more_gavel, "Appeals", "People asking us to look again", () -> new AppealsScreen(a, access));
        row(access.can(PermissionRules.AUDIT_VIEW_ALL), R.drawable.ic_more_warning, "Unusual activity", "Odd patterns worth a look", () -> new UnusualScreen(a));
        row(access.can(PermissionRules.AUDIT_VIEW_ALL), R.drawable.ic_more_history, "All admin activity", "Everything the team did", () -> new ActivityScreen(a, true));

        group("Money");
        row(access.can(PermissionRules.PAYMENTS_VIEW), R.drawable.ic_more_payments, "Payments", "Payments and refunds", () -> new PaymentsScreen(a, access));

        group("AI");
        row(access.can(PermissionRules.AI_ANSWER), R.drawable.ic_more_warning, "Needs attention (AI)", "Questions the AI could not answer", () -> new AiUnansweredScreen(a));
        row(access.can(PermissionRules.AI_LOG_VIEW), R.drawable.ic_more_chat, "AI chats", "Read the AI conversations", () -> new AiChatLogScreen(a));
        row(access.can(PermissionRules.AI_SETTINGS), R.drawable.ic_more_tune, "AI settings", "Turn AI features on or off", () -> new AiSettingsScreen(a));

        group("Team and data");
        row(access.can(PermissionRules.MEMBERS_MANAGE), R.drawable.ic_more_group, "Team", "Invite people and set their roles", () -> new TeamScreen(a));
        row(access.can(PermissionRules.CSV_EXPORT), R.drawable.ic_more_download, "Export CSV", "Save data as a spreadsheet file", () -> new ExportScreen(a));

        TextView ver = Ui.text(a, "BOFO Admin v" + BuildConfig.VERSION_NAME, R.style.Text_Bofo_Caption);
        ver.setGravity(Gravity.CENTER); ver.setPadding(0, Ui.dp(a, 12), 0, Ui.dp(a, 8));
        l.addView(ver, new LinearLayout.LayoutParams(-1, -2));
        addView(MainActivity.scroll(a, l), new LayoutParams(-1, -1));
    }

    private void group(String title) { pending = title; }

    private interface Make { View build(); }

    private void row(boolean allowed, int icon, String title, String hint, Make make) {
        if (!allowed) return;
        if (pending != null) {
            TextView h = Ui.text(act, pending.toUpperCase(), R.style.Text_Bofo_Caption);
            h.setTextColor(act.getColor(R.color.bofo_muted)); h.setLetterSpacing(0.08f); h.setPadding(Ui.dp(act, 4), Ui.dp(act, 14), 0, Ui.dp(act, 6));
            androidx.core.view.ViewCompat.setAccessibilityHeading(h, true);
            l.addView(h); pending = null;
        }
        LinearLayout card = Ui.card(act);
        LinearLayout r = Ui.row(act);
        ImageView tile = Ui.icon(act, icon, R.color.bofo_primary);
        GradientDrawable g = new GradientDrawable(); g.setColor(act.getColor(R.color.bofo_primary_tint)); g.setCornerRadius(Ui.dp(act, 12));
        tile.setBackground(g); int tp = Ui.dp(act, 9); tile.setPadding(tp, tp, tp, tp);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(Ui.dp(act, 42), Ui.dp(act, 42)); tl.setMarginEnd(Ui.dp(act, 14));
        r.addView(tile, tl);
        LinearLayout words = Ui.col(act);
        words.addView(Ui.text(act, title, R.style.Text_Bofo_Section));
        words.addView(Ui.text(act, hint, R.style.Text_Bofo_Caption));
        r.addView(words, new LinearLayout.LayoutParams(0, -2, 1f));
        ImageView go = Ui.icon(act, R.drawable.ic_chevron_right, R.color.bofo_muted);
        r.addView(go, new LinearLayout.LayoutParams(Ui.dp(act, 24), Ui.dp(act, 24)));
        card.addView(r);
        Ui.asButton(card); card.setOnClickListener(v -> sub(make.build()));
        l.addView(card);
    }

    /** Opens a screen under a "< More" bar (a real back target; the phone back button uses up() too). */
    private void sub(View v) {
        removeAllViews(); inSub = true;
        LinearLayout col = Ui.col(act);
        LinearLayout back = Ui.row(act); back.setPadding(Ui.dp(act, 12), 0, Ui.dp(act, 16), 0); back.setMinimumHeight(Ui.dp(act, 48));
        ImageView ic = Ui.icon(act, R.drawable.ic_arrow_back, R.color.bofo_primary);
        back.addView(ic, new LinearLayout.LayoutParams(Ui.dp(act, 24), Ui.dp(act, 24)));
        TextView t = Ui.text(act, "More", R.style.Text_Bofo_Section); t.setTextColor(act.getColor(R.color.bofo_primary)); t.setPadding(Ui.dp(act, 8), 0, 0, 0);
        back.addView(t);
        back.setContentDescription("Back to More"); Ui.asButton(back); back.setOnClickListener(x -> act.showMore());
        col.addView(back, new LinearLayout.LayoutParams(-2, -2));
        col.addView(v, new LinearLayout.LayoutParams(-1, 0, 1f));
        addView(col, new LayoutParams(-1, -1));
    }

    /** Phone back button: true when it closed a sub-screen (the activity then does not leave the app). */
    boolean up() {
        if (!inSub) return false;
        act.showMore();
        return true;
    }
}
