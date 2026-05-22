package org.example.server;

import org.example.auction.AuctionRules;
import org.example.auction.AuctionSettlement;
import org.example.auction.AuctionStatus;
import org.example.auction.AuctionSummary;
import org.example.auction.UserNotification;
import org.example.model.Bid;
import org.example.model.Bidder;
import org.example.model.Item;
import org.example.model.User;
import org.example.model.WalletAuthorization;
import org.example.model.WalletLinkedAccount;
import org.example.model.WalletRecoveryResult;
import org.example.model.WalletSummary;
import org.example.model.WalletTransaction;
import org.example.service.AuctionWorkflowService;
import org.example.service.MarketplaceDashboardService;
import org.example.service.WalletService;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class ApiPayloadFactory {
    private static final DateTimeFormatter ISO_DATE_TIME = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final AuctionWorkflowService workflowService;
    private final MarketplaceDashboardService dashboardService;
    private final WalletService walletService = WalletService.getInstance();

    ApiPayloadFactory(AuctionWorkflowService workflowService, MarketplaceDashboardService dashboardService) {
        this.workflowService = Objects.requireNonNull(workflowService, "workflowService");
        this.dashboardService = Objects.requireNonNull(dashboardService, "dashboardService");
    }

    Map<String, Object> auth(ApiSessionService.SessionState session) {
        return jsonObject(
                "token", session.token(),
                "createdAt", session.createdAt().toString(),
                "expiresAt", session.expiresAt().toString(),
                "user", user(session.user())
        );
    }

    List<Map<String, Object>> bidHistory(String itemId) {
        List<Map<String, Object>> bids = new ArrayList<>();
        for (Bid bid : workflowService.getBidHistory(itemId)) {
            bids.add(bid(bid));
        }
        return bids;
    }

    Map<String, Object> latestBid(String itemId) {
        List<Bid> bids = workflowService.getBidHistory(itemId);
        if (bids.isEmpty()) {
            return null;
        }
        return bid(bids.get(bids.size() - 1));
    }

    Map<String, Object> bid(Bid bid) {
        return jsonObject(
                "id", bid.getId(),
                "itemId", bid.getItemId(),
                "bidderId", bid.getBidderId(),
                "amount", bid.getAmount(),
                "bidTime", formatDateTime(bid.getBidTime())
        );
    }

    Map<String, Object> auction(String itemId) {
        return auction(itemId, null);
    }

    Map<String, Object> auction(String itemId, User user) {
        Item item = workflowService.findItemById(itemId)
                .orElseThrow(() -> new ApiResourceNotFoundException("Auction not found: " + itemId));
        AuctionSummary summary = workflowService.getSummary(itemId);
        double requiredDeposit = AuctionRules.requiredDeposit(summary.currentPrice());
        boolean depositConfirmed = user != null && dashboardService.hasConfirmedEntryDeposit(itemId, user);
        Bidder bidder = user instanceof Bidder typedBidder ? typedBidder : null;
        WalletSummary walletSummary = user == null ? null : safeWalletSnapshot(user, bidder);
        double availableBalance = walletSummary == null ? 0.0 : walletSummary.availableBalance();
        boolean creator = user != null && item.getSellerId() != null
                && item.getSellerId().equalsIgnoreCase(user.getId());
        boolean eligible = !creator && !summary.status().isFinished()
                && (depositConfirmed || availableBalance >= requiredDeposit);

        return jsonObject(
                "itemId", item.getId(),
                "itemName", item.getItemName(),
                "description", item.getDescription(),
                "itemType", item.getClass().getSimpleName(),
                "status", summary.status().name(),
                "startingPrice", item.getStartingPrice(),
                "currentPrice", summary.currentPrice(),
                "minimumNextBid", summary.minimumNextBid(),
                "requiredDeposit", requiredDeposit,
                "availableBalance", availableBalance,
                "depositConfirmed", depositConfirmed,
                "eligible", eligible,
                "creator", creator,
                "secondsRemaining", summary.secondsRemaining(),
                "acceptingBids", summary.status() == AuctionStatus.RUNNING,
                "totalBids", summary.totalBids(),
                "highestBidderId", summary.highestBidderId(),
                "startTime", formatDateTime(item.getStartTime()),
                "endTime", formatDateTime(item.getEndTime()),
                "displayEndTime", item.getEndTimeString()
        );
    }

    Map<String, Object> item(Item item) {
        return jsonObject(
                "id", item.getId(),
                "itemId", item.getId(),
                "itemName", item.getItemName(),
                "description", item.getDescription(),
                "itemType", item.getClass().getSimpleName(),
                "startingPrice", item.getStartingPrice(),
                "currentPrice", item.getCurrentPrice(),
                "sellerId", item.getSellerId(),
                "approvalStatus", item.getApprovalStatus().name(),
                "startTime", formatDateTime(item.getStartTime()),
                "endTime", formatDateTime(item.getEndTime()),
                "displayEndTime", item.getEndTimeString()
        );
    }

    Map<String, Object> settlement(AuctionSettlement settlement) {
        if (settlement == null) {
            return null;
        }
        return jsonObject(
                "itemId", settlement.getItemId(),
                "itemName", settlement.getItemName(),
                "sellerId", settlement.getSellerId(),
                "winnerBidderId", settlement.getWinnerBidderId(),
                "status", settlement.getStatus().name(),
                "winningBidAmount", settlement.getWinningBidAmount(),
                "depositAmount", settlement.getDepositAmount(),
                "buyerPremiumAmount", settlement.getBuyerPremiumAmount(),
                "totalBuyerDue", settlement.getTotalBuyerDue(),
                "remainingPaymentDue", settlement.getRemainingPaymentDue(),
                "adminCommission", settlement.getAdminCommission(),
                "sellerPayout", settlement.getSellerPayout(),
                "adminDepositShare", settlement.getAdminCommission(),
                "sellerDepositShare", settlement.getSellerPayout(),
                "lockedRemainingPayment", settlement.getLockedRemainingPayment(),
                "sellerReleasedAmount", settlement.getSellerReleasedAmount(),
                "buyerRefundedAmount", settlement.getBuyerRefundedAmount(),
                "buyerConfirmationDeadline", formatDateTime(settlement.getBuyerConfirmationDeadline()),
                "displaySummary", settlement.getDisplaySummary()
        );
    }

    Map<String, Object> notification(UserNotification notification) {
        return jsonObject(
                "type", notification.getType(),
                "title", notification.getTitle(),
                "body", notification.getBody(),
                "createdAt", formatDateTime(notification.getCreatedAt()),
                "read", notification.isRead(),
                "displayText", notification.getDisplayText()
        );
    }

    Map<String, Object> wallet(WalletSummary summary) {
        if (summary == null) {
            return null;
        }
        List<Map<String, Object>> transactions = new ArrayList<>();
        for (WalletTransaction transaction : summary.transactions()) {
            transactions.add(walletTransaction(transaction));
        }
        List<Map<String, Object>> linkedAccounts = new ArrayList<>();
        for (WalletLinkedAccount account : summary.linkedAccounts()) {
            linkedAccounts.add(walletLinkedAccount(account));
        }
        return jsonObject(
                "userId", summary.userId(),
                "balance", summary.balance(),
                "lockedBalance", summary.lockedBalance(),
                "availableBalance", summary.availableBalance(),
                "pinSet", summary.pinSet(),
                "linkedAccounts", linkedAccounts,
                "transactions", transactions
        );
    }

    Map<String, Object> walletRecovery(WalletRecoveryResult result) {
        Map<String, Object> payload = jsonObject(
                "accepted", result.accepted(),
                "message", result.message(),
                "email", result.email()
        );
        if (Boolean.getBoolean("auction.dev.exposeRecoveryCode")) {
            payload.put("recoveryCode", result.recoveryCode());
        }
        return payload;
    }

    Map<String, Object> walletAuthorization(WalletAuthorization authorization) {
        if (authorization == null) {
            return null;
        }
        return jsonObject(
                "token", authorization.token(),
                "expiresAt", formatDateTime(authorization.expiresAt())
        );
    }

    Map<String, Object> user(User user) {
        Bidder bidder = user instanceof Bidder typedBidder ? typedBidder : null;
        WalletSummary walletSummary = safeWalletSnapshot(user, bidder);
        double balance = walletSummary == null ? fallbackBalance(bidder) : walletSummary.balance();
        double lockedBalance = walletSummary == null ? fallbackLockedBalance(bidder) : walletSummary.lockedBalance();
        double availableBalance = walletSummary == null
                ? Math.max(0.0, balance - lockedBalance)
                : walletSummary.availableBalance();
        Map<String, Double> lockedDeposits = bidder == null ? Map.of() : bidder.getLockedDepositsByAuctionId();

        return jsonObject(
                "id", user.getId(),
                "username", user.getUsername(),
                "email", user.getEmail(),
                "role", user.getRole(),
                "fullName", user.getFullName(),
                "phoneNumber", user.getPhoneNumber(),
                "address", user.getAddress(),
                "avatarUrl", user.getAvatarUrl(),
                "balance", balance,
                "lockedBalance", lockedBalance,
                "availableBalance", availableBalance,
                "lockedDeposits", lockedDeposits,
                "wallet", wallet(walletSummary)
        );
    }

    Map<String, Object> userListItem(User user) {
        Bidder bidder = user instanceof Bidder typedBidder ? typedBidder : null;
        double balance = fallbackBalance(bidder);
        double lockedBalance = fallbackLockedBalance(bidder);
        double availableBalance = Math.max(0.0, balance - lockedBalance);
        Map<String, Double> lockedDeposits = bidder == null ? Map.of() : bidder.getLockedDepositsByAuctionId();

        return jsonObject(
                "id", user.getId(),
                "username", user.getUsername(),
                "email", user.getEmail(),
                "role", user.getRole(),
                "fullName", user.getFullName(),
                "phoneNumber", user.getPhoneNumber(),
                "address", user.getAddress(),
                "avatarUrl", user.getAvatarUrl(),
                "balance", balance,
                "lockedBalance", lockedBalance,
                "availableBalance", availableBalance,
                "lockedDeposits", lockedDeposits
        );
    }

    Map<String, Object> walletTransaction(WalletTransaction transaction) {
        return jsonObject(
                "id", transaction.id(),
                "userId", transaction.userId(),
                "transactionType", transaction.transactionType(),
                "amount", transaction.amount(),
                "balanceBefore", transaction.balanceBefore(),
                "balanceAfter", transaction.balanceAfter(),
                "referenceId", transaction.referenceId(),
                "note", transaction.note(),
                "createdAt", formatDateTime(transaction.createdAt())
        );
    }

    private Map<String, Object> walletLinkedAccount(WalletLinkedAccount account) {
        return jsonObject(
                "id", account.id(),
                "userId", account.userId(),
                "accountName", account.accountName(),
                "providerName", account.providerName(),
                "accountReference", account.accountReference(),
                "maskedReference", account.maskedReference(),
                "balance", account.balance(),
                "primary", account.primary(),
                "displayName", account.displayName(),
                "createdAt", formatDateTime(account.createdAt())
        );
    }

    private WalletSummary safeWalletSnapshot(User user, Bidder bidder) {
        try {
            return walletService.getWalletSnapshot(user);
        } catch (IllegalStateException exception) {
            String message = exception.getMessage();
            boolean persistenceGap = message != null
                    && (message.contains("Wallet database sync failed")
                    || message.contains("authenticated persisted user"));
            if (!persistenceGap) {
                throw exception;
            }
            if (user == null || user.getId() == null || user.getId().isBlank()) {
                throw exception;
            }
            return bidder == null
                    ? null
                    : new WalletSummary(
                            user.getId(),
                            bidder.getBalance(),
                            bidder.getLockedBalance(),
                            bidder.getAvailableBalance(),
                            false,
                            List.of(),
                            List.of()
                    );
        }
    }

    private double fallbackBalance(Bidder bidder) {
        return bidder == null ? 0.0 : bidder.getBalance();
    }

    private double fallbackLockedBalance(Bidder bidder) {
        return bidder == null ? 0.0 : bidder.getLockedBalance();
    }

    private String formatDateTime(LocalDateTime value) {
        return value == null ? null : ISO_DATE_TIME.format(value);
    }

    private Map<String, Object> jsonObject(Object... fields) {
        Map<String, Object> payload = new LinkedHashMap<>();
        for (int index = 0; index < fields.length; index += 2) {
            payload.put(String.valueOf(fields[index]), fields[index + 1]);
        }
        return payload;
    }
}
