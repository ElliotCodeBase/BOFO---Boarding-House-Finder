package com.bofo.finder.ui;

import com.bofo.finder.service.ChatService;

public final class StudentShell extends Shell {
    public StudentShell() {
        super(new String[]{"Home", "Explore", "Saved", "Chats", "Profile"},
                () -> new HomeView(), () -> new ExploreView(), () -> new SavedView(), () -> new ChatsView(), () -> new ProfileView());
        refreshBadge(new ChatService());
        // no round "+" for students: it only jumped to Explore, which already has its own tab
        BofoPanel.filtersListener = f -> { ExploreView.pendingFilters = f; select(1); };
    }
}
