package com.bofo.finder.ui;

import com.bofo.finder.service.ChatService;

public final class OwnerShell extends Shell {
    public OwnerShell() {
        super(new String[]{"Dashboard", "Listings", "Chats", "Verify", "Profile"},
                () -> new DashboardView(), () -> new ListingsView(), () -> new ChatsView(), () -> new VerificationView(), () -> new ProfileView());
        refreshBadge(new ChatService());
    }
}
