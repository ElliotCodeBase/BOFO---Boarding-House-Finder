package com.bofo.finder.ui;

public final class AdminShell extends Shell {
    public AdminShell() {
        super(new String[]{"Queue", "Payments", "Reports", "Profile"},
                () -> new QueueView(), () -> new PaymentsView(), () -> new ReportsView(), () -> new ProfileView());
    }
}
