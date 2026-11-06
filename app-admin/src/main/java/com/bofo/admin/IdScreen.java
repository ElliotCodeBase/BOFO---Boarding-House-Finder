package com.bofo.admin;

import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.bofo.shared.Api;
import com.bofo.shared.Async;
import com.bofo.shared.PermissionRules;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

/** ID verification queue and review. Server rules: only PENDING requests are readable by a reviewer; a second reviewer is asked via "second opinion". */
final class IdScreen extends FrameLayout {
    private static final String[] REASONS = {"Photo is blurry", "Name does not match", "ID is expired", "Selfie does not match the ID", "Other"};
    private final MainActivity act;
    private final Access access;
    private final LinearLayout list;

    IdScreen(MainActivity a, Access access) {
        super(a); act = a; this.access = access;
        list = Ui.col(a); int p = Ui.dp(a, 16); list.setPadding(p, p, p, p);
        SwipeRefreshLayout sr = new SwipeRefreshLayout(a); sr.addView(MainActivity.scroll(a, list));
        sr.setOnRefreshListener(() -> { load(); sr.setRefreshing(false); });
        addView(sr, new LayoutParams(-1, -1));
        load();
    }

    private void load() {
        Async.run(() -> {
            JSONArray rows = Api.get().select("owner_verifications", "status=eq.pending&order=submitted_at.asc&limit=100&select=id,owner_id,id_type,submitted_at,needs_second_opinion,id_front_url,id_back_url,selfie_url");
            Map<String, String> names = new HashMap<>();
            StringBuilder in = new StringBuilder();
            for (int i = 0; i < rows.length(); i++) in.append(i == 0 ? "" : ",").append(rows.getJSONObject(i).getString("owner_id"));
            if (in.length() > 0) { JSONArray u = Api.get().select("users", "id=in.(" + in + ")&select=id,full_name"); for (int i = 0; i < u.length(); i++) names.put(u.getJSONObject(i).getString("id"), u.getJSONObject(i).optString("full_name", "")); }
            return new Object[]{rows, names};
        }, r -> render((JSONArray) r[0], (Map<String, String>) r[1]), e -> { if (e instanceof Api.ApiException x && x.status == 401) act.sessionEnded(); else Ui.toast(getContext(), Ui.err(e)); });
    }

    private void render(JSONArray rows, Map<String, String> names) {
        android.content.Context c = getContext();
        list.removeAllViews();
        list.addView(Ui.text(c, "ID requests", R.style.Text_Bofo_Title));
        if (rows.length() == 0) { list.addView(Ui.text(c, "Nothing is waiting. Well done.", R.style.Text_Bofo_Caption)); return; }
        for (int i = 0; i < rows.length(); i++) {
            JSONObject x = rows.optJSONObject(i); if (x == null) continue;
            LinearLayout card = Ui.card(c);
            String nm = names.get(x.optString("owner_id")); 
            card.addView(Ui.text(c, nm == null || nm.isEmpty() ? "Unnamed owner" : nm, R.style.Text_Bofo_Section));
            card.addView(Ui.text(c, x.optString("id_type", "ID") + "  |  sent " + x.optString("submitted_at", "").replace('T', ' ').substring(0, Math.min(16, x.optString("submitted_at", "").length())), R.style.Text_Bofo_Caption));
            if (x.optBoolean("needs_second_opinion", false)) card.addView(Ui.pill(c, "Second opinion asked", R.color.bofo_card_ink, R.color.bofo_warn_tint));
            card.setOnClickListener(v -> review(x, nm == null ? "" : nm));
            list.addView(card);
        }
    }

    /* ---------- review dialog: three photos (watermarked with the reviewer's e-mail), approve / reject / second opinion ---------- */
    private void review(JSONObject x, String name) {
        android.content.Context c = getContext();
        Dialog d = new Dialog(act, android.R.style.Theme_Material_Light_NoActionBar);
        d.getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);     // a Dialog is its own window
        LinearLayout col = Ui.col(c); int p = Ui.dp(c, 16); col.setPadding(p, p, p, p);
        col.addView(Ui.text(c, name.isEmpty() ? "Unnamed owner" : name, R.style.Text_Bofo_Title));
        col.addView(Ui.text(c, x.optString("id_type", "ID"), R.style.Text_Bofo_Caption));
        String[][] imgs = {{"ID front", "id_front_url"}, {"ID back", "id_back_url"}, {"Selfie", "selfie_url"}};
        for (String[] im : imgs) {
            col.addView(Ui.text(c, im[0], R.style.Text_Bofo_Section));
            ImageView iv = new ImageView(c); iv.setAdjustViewBounds(true); iv.setContentDescription(im[0] + " photo");
            col.addView(iv, new LinearLayout.LayoutParams(-1, Ui.dp(c, 240)));
            loadImage(x.optString(im[1]), iv);
        }
        final String vid = x.optString("id");
        LinearLayout acts = Ui.row(c); acts.setGravity(Gravity.END);
        android.widget.Button close = Ui.button(c, "Close", false); close.setOnClickListener(v -> d.dismiss()); acts.addView(close);
        if (access.can(PermissionRules.ID_REVIEW)) {
            android.widget.Button second = Ui.button(c, x.optBoolean("needs_second_opinion") ? "Clear 2nd opinion" : "Ask 2nd opinion", false);
            second.setOnClickListener(v -> run(() -> Api.get().rpc("admin_flag_second_opinion", new JSONObject().put("vid", vid).put("flag", !x.optBoolean("needs_second_opinion"))), d, "Saved."));
            android.widget.Button rej = Ui.button(c, "Reject", false); rej.setOnClickListener(v -> reject(vid, d));
            android.widget.Button ok = Ui.button(c, "Approve", true); ok.setOnClickListener(v -> approve(vid, d));
            acts.addView(second); acts.addView(rej); acts.addView(ok);
        }
        col.addView(acts);
        d.setContentView(MainActivity.scroll(c, col));
        d.show();
    }

    private void loadImage(String path, ImageView into) {
        if (path == null || path.isEmpty()) return;
        String mark = AdminAuth.get().email();
        Async.run(() -> {
            byte[] raw = Api.get().fetchBytes(Api.get().signedUrl("id-verification", path, 120));
            BitmapFactory.Options o = new BitmapFactory.Options(); o.inSampleSize = 2;
            Bitmap src = BitmapFactory.decodeByteArray(raw, 0, raw.length, o);
            if (src == null) return null;
            Bitmap b = src.copy(Bitmap.Config.ARGB_8888, true);
            Canvas cv = new Canvas(b); Paint pt = new Paint(Paint.ANTI_ALIAS_FLAG); pt.setColor(0x66FFFFFF); pt.setShadowLayer(3f, 0, 0, 0x99000000); pt.setTextSize(Math.max(24f, b.getWidth() / 22f));
            cv.rotate(-25f, b.getWidth() / 2f, b.getHeight() / 2f);
            for (int y = -b.getHeight(); y < b.getHeight() * 2; y += Math.max(80, b.getHeight() / 5)) cv.drawText(mark + "  " + java.time.LocalDate.now(), -b.getWidth() / 4f, y, pt);
            return b;
        }, b -> { if (b != null) into.setImageBitmap(b); }, e -> Ui.toast(getContext(), "Photo could not be loaded."));
    }

    private void approve(String vid, Dialog d) {
        EditText in = new EditText(getContext()); in.setHint("ID number as printed on the ID");
        in.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(30)});
        new MaterialAlertDialogBuilder(act).setTitle("Approve this owner").setMessage("Type the ID number you can see. The server checks it against what the owner entered and against the ban list.")
                .setView(in).setNegativeButton("Cancel", null).setPositiveButton("Approve", (dd, w) -> {
                    String n = in.getText().toString().trim();
                    if (!n.matches("[A-Za-z0-9 \\-]{4,30}")) { Ui.toast(getContext(), "Use 4 to 30 letters, digits, spaces or dashes."); return; }
                    Reauth.ask(act, "Approve ID request", () -> run(() -> Api.get().rpc("admin_approve_verification", new JSONObject().put("vid", vid).put("id_number", n)), d, "Approved."), null);
                }).show();
    }

    private void reject(String vid, Dialog d) {
        final int[] pick = {-1};
        EditText note = new EditText(getContext()); note.setHint("Note (required for Other)");
        LinearLayout box = Ui.col(getContext()); box.setPadding(Ui.dp(getContext(), 20), 0, Ui.dp(getContext(), 20), 0); box.addView(note);
        new MaterialAlertDialogBuilder(act).setTitle("Why reject?").setSingleChoiceItems(REASONS, -1, (dd, i) -> pick[0] = i).setView(box)
                .setNegativeButton("Cancel", null).setPositiveButton("Reject", (dd, w) -> {
                    if (pick[0] < 0) { Ui.toast(getContext(), "Pick a reason."); return; }
                    boolean other = pick[0] == REASONS.length - 1; String nt = note.getText().toString().trim().replaceAll("\\s+", " ");
                    if (other && nt.isEmpty()) { Ui.toast(getContext(), "Write a short note for Other."); return; }
                    String reason = other ? nt : REASONS[pick[0]] + (nt.isEmpty() ? "" : ": " + nt);
                    if (reason.length() > 300) reason = reason.substring(0, 300);
                    final String why = reason;
                    Reauth.ask(act, "Reject ID request", () -> run(() -> Api.get().rpc("review_verification", new JSONObject().put("vid", vid).put("approve", false).put("reason", why)), d, "Rejected."), null);
                }).show();
    }

    private void run(Async.Work<String> w, Dialog d, String done) {
        Async.run(w, r -> { Ui.toast(getContext(), done); d.dismiss(); load(); }, e -> Ui.toast(getContext(), Ui.err(e)));
    }
}
