package com.bofo.finder.ui;

import com.bofo.finder.service.AuthService;
import com.bofo.finder.service.BhService;
import com.bofo.finder.util.Async;
import javafx.geometry.Insets;
import javafx.scene.layout.VBox;

public final class SavedView extends VBox {
    public SavedView() {
        BhService bh = new BhService();
        CardGrid list = new CardGrid(10, 320); list.setPadding(new Insets(12, 16, 72, 16));
        getChildren().addAll(Ui.appBar("Saved"), Ui.scroll(list));
        Ui.load(list, () -> Skeleton.cards(4), bh::favorites, items -> {
            if (items.isEmpty()) list.getChildren().add(Ui.emptyState("Nothing saved yet", "Tap the heart on a boarding house to keep it here."));
            String uid = AuthService.get().userId();
            items.forEach(b -> list.getChildren().add(new BhCard(b, false, true,
                    x -> Nav.show(new BhDetailView(x.id(), () -> Nav.show(new StudentShell(), true)), true),
                    (on, undo) -> BhCard.saveFavorite(bh, uid, b.id(), on, undo))));
        });
    }
}
