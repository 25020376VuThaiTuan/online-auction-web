package org.example.controller;

import org.example.model.User;
import org.example.service.MarketplaceDashboardService;
import org.example.state.ApplicationSession;

final class DashboardBidderNameResolver {
    private final ApplicationSession applicationSession;
    private final MarketplaceDashboardService dashboardService;

    DashboardBidderNameResolver(ApplicationSession applicationSession, MarketplaceDashboardService dashboardService) {
        this.applicationSession = applicationSession;
        this.dashboardService = dashboardService;
    }

    String displayName(String bidderId) {
        String safeBidderId = DashboardFormatters.value(bidderId);
        if (safeBidderId.isBlank()) {
            return "Unknown bidder";
        }

        var currentUser = applicationSession.getCurrentUser();
        if (currentUser.isPresent() && safeBidderId.equals(currentUser.get().getId())) {
            return currentUser.get().getFullName();
        }

        try {
            return dashboardService.findUserById(safeBidderId)
                    .map(User::getFullName)
                    .filter(name -> !DashboardFormatters.value(name).isBlank())
                    .orElse(safeBidderId);
        } catch (RuntimeException ignored) {
            return safeBidderId;
        }
    }
}
