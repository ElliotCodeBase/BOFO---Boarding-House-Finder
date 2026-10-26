package com.bofo.finder.ui;

import com.bofo.finder.service.ChatService;
import javafx.scene.layout.Region;

public final class OwnerShell extends Shell {
    public OwnerShell() {
        super(new String[]{"Dashboard", "Listings", "Create", "Chats", "Profile"},
                () -> new DashboardView(), () -> new ListingsView(), () -> new Region(), () -> new ChatsView(), () -> new ProfileView());
        refreshBadge(new ChatService());
        setActionTab(2, () -> Nav.show(new ListingFormView(() -> Nav.show(new OwnerShell(), true)), false));   // "Create" = add listing
    }
}
