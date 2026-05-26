package org.example.service;

import org.example.auction.AuctionStatus;
import org.example.auction.AuctionSummary;
import org.example.auction.BidValidationResult;
import org.example.model.Bidder;
import org.example.model.Item;
import org.example.model.ItemFactory;
import org.example.model.Seller;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BidAuthorizationPolicyTest {
    private final BidAuthorizationPolicy policy = new BidAuthorizationPolicy();

    @Test
    void defaultPolicyReturnsSingletonInstance() {
        assertSame(BidAuthorizationPolicy.defaultPolicy(), BidAuthorizationPolicy.defaultPolicy());
    }

    @Test
    void findFailureRejectsMissingUserCreatorMissingDepositAndInsufficientFunds() {
        LocalDateTime endTime = LocalDateTime.of(2026, 5, 27, 10, 0);
        Item item = item(endTime);
        item.setSellerId("SELLER-1");
        Seller seller = new Seller("SELLER-1", "seller", "hash", "seller@test.local");
        Bidder bidder = new Bidder("BIDDER-1", "bidder", "hash", "bidder@test.local", 100.0);

        assertFailure("Authentication required", policy.findFailure(item, null, null, 120.0, true, 200.0));
        assertFailure("creators cannot bid", policy.findFailure(item, null, seller, 120.0, true, 200.0));
        assertFailure("Confirm auction entry", policy.findFailure(item, null, bidder, 120.0, false, 200.0));
        assertFailure("Available balance", policy.findFailure(item, null, bidder, 120.0, true, 119.99));
    }

    @Test
    void findFailureUsesSummaryValuesWhenProvided() {
        Bidder bidder = new Bidder("BIDDER-1", "bidder", "hash", "bidder@test.local", 100.0);
        AuctionSummary summary = new AuctionSummary(
                "ITEM-1",
                "Camera",
                AuctionStatus.RUNNING,
                250.0,
                300.0,
                60L,
                2,
                "BIDDER-2"
        );

        BidValidationResult result = policy.findFailure(null, summary, null, 320.0, true, 500.0).orElseThrow();

        assertEquals(250.0, result.currentPrice(), 0.001);
        assertEquals(300.0, result.minimumAllowedBid(), 0.001);
        assertEquals(AuctionStatus.RUNNING, result.status());

        assertTrue(policy.findFailure(null, summary, bidder, 320.0, true, 500.0).isEmpty());
    }

    @Test
    void successfulAuthorizationReturnsEmptyAndFailureOrNullMirrorsOptional() {
        Item item = item(LocalDateTime.of(2026, 5, 27, 10, 0));
        Bidder bidder = new Bidder("BIDDER-1", "bidder", "hash", "bidder@test.local", 100.0);

        Optional<BidValidationResult> success = policy.findFailure(item, null, bidder, 50.0, true, 50.0);

        assertTrue(success.isEmpty());
        assertNull(policy.failureOrNull(item, null, bidder, 50.0, true, 50.0));
        assertFalse(policy.failureOrNull(item, null, null, 50.0, true, 50.0).accepted());
    }

    @Test
    void nonFiniteBidAmountsDoNotTripBalanceAuthorization() {
        Item item = item(LocalDateTime.of(2026, 5, 27, 10, 0));
        Bidder bidder = new Bidder("BIDDER-1", "bidder", "hash", "bidder@test.local", 100.0);

        assertTrue(policy.findFailure(item, null, bidder, Double.NaN, true, 0.0).isEmpty());
        assertTrue(policy.findFailure(item, null, bidder, Double.POSITIVE_INFINITY, true, 0.0).isEmpty());
        assertTrue(policy.findFailure(item, null, bidder, -1.0, true, 0.0).isEmpty());
    }

    private static void assertFailure(String messagePart, Optional<BidValidationResult> failure) {
        BidValidationResult result = failure.orElseThrow();
        assertFalse(result.accepted());
        assertTrue(result.message().contains(messagePart));
    }

    private static Item item(LocalDateTime endTime) {
        return ItemFactory.createItem(
                "electronics",
                "ITEM-" + UUID.randomUUID(),
                "Camera",
                "Mirrorless",
                100.0,
                endTime.minusHours(1),
                endTime,
                "Brand",
                12
        );
    }
}
