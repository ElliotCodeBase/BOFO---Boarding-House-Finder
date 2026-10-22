package com.bofo.finder.ui;

import com.bofo.finder.service.AuthService;
import com.bofo.finder.service.BhService;
import com.bofo.finder.util.Async;
import javafx.geometry.Insets;
import javafx.scene.layout.VBox;

public final class SavedView extends VBox {
    public SavedView() {
        BhService bh = new BhService();
        VBox list = new VBox(10); list.setPadding(new Insets(12, 16, 90, 16));
        getChildren().addAll(Ui.appBar("Saved"), Ui.scroll(list));
        Async.run(bh::favorites, items -> {
            if (items.isEmpty()) list.getChildren().add(Ui.label("Tap ♡ on a boarding house to save it here.", "secondary"));
            String uid = AuthService.get().userId();
            items.forEach(b -> list.getChildren().add(new BhCard(b, false, true,
                    x -> Nav.show(new BhDetailView(x.id(), () -> Nav.show(new StudentShell(), true)), true),
                    on -> Async.run(() -> { bh.setFavorite(uid, b.id(), on); return true; }, ok -> {}))));
        }, e -> list.getChildren().add(Ui.label("Could not load saved items.", "error-text")));
    }
}
