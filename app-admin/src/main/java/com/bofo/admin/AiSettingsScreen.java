package com.bofo.admin;

import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import com.bofo.shared.AiChatLogRules;
import com.bofo.shared.Api;
import com.bofo.shared.Async;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * AI settings (ai_settings, Owner only): switch the BOFO assistant on or off for every user, see whether the chat monitor is on, and delete all stored
 * AI chats. Every change asks for the fingerprint / screen lock and is written to the audit log by the server (set_ai_enabled, ai_chat_log_wipe).
 * The monitor flag itself is NOT changed here: it decides whether chats are stored at all, and stays a deliberate SQL-editor change.
 */
final class AiSettingsScreen extends FrameLayout {
    private final MainActivity act; private final LinearLayout list; private boolean busy;

    AiSettingsScreen(MainActivity a) {
        super(a); act = a;
        list = Ui.col(a); int p = Ui.dp(a, 16); list.setPadding(p, p, p, p);
        addView(MainActivity.scroll(a, list), new LayoutParams(-1, -1));
        load();
    }

    private void load() {
        list.removeAllViews();
        list.addView(Ui.text(act, "AI settings", R.style.Text_Bofo_Title));
        Async.run(() -> {
            JSONArray a = Api.get().select("app_flags", "key=in.(" + AiChatLogRules.FLAG_AI + "," + AiChatLogRules.FLAG_MONITOR + ")&select=key,value");
            Boolean ai = null, mon = null;
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                if (AiChatLogRules.FLAG_AI.equals(o.optString("key"))) ai = o.optBoolean("value", true);
                if (AiChatLogRules.FLAG_MONITOR.equals(o.optString("key"))) mon = o.optBoolean("value", false);
            }
            return new Boolean[]{ai, mon};
        }, r -> render(AiChatLogRules.aiOn(r[0]), AiChatLogRules.monitorOn(r[1])), e -> Ui.toast(act, Ui.err(e)));
    }

    private void render(boolean aiOn, boolean monitorOn) {
        LinearLayout c1 = Ui.card(act);
        c1.addView(Ui.text(act, "BOFO AI for users", R.style.Text_Bofo_Section));
        c1.addView(Ui.pill(act, aiOn ? "ON" : "OFF", aiOn ? R.color.bofo_success : R.color.bofo_error, aiOn ? R.color.bofo_success_tint : R.color.bofo_error_tint));
        c1.addView(Ui.text(act, "Turning it off hides the assistant for everyone until you turn it on again.", R.style.Text_Bofo_Caption));
        android.widget.Button sw = Ui.button(act, aiOn ? "Turn off" : "Turn on", !aiOn);
        sw.setOnClickListener(v -> confirmSwitch(!aiOn));
        c1.addView(sw);
        list.addView(c1);

        LinearLayout c2 = Ui.card(act);
        c2.addView(Ui.text(act, "AI chat monitor", R.style.Text_Bofo_Section));
        c2.addView(Ui.pill(act, monitorOn ? "ON: chats are stored" : "OFF: chats are not stored", monitorOn ? R.color.bofo_card_ink : R.color.bofo_success, monitorOn ? R.color.bofo_warn_tint : R.color.bofo_success_tint));
        c2.addView(Ui.text(act, "Decides whether what people ask BOFO is saved for you to read under More > AI chats (kept " + AiChatLogRules.RETENTION_DAYS
                + " days). It is changed only in the Supabase SQL editor, on purpose.", R.style.Text_Bofo_Caption));
        list.addView(c2);

        LinearLayout c3 = Ui.card(act);
        c3.addView(Ui.text(act, "Delete all stored AI chats", R.style.Text_Bofo_Section));
        c3.addView(Ui.text(act, "Removes every saved question and answer for everyone. It cannot be undone.", R.style.Text_Bofo_Caption));
        android.widget.Button wipe = Ui.button(act, "Delete all", false);
        wipe.setOnClickListener(v -> confirmWipe());
        c3.addView(wipe);
        list.addView(c3);
    }

    private void confirmSwitch(boolean on) {
        if (busy) return;
        new MaterialAlertDialogBuilder(act).setTitle(on ? "Turn BOFO AI on?" : "Turn BOFO AI off?")
                .setMessage(on ? "The assistant will show for all users again." : "The assistant will be hidden for all users.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton(on ? "Turn on" : "Turn off", (d, w) -> Reauth.ask(act, "Change BOFO AI", () -> {
                    busy = true;
                    Async.run(() -> Api.get().rpc("set_ai_enabled", new JSONObject().put("on_", on)),
                            ok -> { busy = false; Ui.toast(act, on ? "BOFO AI is on." : "BOFO AI is off."); load(); },
                            e -> { busy = false; Ui.toast(act, Ui.err(e)); });
                }, null)).show();
    }

    private void confirmWipe() {
        if (busy) return;
        LinearLayout box = Ui.col(act); int p = Ui.dp(act, 20); box.setPadding(p, p / 2, p, 0);
        box.addView(Ui.text(act, "Every stored AI chat is deleted for good. Type DELETE to continue.", R.style.Text_Bofo_Caption));
        EditText t = new EditText(act); t.setHint("DELETE"); t.setSingleLine(); box.addView(t);
        new MaterialAlertDialogBuilder(act).setTitle("Delete all AI chats?").setView(box).setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (d, w) -> {
                    if (!AiChatLogRules.wipeConfirmed(t.getText().toString())) { Ui.toast(act, "Type DELETE to confirm."); return; }
                    Reauth.ask(act, "Delete all AI chats", () -> {
                        busy = true;
                        Async.run(() -> Api.get().rpcObject("ai_chat_log_wipe", new JSONObject()).optInt("deleted", 0),
                                n -> { busy = false; Ui.toast(act, "Deleted " + n + " chats."); },
                                e -> { busy = false; Ui.toast(act, Ui.err(e)); });
                    }, null);
                }).show();
    }
}
