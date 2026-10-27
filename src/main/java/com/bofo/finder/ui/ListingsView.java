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
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

public final class ListingsView extends VBox {
    private final BhService bh = new BhService();
    /** One column, sized by the screen width like Home / Explore, so a card never grows wider than the screen. */
    private final CardGrid rows = new CardGrid(10, 150, 1);

    public ListingsView() {
        // compact round "+" (the full "+ Add listing" pill took half the header and squeezed the title into "My ...")
        Button add = new Button(); add.getStyleClass().add("hdr-btn-cta");
        add.setGraphic(Icons.icon(Icons.PLUS, 20, javafx.scene.paint.Color.WHITE));
        add.setTooltip(new javafx.scene.control.Tooltip("Add listing"));
        add.setOnAction(e -> Nav.show(new ListingFormView(() -> Nav.show(new OwnerShell(), true)), false));
        rows.setPadding(new Insets(12, 16, 72, 16));
        getChildren().addAll(Ui.pageHeader("My listings", null, add), Ui.scroll(rows));
        load();
    }

    private void load() {
        Ui.load(rows, () -> Skeleton.cards(2), () -> bh.myListings(AuthService.get().userId()), l -> {
            if (l.isEmpty()) rows.getChildren().add(Ui.label("No listings yet.", "secondary"));
            for (BoardingHouse b : l) {
                BhCard c = new BhCard(b, false, false, x -> {}, (on, undo) -> {});
                for (javafx.scene.Node h : c.lookupAll(".heart-fab")) { h.setVisible(false); h.setManaged(false); }   // no favourite heart on your own listing
                Button unlist = Ui.btn("Unlist", "btn-outline", () -> Async.run(() -> { bh.setStatus(b.id(), "unlisted"); return true; }, ok -> load(), e -> showError("Unlist failed", e)));
                Button del = Ui.btn("Delete", "btn-danger", () -> confirmDelete(b));
                Button boost = Ui.btn(b.featured() ? "Extend boost" : "Boost", "btn-cta", () -> Nav.show(new BoostView(b, () -> Nav.show(new OwnerShell(), true)), true));
                HBox actions = Ui.hbox(8, boost, unlist, del);                 // compact buttons that share the card width
                for (Button x : new Button[]{boost, unlist, del}) {
                    x.getStyleClass().add("btn-sm");
                    x.setMinWidth(Region.USE_PREF_SIZE);                        // a label is never cut to "Bo..."; spare width is shared equally
                    x.setMaxWidth(Double.MAX_VALUE); HBox.setHgrow(x, Priority.ALWAYS);
                }
                VBox group = new VBox(8, c, actions);                           // actions stay attached to their own card
                group.setPadding(new Insets(0, 0, 10, 0));
                rows.getChildren().add(group);
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
