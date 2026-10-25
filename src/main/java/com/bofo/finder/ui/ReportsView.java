package com.bofo.finder.ui;

import com.bofo.finder.service.SupabaseClient;
import com.bofo.finder.util.Async;
import javafx.geometry.Insets;
import javafx.scene.layout.VBox;
import org.json.JSONArray;
import org.json.JSONObject;

public final class ReportsView extends VBox {
    public ReportsView() {
        VBox rows = new VBox(8); rows.setPadding(new Insets(12, 16, 72, 16));
        getChildren().addAll(Ui.pageHeader("Reports", null), Ui.scroll(rows));
        Ui.load(rows, () -> Skeleton.rows(5), () -> SupabaseClient.get().select("blocks_reports", "order=created_at.desc&limit=100&select=*"), (JSONArray a) -> {
            if (a.isEmpty()) rows.getChildren().add(Ui.label("No reports.", "secondary"));
            for (int i = 0; i < a.length(); i++) { JSONObject r = a.getJSONObject(i);
                VBox c = new VBox(2, Ui.label(r.optString("reason", "(no reason)"), "label"), Ui.label("Reported user: " + r.getString("reported_user_id"), "secondary"));
                c.getStyleClass().add("card"); rows.getChildren().add(c); }
        });
    }
}
