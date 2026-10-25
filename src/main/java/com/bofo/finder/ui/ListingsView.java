package com.bofo.finder.ui;

import com.bofo.finder.model.BoardingHouse;
import com.bofo.finder.service.AuthService;
import com.bofo.finder.service.BhService;
import com.bofo.finder.util.Async;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.layout.VBox;

public final class ListingsView extends VBox {
    private final BhService bh = new BhService();
    private final VBox rows = new VBox(10);

    public ListingsView() {
        Button add = Ui.btn("+ Add listing", "btn-cta", () -> Nav.show(new ListingFormView(() -> Nav.show(new OwnerShell(), true)), true));
        rows.setPadding(new Insets(12, 16, 72, 16));
        getChildren().addAll(Ui.pageHeader("My listings", null, add), Ui.scroll(rows));
        load();
    }

    private void load() {
        Ui.load(rows, () -> Skeleton.cards(2), () -> bh.myListings(AuthService.get().userId()), l -> {
            if (l.isEmpty()) rows.getChildren().add(Ui.label("No listings yet.", "secondary"));
            for (BoardingHouse b : l) {
                BhCard c = new BhCard(b, false, false, x -> {}, (on, undo) -> {});
                Button unlist = Ui.btn("Unlist", "btn-outline", () -> Async.run(() -> { bh.setStatus(b.id(), "unlisted"); return true; }, ok -> load(), e -> Ui.failToast(e, "Failed")));
                Button del = Ui.btn("Delete", "btn-danger", () -> Async.run(() -> { bh.deleteListing(b.id()); return true; }, ok -> load(), e -> Ui.failToast(e, "Failed")));
                Button boost = Ui.btn(b.featured() ? "Extend boost" : "Boost", "btn-cta", () -> Nav.show(new BoostView(b, () -> Nav.show(new OwnerShell(), true)), true));
                rows.getChildren().addAll(c, Ui.hbox(8, boost, unlist, del));
            }
        });
    }
}
