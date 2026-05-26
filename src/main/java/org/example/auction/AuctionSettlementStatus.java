package org.example.auction;

public enum AuctionSettlementStatus {
    AWAITING_WINNER_ADMISSION,
    AWAITING_SELLER_CONFIRMATION,
    AWAITING_SELLER_SHIPPING,
    AWAITING_BUYER_CONFIRMATION,
    PAYMENT_RELEASED,
    DISPUTED_NOT_RECEIVED,
    PAYMENT_UNFROZEN_BY_ADMIN,
    PAYMENT_FROZEN_BY_ADMIN,
    CANCELLED_NO_WINNER;

    public boolean isClosed() {
        return this == PAYMENT_RELEASED
                || this == PAYMENT_UNFROZEN_BY_ADMIN
                || this == PAYMENT_FROZEN_BY_ADMIN
                || this == CANCELLED_NO_WINNER;
    }
}
