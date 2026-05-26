package org.example.auction;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserNotificationCoverageTest {
    @Test
    void notificationDefaultsAndTrimsDisplayFields() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 5, 27, 9, 15);

        UserNotification notification = new UserNotification(
                " user-1 ",
                " BID ",
                " You won ",
                " Pay now ",
                createdAt
        );

        assertEquals(" user-1 ", notification.getUserId());
        assertEquals("BID", notification.getType());
        assertEquals("You won", notification.getTitle());
        assertEquals("Pay now", notification.getBody());
        assertEquals(createdAt, notification.getCreatedAt());
        assertFalse(notification.isRead());
        assertEquals("user-1|BID|You won|Pay now", notification.getPopupKey());
        assertEquals("27/05 09:15 - You won: Pay now", notification.getDisplayText());

        notification.markRead();

        assertTrue(notification.isRead());
    }

    @Test
    void notificationUsesSafeDefaultsForOptionalValues() {
        UserNotification notification = new UserNotification(null, " ", null, null, null);

        assertEquals("INFO", notification.getType());
        assertEquals("", notification.getTitle());
        assertEquals("", notification.getBody());
        assertEquals("|INFO||", notification.getPopupKey());
        assertNotNull(notification.getCreatedAt());
    }

    @Test
    void settlementStatusIdentifiesOnlyClosedStates() {
        assertFalse(AuctionSettlementStatus.AWAITING_WINNER_ADMISSION.isClosed());
        assertFalse(AuctionSettlementStatus.AWAITING_SELLER_CONFIRMATION.isClosed());
        assertFalse(AuctionSettlementStatus.AWAITING_SELLER_SHIPPING.isClosed());
        assertFalse(AuctionSettlementStatus.AWAITING_BUYER_CONFIRMATION.isClosed());
        assertFalse(AuctionSettlementStatus.DISPUTED_NOT_RECEIVED.isClosed());
        assertTrue(AuctionSettlementStatus.PAYMENT_RELEASED.isClosed());
        assertTrue(AuctionSettlementStatus.PAYMENT_UNFROZEN_BY_ADMIN.isClosed());
        assertTrue(AuctionSettlementStatus.PAYMENT_FROZEN_BY_ADMIN.isClosed());
        assertTrue(AuctionSettlementStatus.CANCELLED_NO_WINNER.isClosed());
    }
}
