package com.bofo.admin;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.bofo.shared.Api;
import com.google.android.material.button.MaterialButton;

import org.json.JSONObject;

/** Small view helpers (screens are built in code; same colours and Text.Bofo styles as the main app). */
final class Ui {
    private Ui() {}
    static int dp(Context c, int v) { return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics())); }
    static int color(Context c, int id) { return c.getColor(id); }

    static TextView text(Context c, String s, int style) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextAppearance(style);
        if (style == R.style.Text_Bofo_Title || style == R.style.Text_Bofo_Section) androidx.core.view.ViewCompat.setAccessibilityHeading(t, true);   // TalkBack heading navigation
        return t;
    }
    static LinearLayout col(Context c) { LinearLayout l = new LinearLayout(c); l.setOrientation(LinearLayout.VERTICAL); return l; }
    static LinearLayout row(Context c) { LinearLayout l = new LinearLayout(c); l.setOrientation(LinearLayout.HORIZONTAL); l.setGravity(android.view.Gravity.CENTER_VERTICAL); return l; }

    static LinearLayout card(Context c) {
        LinearLayout l = col(c);
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.WHITE); g.setCornerRadius(dp(c, 16)); g.setStroke(dp(c, 1), c.getColor(R.color.bofo_border));
        l.setBackground(g); l.setPadding(dp(c, 16), dp(c, 14), dp(c, 16), dp(c, 14));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, 0, 0, dp(c, 10)); l.setLayoutParams(p);
        return l;
    }

    static Button button(Context c, String label, boolean filled) {
        MaterialButton b = new MaterialButton(c);
        b.setText(label); b.setAllCaps(false);
        b.setMinHeight(dp(c, 48)); b.setMinimumHeight(dp(c, 48));      // 48 dp touch target
        if (!filled) { b.setBackgroundColor(Color.TRANSPARENT); b.setTextColor(c.getColor(R.color.bofo_primary)); }
        return b;
    }

    /** Filter chip: a button whose selected state is also spoken (colour alone is not enough). */
    static Button chip(Context c, String label, boolean selected) {
        Button b = button(c, label, selected);
        androidx.core.view.ViewCompat.setStateDescription(b, selected ? "Selected" : "Not selected");
        return b;
    }

    /** A tappable card or row built from a LinearLayout: TalkBack must say "button", not just read the text. */
    static void asButton(View v) { androidx.core.view.ViewCompat.setAccessibilityDelegate(v, new ButtonRole()); }

    private static final class ButtonRole extends androidx.core.view.AccessibilityDelegateCompat {
        @Override public void onInitializeAccessibilityNodeInfo(View host, androidx.core.view.accessibility.AccessibilityNodeInfoCompat info) {
            super.onInitializeAccessibilityNodeInfo(host, info);
            info.setClassName(Button.class.getName());
        }
    }

    /** A row of action buttons. With a large font (1.3x or more) the buttons stack so none is cut off. */
    static LinearLayout actions(Context c) {
        LinearLayout l = row(c);
        if (c.getResources().getConfiguration().fontScale >= 1.3f) { l.setOrientation(LinearLayout.VERTICAL); l.setGravity(android.view.Gravity.END); }
        return l;
    }

    /** Words AND colour (accessibility habit of the main app). */
    static TextView pill(Context c, String s, int fg, int bg) {
        TextView t = text(c, s, R.style.Text_Bofo_Caption);
        t.setTextColor(c.getColor(fg));
        GradientDrawable g = new GradientDrawable(); g.setColor(c.getColor(bg)); g.setCornerRadius(dp(c, 10));
        t.setBackground(g); t.setPadding(dp(c, 8), dp(c, 2), dp(c, 8), dp(c, 2));
        return t;
    }

    /** Waiting-time pill: green under a day, amber 1 to 3 days, red after 3 days. Words as well as colour. */
    static TextView waitPill(Context c, String createdIso) {
        long age = com.bofo.shared.QueueRules.ageMillis(createdIso, System.currentTimeMillis());
        if (age < 0) return pill(c, "Waiting", R.color.bofo_card_ink, R.color.bofo_warn_tint);
        int tier = com.bofo.shared.QueueRules.tier(age);
        String label = com.bofo.shared.QueueRules.waitLabel(age);
        if (tier == com.bofo.shared.QueueRules.TIER_LATE) return pill(c, label + " (late)", R.color.bofo_error, R.color.bofo_error_tint);
        if (tier == com.bofo.shared.QueueRules.TIER_WAITING) return pill(c, label, R.color.bofo_card_ink, R.color.bofo_warn_tint);
        return pill(c, label, R.color.bofo_success, R.color.bofo_success_tint);
    }

    /** Plain words for an error. Never shows raw JSON or ids. */
    static String err(Throwable e) {
        if (e instanceof java.io.IOException) return "No connection. Check the internet and try again.";
        if (e instanceof Api.ApiException a) {
            if (a.status == 401) return "Your session ended. Sign in again.";
            if (a.status == 403 || a.body.contains("42501")) return "Your role cannot do this.";
            try { String m = new JSONObject(a.body).optString("message", ""); if (!m.isEmpty() && m.length() < 160) return m; } catch (Exception ignored) { }
            return "The server refused this (code " + a.status + ").";
        }
        return "Something went wrong. Try again.";
    }
    /** JSON object from key, value pairs (org.json throws a checked exception on Android; this wraps it for use in click handlers). */
    static JSONObject json(Object... kv) {
        try { JSONObject o = new JSONObject(); for (int i = 0; i + 1 < kv.length; i += 2) o.put((String) kv[i], kv[i + 1]); return o; }
        catch (org.json.JSONException e) { throw new IllegalStateException(e); }
    }
    static void toast(Context c, String s) { android.widget.Toast.makeText(c, s, android.widget.Toast.LENGTH_LONG).show(); }
    static View space(Context c, int h) { View v = new View(c); v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(c, h))); return v; }
}
