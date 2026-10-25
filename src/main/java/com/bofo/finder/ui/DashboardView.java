package com.bofo.finder.ui;

import com.bofo.finder.service.AuthService;
import com.bofo.finder.service.BhService;
import com.bofo.finder.util.Async;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/** Owner home: verification status banner + three headline numbers. */
public final class DashboardView extends VBox {
    public DashboardView() {
        var p = AuthService.get().profile();
        VBox box = Ui.padded(12);
        getChildren().addAll(Ui.pageHeader("Dashboard", null), Ui.scroll(box));
        String vs = p.verificationStatus();
        String cls = switch (vs) { case "verified" -> "notice-ok"; case "rejected" -> "notice-bad"; default -> "notice-warn"; };
        Label status = Ui.label(switch (vs) {
            case "verified" -> "You are a Verified Owner. You can publish listings.";
            case "pending" -> "Verification pending. An admin is reviewing your documents.";
            case "rejected" -> "Verification rejected. Open Profile > Verification to see why and resubmit.";
            default -> "Verify your identity before publishing listings. Open Profile > Verification.";
        }, cls);
        box.getChildren().add(status);
        VBox statsBox = new VBox(10); box.getChildren().add(statsBox);

        // map of the owner's listings; tap a pin to see the walking route from the nearest campus
        MapPane map = new MapPane(300);
        map.enablePreview(null);
        VBox mapBox = new VBox(8, Ui.section("Your listings on the map"), MapPane.framed(map));
        mapBox.setVisible(false); mapBox.setManaged(false);                       // appears once there is a listing to show
        box.getChildren().add(mapBox);
        Async.run(() -> new BhService().colleges(), map::setColleges, e -> {});
        Ui.load(statsBox, () -> Skeleton.stats(3), () -> new BhService().myListings(p.id()), l -> {
            int beds = l.stream().mapToInt(b -> b.availableBeds()).sum();
            double avg = l.stream().filter(b -> b.reviewCount() > 0).mapToDouble(b -> b.avgRating()).average().orElse(0);
            HBox stats = new HBox(10, Ui.statCard(String.valueOf(l.size()), "Listings"), Ui.statCard(String.valueOf(beds), "Beds available"),
                    Ui.statCard(avg == 0 ? "-" : String.format("%.1f", avg), "Avg rating"));
            statsBox.getChildren().addAll(Ui.section("Overview"), stats);
            if (!l.isEmpty()) {
                double lat = l.stream().mapToDouble(b -> b.lat()).average().orElse(0), lng = l.stream().mapToDouble(b -> b.lng()).average().orElse(0);
                mapBox.setManaged(true); mapBox.setVisible(true);
                map.show(new double[]{lat, lng}, 0, l, false, null);
                map.fitPins(l);
            }
        });
    }
}
