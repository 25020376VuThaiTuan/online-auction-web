package org.example.auction;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class AuctionSettlement {
    private static final DateTimeFormatter DISPLAY_DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final String itemId;
    private final String itemName;
    private final String sellerId;
    private final String winnerBidderId;
    private final double winningBidAmount;
    private final double depositAmount;
    private final double buyerPremiumAmount;
    private final double totalBuyerDue;
    private final double remainingPaymentDue;
    private final double adminDepositShare;
    private final double sellerDepositShare;
    private final LocalDateTime finishedAt;

    private AuctionSettlementStatus status;
    private double lockedRemainingPayment;
    private double sellerReleasedAmount;
    private double buyerRefundedAmount;
    private LocalDateTime winnerAdmittedAt;
    private LocalDateTime shippedAt;
    private LocalDateTime buyerConfirmationDeadline;
    private LocalDateTime releasedAt;
    private LocalDateTime disputedAt;
    private LocalDateTime adminDecisionAt;
    private String disputeReason;

    public AuctionSettlement(
            String itemId,
            String itemName,
            String sellerId,
            String winnerBidderId,
            double winningBidAmount,
            double depositAmount,
            double buyerPremiumAmount,
            double totalBuyerDue,
            double remainingPaymentDue,
            double adminDepositShare,
            double sellerDepositShare,
            LocalDateTime finishedAt
    ) {
        this.itemId = itemId;
        this.itemName = itemName;
        this.sellerId = sellerId;
        this.winnerBidderId = winnerBidderId;
        this.winningBidAmount = winningBidAmount;
        this.depositAmount = depositAmount;
        this.buyerPremiumAmount = buyerPremiumAmount;
        this.totalBuyerDue = totalBuyerDue;
        this.remainingPaymentDue = remainingPaymentDue;
        this.adminDepositShare = adminDepositShare;
        this.sellerDepositShare = sellerDepositShare;
        this.finishedAt = finishedAt == null ? LocalDateTime.now() : finishedAt;
        this.status = AuctionSettlementStatus.AWAITING_WINNER_ADMISSION;
    }

    public String getItemId() {
        return itemId;
    }

    public String getItemName() {
        return itemName;
    }

    public String getSellerId() {
        return sellerId;
    }

    public String getWinnerBidderId() {
        return winnerBidderId;
    }

    public double getWinningBidAmount() {
        return winningBidAmount;
    }

    public double getDepositAmount() {
        return depositAmount;
    }

    public double getBuyerPremiumAmount() {
        return buyerPremiumAmount;
    }

    public double getTotalBuyerDue() {
        return totalBuyerDue;
    }

    public double getRemainingPaymentDue() {
        return remainingPaymentDue;
    }

    public double getAdminDepositShare() {
        return adminDepositShare;
    }

    public double getSellerDepositShare() {
        return sellerDepositShare;
    }

    public LocalDateTime getFinishedAt() {
        return finishedAt;
    }

    public AuctionSettlementStatus getStatus() {
        return status;
    }

    public void setStatus(AuctionSettlementStatus status) {
        this.status = status == null ? this.status : status;
    }

    public double getLockedRemainingPayment() {
        return lockedRemainingPayment;
    }

    public void setLockedRemainingPayment(double lockedRemainingPayment) {
        this.lockedRemainingPayment = Math.max(0.0, lockedRemainingPayment);
    }

    public double getSellerReleasedAmount() {
        return sellerReleasedAmount;
    }

    public void setSellerReleasedAmount(double sellerReleasedAmount) {
        this.sellerReleasedAmount = Math.max(0.0, sellerReleasedAmount);
    }

    public double getBuyerRefundedAmount() {
        return buyerRefundedAmount;
    }

    public void setBuyerRefundedAmount(double buyerRefundedAmount) {
        this.buyerRefundedAmount = Math.max(0.0, buyerRefundedAmount);
    }

    public LocalDateTime getWinnerAdmittedAt() {
        return winnerAdmittedAt;
    }

    public void setWinnerAdmittedAt(LocalDateTime winnerAdmittedAt) {
        this.winnerAdmittedAt = winnerAdmittedAt;
    }

    public LocalDateTime getShippedAt() {
        return shippedAt;
    }

    public void setShippedAt(LocalDateTime shippedAt) {
        this.shippedAt = shippedAt;
    }

    public LocalDateTime getBuyerConfirmationDeadline() {
        return buyerConfirmationDeadline;
    }

    public void setBuyerConfirmationDeadline(LocalDateTime buyerConfirmationDeadline) {
        this.buyerConfirmationDeadline = buyerConfirmationDeadline;
    }

    public LocalDateTime getReleasedAt() {
        return releasedAt;
    }

    public void setReleasedAt(LocalDateTime releasedAt) {
        this.releasedAt = releasedAt;
    }

    public LocalDateTime getDisputedAt() {
        return disputedAt;
    }

    public void setDisputedAt(LocalDateTime disputedAt) {
        this.disputedAt = disputedAt;
    }

    public LocalDateTime getAdminDecisionAt() {
        return adminDecisionAt;
    }

    public void setAdminDecisionAt(LocalDateTime adminDecisionAt) {
        this.adminDecisionAt = adminDecisionAt;
    }

    public String getDisputeReason() {
        return disputeReason == null ? "" : disputeReason;
    }

    public void setDisputeReason(String disputeReason) {
        this.disputeReason = disputeReason == null ? "" : disputeReason.trim();
    }

    public boolean isBuyerConfirmationExpired(LocalDateTime now) {
        LocalDateTime safeNow = now == null ? LocalDateTime.now() : now;
        return buyerConfirmationDeadline != null && !safeNow.isBefore(buyerConfirmationDeadline);
    }

    public String getDisplayStatus() {
        return status == null ? "N/A" : status.name().replace('_', ' ');
    }

    public String getDisplayDeadline() {
        return buyerConfirmationDeadline == null ? "N/A" : DISPLAY_DATE_TIME.format(buyerConfirmationDeadline);
    }

    public String getDisplaySummary() {
        String summary = itemName + " [" + getDisplayStatus() + "] winner=" + winnerBidderId
                + ", total due=" + formatAmount(totalBuyerDue)
                + ", remaining hold=" + formatAmount(lockedRemainingPayment);
        if (buyerConfirmationDeadline != null) {
            summary += ", deadline=" + getDisplayDeadline();
        }
        return summary;
    }

    private String formatAmount(double amount) {
        return String.format("$%,.2f", amount);
    }
}
