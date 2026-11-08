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
        MaterialButton b = new MaterialButton(c);
        b.setText(label); b.setAllCaps(false); b.setTextSize(14);
        b.setMinHeight(dp(c, 48)); b.setMinimumHeight(dp(c, 48));                 // 48 dp touch target...
        b.setInsetTop(dp(c, 4)); b.setInsetBottom(dp(c, 4));                      // ...drawn 40 dp tall
        b.setCornerRadius(dp(c, 20)); b.setStrokeWidth(dp(c, 1)); b.setPadding(dp(c, 16), 0, dp(c, 16), 0);
        int ink = selected ? R.color.bofo_primary : R.color.bofo_text_2;
        b.setTextColor(c.getColor(ink));
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(c.getColor(selected ? R.color.bofo_primary_tint : R.color.bofo_white)));
        b.setStrokeColor(android.content.res.ColorStateList.valueOf(c.getColor(selected ? R.color.bofo_primary : R.color.bofo_stroke_neutral)));
        if (selected) b.setTypeface(null, android.graphics.Typeface.BOLD);
        androidx.core.view.ViewCompat.setStateDescription(b, selected ? "Selected" : "Not selected");
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, dp(c, 8), 0); b.setLayoutParams(lp);
        return b;
    }

    /** Empty list: a round tick, one calm sentence, centred. Decorative icon (TalkBack reads the sentence only). */
    static View empty(Context c, String message) {
        LinearLayout l = col(c); l.setGravity(android.view.Gravity.CENTER_HORIZONTAL); l.setPadding(dp(c, 24), dp(c, 56), dp(c, 24), dp(c, 24));
        android.widget.ImageView iv = new android.widget.ImageView(c);
        iv.setImageResource(R.drawable.ic_nav_approvals); iv.setColorFilter(c.getColor(R.color.bofo_primary));
        iv.setContentDescription(null); iv.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        GradientDrawable g = new GradientDrawable(); g.setShape(GradientDrawable.OVAL); g.setColor(c.getColor(R.color.bofo_primary_tint));
        iv.setBackground(g); iv.setPadding(dp(c, 18), dp(c, 18), dp(c, 18), dp(c, 18));
        l.addView(iv, new LinearLayout.LayoutParams(dp(c, 72), dp(c, 72)));
        TextView t = text(c, message, R.style.Text_Bofo_Body); t.setTextColor(c.getColor(R.color.bofo_text_2)); t.setGravity(android.view.Gravity.CENTER); t.setPadding(0, dp(c, 16), 0, 0);
        l.addView(t, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return l;
    }

    /** A tappable card or row built from a LinearLayout: TalkBack must say "button", not just read the text. */
    static void asButton(View v) { androidx.core.view.ViewCompat.setAccessibilityDelegate(v, new ButtonRole()); ripple(v, 16); }

    /** Touch feedback: a soft purple ripple clipped to the card corners (tappable cards used to give no sign they were pressed). */
    static void ripple(View v, int radiusDp) {
        Context c = v.getContext();
        GradientDrawable mask = new GradientDrawable(); mask.setColor(Color.BLACK); mask.setCornerRadius(dp(c, radiusDp));
        v.setForeground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(c.getColor(R.color.bofo_ripple)), null, mask));
    }

    /** Decorative icon (TalkBack skips it; the words next to it say everything). */
    static android.widget.ImageView icon(Context c, int drawable, int colorRes) {
        android.widget.ImageView iv = new android.widget.ImageView(c);
        iv.setImageResource(drawable); iv.setColorFilter(c.getColor(colorRes));
        iv.setContentDescription(null); iv.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return iv;
    }

    /** First-load spinner (tagged so Ui.fail knows the screen never got its content). */
    static View loading(Context c) {
        LinearLayout l = col(c); l.setGravity(android.view.Gravity.CENTER); l.setPadding(0, dp(c, 72), 0, 0); l.setTag("loading");
        l.addView(new android.widget.ProgressBar(c));
        TextView t = text(c, "Loading...", R.style.Text_Bofo_Caption); t.setPadding(0, dp(c, 12), 0, 0); l.addView(t);
        return l;
    }

    /** A failed first load: say why in plain words and offer Try again (before, the screen stayed blank). A failed refresh keeps the old content and only shows the toast. */
    static void fail(LinearLayout list, Context c, Throwable e, Runnable retry) {
        toast(c, err(e));
        View first = list.getChildAt(0);
        if (first == null || !"loading".equals(first.getTag())) return;
        list.removeAllViews();
        LinearLayout l = col(c); l.setGravity(android.view.Gravity.CENTER_HORIZONTAL); l.setPadding(dp(c, 24), dp(c, 56), dp(c, 24), 0);
        TextView t = text(c, err(e), R.style.Text_Bofo_Body); t.setGravity(android.view.Gravity.CENTER); l.addView(t);
        Button b = button(c, "Try again", true);
        b.setOnClickListener(v -> { list.removeAllViews(); list.addView(loading(c)); retry.run(); });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT); lp.topMargin = dp(c, 16);
        l.addView(b, lp);
        list.addView(l);
    }

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
