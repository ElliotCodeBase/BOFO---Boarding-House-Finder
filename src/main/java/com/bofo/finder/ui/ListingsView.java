package com.bofo.finder.ui;

import com.bofo.finder.model.BoardingHouse;
import com.bofo.finder.service.AuthService;
import com.bofo.finder.service.BhService;
import com.bofo.finder.util.Async;
import com.bofo.finder.util.ErrorText;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public final class ListingsView extends VBox {
    private final BhService bh = new BhService();
    /** One column, sized by the screen width like Home / Explore, so a card never grows wider than the screen. */
    private final CardGrid rows = new CardGrid(10, 150, 1);

    public ListingsView() {
        Button add = Ui.btn("+ Add listing", "btn-cta", () -> Nav.show(new ListingFormView(() -> Nav.show(new OwnerShell(), true)), false));
        rows.setPadding(new Insets(12, 16, 72, 16));
        getChildren().addAll(Ui.pageHeader("My listings", null, add), Ui.scroll(rows));
        load();
    }

    private void load() {
        Ui.load(rows, () -> Skeleton.cards(2), () -> bh.myListings(AuthService.get().userId()), l -> {
            if (l.isEmpty()) rows.getChildren().add(Ui.label("No listings yet.", "secondary"));
            for (BoardingHouse b : l) {
                BhCard c = new BhCard(b, false, false, x -> {}, (on, undo) -> {});
                Button unlist = Ui.btn("Unlist", "btn-outline", () -> Async.run(() -> { bh.setStatus(b.id(), "unlisted"); return true; }, ok -> load(), e -> showError("Unlist failed", e)));
                Button del = Ui.btn("Delete", "btn-danger", () -> confirmDelete(b));
                Button boost = Ui.btn(b.featured() ? "Extend boost" : "Boost", "btn-cta", () -> Nav.show(new BoostView(b, () -> Nav.show(new OwnerShell(), true)), true));
                HBox actions = Ui.hbox(8, boost, unlist, del);                 // three equal buttons that share the card width
                for (Button x : new Button[]{boost, unlist, del}) { x.setMinWidth(0); x.setMaxWidth(Double.MAX_VALUE); HBox.setHgrow(x, Priority.ALWAYS); }
                actions.setPadding(new Insets(0, 0, 8, 0));
                rows.getChildren().addAll(c, actions);
            }
        });
    }

    private void confirmDelete(BoardingHouse b) {
        Popup.confirm(Popup.Kind.DANGER, "Delete this listing?", "\"" + b.name() + "\" and its rooms, photos, reviews and chats will be removed. This cannot be undone.", "Delete", () ->
                Async.run(() -> { bh.deleteListing(b.id()); return true; }, ok -> { Nav.toast("Listing deleted"); load(); }, e -> showError("Delete failed", e)));
    }

    /** Full, selectable error text in a dialog (a toast vanishes before you can read it). */
    private void showError(String headline, Throwable e) {
        e.printStackTrace();
        Popup.alert(Popup.Kind.ERROR, headline, ErrorText.full(headline, e));
    }
}
