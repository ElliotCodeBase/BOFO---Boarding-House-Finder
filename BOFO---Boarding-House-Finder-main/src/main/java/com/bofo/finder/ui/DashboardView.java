package com.bofo.finder.ui;

import com.bofo.finder.service.AuthService;
import com.bofo.finder.service.BhService;
import com.bofo.finder.util.Async;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

public final class DashboardView extends VBox {
    public DashboardView() {
        var p = AuthService.get().profile();
        VBox box = Ui.padded(12);
        getChildren().addAll(Ui.appBar("Dashboard"), Ui.scroll(box));
        String vs = p.verificationStatus();
        Label status = Ui.label(switch (vs) {
            case "verified" -> "✔ You are a Verified Owner. You can publish listings.";
            case "pending" -> "⏳ Verification pending. An admin is reviewing your documents.";
            case "rejected" -> "✖ Verification rejected. Open the Verify tab to see why and resubmit.";
            default -> "You must verify your identity before publishing listings. Open the Verify tab.";
        }, "card");
        box.getChildren().add(status);
        Async.run(() -> new BhService().myListings(p.id()), l -> {
            int beds = l.stream().mapToInt(b -> b.availableBeds()).sum();
            double avg = l.stream().filter(b -> b.reviewCount() > 0).mapToDouble(b -> b.avgRating()).average().orElse(0);
            box.getChildren().addAll(Ui.label("Listings: " + l.size(), "h2"), Ui.label("Beds available: " + beds, "h2"),
                    Ui.label("Average rating: " + String.format("%.1f", avg) + " " + Ui.stars(avg), "h2"));
        }, e -> box.getChildren().add(Ui.label("Could not load stats.", "error-text")));
    }
}
