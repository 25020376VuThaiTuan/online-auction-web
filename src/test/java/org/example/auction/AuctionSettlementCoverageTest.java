package org.example.auction;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuctionSettlementCoverageTest {
    @Test
    void settlementDefaultsClampAmountsAndFormatDisplayFields() {
        AuctionSettlement settlement = new AuctionSettlement(
                "ITEM-1",
                "Vintage Camera",
                "SELLER-1",
                "BIDDER-1",
                200.0,
                25.0,
                10.0,
                210.0,
                185.0,
                10.0,
                190.0,
                null
        );

        assertEquals("ITEM-1", settlement.getItemId());
        assertEquals("Vintage Camera", settlement.getItemName());
        assertEquals("SELLER-1", settlement.getSellerId());
        assertEquals("BIDDER-1", settlement.getWinnerBidderId());
        assertEquals(200.0, settlement.getWinningBidAmount());
        assertEquals(25.0, settlement.getDepositAmount());
        assertEquals(10.0, settlement.getBuyerPremiumAmount());
        assertEquals(210.0, settlement.getTotalBuyerDue());
        assertEquals(185.0, settlement.getRemainingPaymentDue());
        assertEquals(10.0, settlement.getAdminCommission());
        assertEquals(190.0, settlement.getSellerPayout());
        assertNotNull(settlement.getFinishedAt());
        assertEquals(AuctionSettlementStatus.AWAITING_WINNER_ADMISSION, settlement.getStatus());

        settlement.setStatus(null);
        assertEquals(AuctionSettlementStatus.AWAITING_WINNER_ADMISSION, settlement.getStatus());
        settlement.setStatus(AuctionSettlementStatus.AWAITING_BUYER_CONFIRMATION);
        settlement.setLockedRemainingPayment(-5.0);
        settlement.setSellerReleasedAmount(-1.0);
        settlement.setBuyerRefundedAmount(-2.0);
        settlement.setDisputeReason("  delayed delivery  ");

        assertEquals(0.0, settlement.getLockedRemainingPayment());
        assertEquals(0.0, settlement.getSellerReleasedAmount());
        assertEquals(0.0, settlement.getBuyerRefundedAmount());
        assertEquals("delayed delivery", settlement.getDisputeReason());
        assertEquals("AWAITING BUYER CONFIRMATION", settlement.getDisplayStatus());
        assertEquals("N/A", settlement.getDisplayDeadline());
        assertFalse(settlement.isBuyerConfirmationExpired(LocalDateTime.of(2026, 5, 27, 10, 0)));
        assertTrue(settlement.getDisplaySummary().contains("Vintage Camera [AWAITING BUYER CONFIRMATION]"));
    }

    @Test
    void settlementTimestampsAndDeadlineExpirationAreTracked() {
        LocalDateTime finishedAt = LocalDateTime.of(2026, 5, 27, 9, 0);
        LocalDateTime deadline = LocalDateTime.of(2026, 5, 28, 9, 0);
        AuctionSettlement settlement = new AuctionSettlement(
                "ITEM-2",
                "Desk Lamp",
                "SELLER-2",
                "BIDDER-2",
                100.0,
                10.0,
                5.0,
                105.0,
                95.0,
                5.0,
                95.0,
                finishedAt
        );

        settlement.setWinnerAdmittedAt(finishedAt.plusMinutes(5));
        settlement.setShippedAt(finishedAt.plusHours(2));
        settlement.setBuyerConfirmationDeadline(deadline);
        settlement.setReleasedAt(deadline.plusHours(1));
        settlement.setDisputedAt(deadline.plusHours(2));
        settlement.setAdminDecisionAt(deadline.plusHours(3));
        settlement.setLockedRemainingPayment(95.0);
        settlement.setSellerReleasedAmount(90.0);
        settlement.setBuyerRefundedAmount(5.0);
        settlement.setDisputeReason(null);

        assertEquals(finishedAt, settlement.getFinishedAt());
        assertEquals(finishedAt.plusMinutes(5), settlement.getWinnerAdmittedAt());
        assertEquals(finishedAt.plusHours(2), settlement.getShippedAt());
        assertEquals(deadline, settlement.getBuyerConfirmationDeadline());
        assertEquals(deadline.plusHours(1), settlement.getReleasedAt());
        assertEquals(deadline.plusHours(2), settlement.getDisputedAt());
        assertEquals(deadline.plusHours(3), settlement.getAdminDecisionAt());
        assertEquals(95.0, settlement.getLockedRemainingPayment());
        assertEquals(90.0, settlement.getSellerReleasedAmount());
        assertEquals(5.0, settlement.getBuyerRefundedAmount());
        assertEquals("", settlement.getDisputeReason());
        assertEquals("28/05/2026 09:00", settlement.getDisplayDeadline());
        assertFalse(settlement.isBuyerConfirmationExpired(deadline.minusNanos(1)));
        assertTrue(settlement.isBuyerConfirmationExpired(deadline));
        assertTrue(settlement.getDisplaySummary().contains("deadline=28/05/2026 09:00"));
        settlement.setBuyerConfirmationDeadline(LocalDateTime.now().minusSeconds(1));
        assertTrue(settlement.isBuyerConfirmationExpired(null));
    }
}
