package org.example.service;

import org.example.auction.AuctionDepositResult;
import org.example.auction.AuctionRules;
import org.example.auction.AuctionSettlement;
import org.example.auction.AuctionSettlementStatus;
import org.example.auction.AuctionStatus;
import org.example.auction.AuctionSummary;
import org.example.auction.UserNotification;
import org.example.model.Bid;
import org.example.model.Bidder;
import org.example.model.Item;
import org.example.model.User;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class AuctionSettlementService {
    public static final double BUYER_PREMIUM_RATE = 0.03;

    private static final AuctionSettlementService INSTANCE = new AuctionSettlementService();

    private final AuthenticationService authenticationService = AuthenticationService.getInstance();
    private final WalletService walletService = WalletService.getInstance();
    private final Map<String, Map<String, Double>> depositsByAuctionId = new LinkedHashMap<>();
    private final Map<String, AuctionSettlement> settlementsByAuctionId = new LinkedHashMap<>();
    private final Map<String, List<UserNotification>> notificationsByUserId = new LinkedHashMap<>();
    private final Set<String> closedNoWinnerAuctionIds = new HashSet<>();

    private AuctionSettlementService() {
    }

    public static AuctionSettlementService getInstance() {
        return INSTANCE;
    }

    public synchronized AuctionDepositResult lockEntryDeposit(Item item, AuctionSummary summary, User user) {
        if (item == null || summary == null) {
            return AuctionDepositResult.rejected("Auction item was not found.", 0.0, 0.0, null);
        }
        if (!(user instanceof Bidder bidder)) {
            return AuctionDepositResult.rejected("Only bidder accounts can enter an auction.", 0.0, 0.0, summary.status());
        }
        if (summary.status() == AuctionStatus.FINISHED || summary.status() == AuctionStatus.PAID
                || summary.status() == AuctionStatus.CANCELLED) {
            return AuctionDepositResult.rejected("This auction is finished and cannot accept a new deposit.",
                    0.0, bidder.getLockedAmount(item.getId()), summary.status());
        }

        double requiredDeposit = AuctionRules.requiredDeposit(summary.currentPrice());
        double existingDeposit = lockedEntryDeposit(item.getId(), user);
        double additionalRequired = Math.max(0.0, requiredDeposit - existingDeposit);
        if (bidder.getAvailableBalance() < additionalRequired) {
            return AuctionDepositResult.rejected(
                    "Available balance is lower than the deposit required to enter this auction.",
                    requiredDeposit,
                    existingDeposit,
                    summary.status()
            );
        }

        walletService.lockDeposit(bidder, item.getId(), requiredDeposit, item.getId(),
                "Entry deposit locked for " + item.getItemName() + ".");
        depositsByAuctionId
                .computeIfAbsent(item.getId(), ignored -> new LinkedHashMap<>())
                .put(user.getId(), requiredDeposit);
        notifyUser(user.getId(), "Deposit locked",
                "Deposit " + formatAmount(requiredDeposit) + " locked for " + item.getItemName() + ".");

        String message = existingDeposit > 0.0
                ? "Auction entry confirmed. Deposit hold was updated."
                : "Auction entry confirmed. Deposit is now locked until the auction finishes.";
        return AuctionDepositResult.accepted(message, requiredDeposit, requiredDeposit, summary.status());
    }

    public synchronized boolean hasEntryDeposit(String itemId, User user) {
        return lockedEntryDeposit(itemId, user) > 0.0;
    }

    public synchronized double lockedEntryDeposit(String itemId, User user) {
        if (itemId == null || itemId.isBlank() || !(user instanceof Bidder bidder)) {
            return 0.0;
        }

        double recorded = depositsByAuctionId
                .getOrDefault(itemId, Map.of())
                .getOrDefault(user.getId(), 0.0);
        return Math.max(recorded, bidder.getLockedAmount(itemId));
    }

    public synchronized Optional<AuctionSettlement> finalizeAuction(
            Item item,
            AuctionSummary summary,
            List<Bid> bidHistory
    ) {
        if (item == null || summary == null || summary.status() != AuctionStatus.FINISHED) {
            return Optional.empty();
        }
        if (settlementsByAuctionId.containsKey(item.getId())) {
            return Optional.of(settlementsByAuctionId.get(item.getId()));
        }
        if (closedNoWinnerAuctionIds.contains(item.getId())) {
            return Optional.empty();
        }

        String winnerId = summary.highestBidderId();
        Map<String, Double> deposits = depositsByAuctionId.getOrDefault(item.getId(), Map.of());
        Set<String> participantIds = participantIds(deposits, bidHistory);
        if (winnerId == null || winnerId.isBlank() || bidHistory == null || bidHistory.isEmpty()) {
            releaseLosingDeposits(item, deposits, null);
            notifyParticipantsWithoutWinner(item, participantIds);
            closedNoWinnerAuctionIds.add(item.getId());
            notifyUser(item.getSellerId(), "Auction finished", item.getItemName() + " finished with no winning bidder.");
            return Optional.empty();
        }

        releaseLosingDeposits(item, deposits, winnerId);
        notifyLosingParticipants(item, participantIds, winnerId);
        double capturedDeposit = captureWinnerDeposit(item, winnerId, deposits.getOrDefault(winnerId, 0.0));
        double winningBid = roundCurrency(summary.currentPrice());
        double buyerPremium = roundCurrency(winningBid * BUYER_PREMIUM_RATE);
        double totalBuyerDue = roundCurrency(winningBid + buyerPremium);
        double remainingDue = roundCurrency(Math.max(0.0, totalBuyerDue - capturedDeposit));
        double sellerDepositShare = roundCurrency(Math.min(capturedDeposit, winningBid));
        double adminDepositShare = roundCurrency(Math.max(0.0, capturedDeposit - sellerDepositShare));

        AuctionSettlement settlement = new AuctionSettlement(
                item.getId(),
                item.getItemName(),
                item.getSellerId(),
                winnerId,
                winningBid,
                capturedDeposit,
                buyerPremium,
                totalBuyerDue,
                remainingDue,
                adminDepositShare,
                sellerDepositShare,
                LocalDateTime.now()
        );
        settlementsByAuctionId.put(item.getId(), settlement);

        notifyUser(winnerId, "Auction result ready",
                "You have won this session. You won " + item.getItemName()
                        + ". Admit the result to continue. Total due is "
                        + formatAmount(totalBuyerDue) + ", with " + formatAmount(capturedDeposit)
                        + " already paid by deposit.");
        notifyUser(item.getSellerId(), "Auction finished",
                item.getItemName() + " has a winner. Wait for buyer admission before confirming the item was sent.");
        notifyAdmins("Auction finished",
                item.getItemName() + " is locked. Seller payout will be " + formatAmount(winningBid)
                        + " and admin fee will be " + formatAmount(buyerPremium)
                        + " after the buyer confirms receipt.");
        return Optional.of(settlement);
    }

    public synchronized Optional<AuctionSettlement> getSettlement(String itemId) {
        return Optional.ofNullable(settlementsByAuctionId.get(itemId));
    }

    public synchronized List<AuctionSettlement> getAllSettlements() {
        return settlementsByAuctionId.values().stream()
                .sorted(Comparator.comparing(AuctionSettlement::getFinishedAt).reversed())
                .toList();
    }

    public synchronized List<UserNotification> getNotificationsFor(User user) {
        if (user == null) {
            return List.of();
        }
        return notificationsByUserId.getOrDefault(user.getId(), List.of()).stream()
                .sorted(Comparator.comparing(UserNotification::getCreatedAt).reversed())
                .toList();
    }

    public synchronized AuctionSettlement admitWinnerResult(String itemId, User user) {
        AuctionSettlement settlement = requireSettlement(itemId);
        requireWinner(settlement, user);
        if (settlement.getStatus() != AuctionSettlementStatus.AWAITING_WINNER_ADMISSION) {
            throw new IllegalStateException("The result has already moved past winner admission.");
        }

        settlement.setWinnerAdmittedAt(LocalDateTime.now());
        settlement.setStatus(AuctionSettlementStatus.AWAITING_SELLER_CONFIRMATION);
        notifyUser(settlement.getSellerId(), "Buyer admitted result",
                "The winner admitted " + settlement.getItemName() + ". Confirm that the item was sent.");
        return settlement;
    }

    public synchronized AuctionSettlement markGoodsShipped(String itemId, User sellerOrAdmin) {
        AuctionSettlement settlement = requireSettlement(itemId);
        requireSellerOrAdmin(settlement, sellerOrAdmin);
        if (settlement.getStatus() != AuctionSettlementStatus.AWAITING_SELLER_CONFIRMATION) {
            throw new IllegalStateException("The seller can only confirm sent after the winner admits the result.");
        }

        double remainingDue = settlement.getRemainingPaymentDue();
        if (remainingDue > 0.0) {
            Bidder buyer = findBidder(settlement.getWinnerBidderId())
                    .orElseThrow(() -> new IllegalStateException("Winning bidder account was not found."));
            if (buyer.getAvailableBalance() < remainingDue) {
                notifyAdmins("Payment hold failed",
                        "Buyer " + buyer.getUsername() + " cannot cover " + formatAmount(remainingDue)
                                + " for " + settlement.getItemName() + ".");
                throw new IllegalStateException("Buyer does not have enough available balance for the remaining payment hold.");
            }
            walletService.lockDeposit(buyer, paymentHoldKey(itemId), remainingDue, itemId,
                    "Remaining payment locked for " + settlement.getItemName() + ".");
            settlement.setLockedRemainingPayment(remainingDue);
        }

        settlement.setShippedAt(LocalDateTime.now());
        settlement.setStatus(AuctionSettlementStatus.AWAITING_BUYER_CONFIRMATION);
        notifyUser(settlement.getWinnerBidderId(), "Item sent",
                "Seller confirmed " + settlement.getItemName() + " was sent. Confirm receipt to release payment.");
        notifyUser(settlement.getSellerId(), "Sent confirmed",
                "Remaining buyer payment is locked until the buyer confirms receipt for " + settlement.getItemName() + ".");
        return settlement;
    }

    public synchronized AuctionSettlement confirmGoodsReceived(String itemId, User user) {
        AuctionSettlement settlement = requireSettlement(itemId);
        requireWinner(settlement, user);
        if (settlement.getStatus() != AuctionSettlementStatus.AWAITING_BUYER_CONFIRMATION) {
            throw new IllegalStateException("Payment can only be confirmed while goods are awaiting buyer confirmation.");
        }
        releaseRemainingPaymentToSeller(settlement, "Buyer confirmed receipt.");
        return settlement;
    }

    public synchronized AuctionSettlement reportGoodsNotReceived(String itemId, User user, String reason) {
        throw new IllegalStateException("Delivery disputes are no longer supported. Confirm sent and received to complete payment.");
    }

    public synchronized AuctionSettlement adminUnfreezeRemainingPayment(String itemId, User admin) {
        throw new IllegalStateException("Admin settlement overrides are no longer supported.");
    }

    public synchronized AuctionSettlement adminKeepRemainingPaymentFrozen(String itemId, User admin) {
        throw new IllegalStateException("Admin settlement overrides are no longer supported.");
    }

    public synchronized void releaseExpiredBuyerConfirmations() {
        // Payment release is now driven only by seller/buyer confirmations.
    }

    private void releaseLosingDeposits(Item item, Map<String, Double> deposits, String winnerId) {
        for (String bidderId : deposits.keySet()) {
            if (winnerId != null && winnerId.equals(bidderId)) {
                continue;
            }
            findBidder(bidderId).ifPresent(bidder -> {
                double amount = walletService.releaseLockedDeposit(
                        bidder,
                        item.getId(),
                        deposits.getOrDefault(bidderId, 0.0),
                        item.getId(),
                        "Entry deposit released for " + item.getItemName() + ".");
                notifyUser(bidderId, "Deposit released",
                        "Deposit " + formatAmount(amount) + " released for " + item.getItemName() + ".");
            });
        }
    }

    private Set<String> participantIds(Map<String, Double> deposits, List<Bid> bidHistory) {
        Set<String> participantIds = new LinkedHashSet<>();
        if (deposits != null) {
            participantIds.addAll(deposits.keySet());
        }
        if (bidHistory != null) {
            for (Bid bid : bidHistory) {
                if (bid.getBidderId() != null && !bid.getBidderId().isBlank()) {
                    participantIds.add(bid.getBidderId());
                }
            }
        }
        return participantIds;
    }

    private void notifyParticipantsWithoutWinner(Item item, Set<String> participantIds) {
        for (String participantId : participantIds) {
            notifyUser(participantId, "Auction finished",
                    item.getItemName() + " has finished. No winning bid was submitted. Any entry deposit has been released.");
        }
    }

    private void notifyLosingParticipants(Item item, Set<String> participantIds, String winnerId) {
        for (String participantId : participantIds) {
            if (winnerId != null && winnerId.equals(participantId)) {
                continue;
            }
            notifyUser(participantId, "Auction finished",
                    item.getItemName() + " has finished. Another bidder won this session. Any entry deposit has been released.");
        }
    }

    private double captureWinnerDeposit(Item item, String winnerId, double recordedDeposit) {
        Optional<Bidder> bidder = findBidder(winnerId);
        double safeRecordedDeposit = roundCurrency(recordedDeposit);
        if (bidder.isEmpty()) {
            return safeRecordedDeposit;
        }
        Bidder winner = bidder.get();
        double captured = walletService.captureLockedDeposit(
                winner,
                item.getId(),
                safeRecordedDeposit,
                "PAYMENT",
                item.getId(),
                "Winner deposit captured for " + item.getItemName() + "."
        );
        return roundCurrency(Math.max(captured, safeRecordedDeposit));
    }

    private void releaseRemainingPaymentToSeller(AuctionSettlement settlement, String reason) {
        captureBuyerPaymentHold(settlement);
        double sellerPayout = roundCurrency(settlement.getWinningBidAmount());
        double adminFee = roundCurrency(settlement.getBuyerPremiumAmount());
        settlement.setSellerReleasedAmount(sellerPayout);
        settlement.setLockedRemainingPayment(0.0);
        settlement.setReleasedAt(LocalDateTime.now());
        settlement.setStatus(AuctionSettlementStatus.PAYMENT_RELEASED);
        if (sellerPayout > 0.0) {
            creditSeller(settlement, sellerPayout, "Seller payout released for " + settlement.getItemName() + ".");
        }
        if (adminFee > 0.0) {
            creditAdmin(settlement, adminFee, "Admin fee released for " + settlement.getItemName() + ".");
        }
        notifyUser(settlement.getSellerId(), "Payment released",
                reason + " Seller receives " + formatAmount(sellerPayout) + " for " + settlement.getItemName() + ".");
        notifyUser(settlement.getWinnerBidderId(), "Payment completed",
                "Payment completed for " + settlement.getItemName() + ". Total charged was "
                        + formatAmount(settlement.getTotalBuyerDue()) + ".");
        notifyAdmins("Payment released",
                settlement.getItemName() + " payment released to seller with admin fee " + formatAmount(adminFee) + ".");
    }

    private void creditSeller(AuctionSettlement settlement, double amount, String note) {
        User seller = findUser(settlement.getSellerId())
                .orElseThrow(() -> new IllegalStateException("Seller payout account was not found."));
        walletService.recordSystemEvent(seller, "SELLER_PAYOUT", amount, settlement.getItemId(), note);
    }

    private void creditAdmin(AuctionSettlement settlement, double amount, String note) {
        User admin = findAdmin()
                .orElseThrow(() -> new IllegalStateException("Admin payout account was not found."));
        walletService.recordSystemEvent(admin, "ADMIN_FEE", amount, settlement.getItemId(), note);
        notifyUser(admin.getId(), "Admin fee received",
                "Admin fee " + formatAmount(amount) + " released for " + settlement.getItemName() + ".");
    }

    private double captureBuyerPaymentHold(AuctionSettlement settlement) {
        double recordedHold = roundCurrency(settlement.getLockedRemainingPayment());
        return findBidder(settlement.getWinnerBidderId())
                .map(bidder -> {
                    double captured = walletService.captureLockedDeposit(
                            bidder,
                            paymentHoldKey(settlement.getItemId()),
                            recordedHold,
                            "PAYMENT",
                            settlement.getItemId(),
                            "Remaining payment captured for " + settlement.getItemName() + "."
                    );
                    return roundCurrency(Math.max(captured, recordedHold));
                })
                .orElse(recordedHold);
    }

    private double releaseRemainingPaymentToBuyer(AuctionSettlement settlement) {
        double recordedHold = roundCurrency(settlement.getLockedRemainingPayment());
        Optional<Bidder> bidder = findBidder(settlement.getWinnerBidderId());
        if (bidder.isEmpty()) {
            settlement.setLockedRemainingPayment(0.0);
            return recordedHold;
        }

        double amount = walletService.releaseLockedDeposit(
                bidder.get(),
                paymentHoldKey(settlement.getItemId()),
                recordedHold,
                "REFUND",
                settlement.getItemId(),
                "Remaining payment hold released for " + settlement.getItemName() + "."
        );
        settlement.setLockedRemainingPayment(0.0);
        return roundCurrency(Math.max(amount, recordedHold));
    }

    private AuctionSettlement requireSettlement(String itemId) {
        AuctionSettlement settlement = settlementsByAuctionId.get(itemId);
        if (settlement == null) {
            throw new IllegalStateException("No finished auction settlement exists for this item yet.");
        }
        return settlement;
    }

    private void requireWinner(AuctionSettlement settlement, User user) {
        if (user == null || !settlement.getWinnerBidderId().equals(user.getId())) {
            throw new IllegalStateException("Only the winning buyer can perform this action.");
        }
    }

    private void requireSellerOrAdmin(AuctionSettlement settlement, User user) {
        if (user == null) {
            throw new IllegalStateException("Authentication required.");
        }
        boolean ownsItem = settlement.getSellerId().equals(user.getId());
        boolean admin = "ADMIN".equalsIgnoreCase(user.getRole());
        if (!ownsItem && !admin) {
            throw new IllegalStateException("Only the seller or admin can perform this action.");
        }
    }

    private void requireAdmin(User user) {
        if (user == null || !"ADMIN".equalsIgnoreCase(user.getRole())) {
            throw new IllegalStateException("Admin role required.");
        }
    }

    private Optional<Bidder> findBidder(String userId) {
        return authenticationService.findById(userId)
                .filter(Bidder.class::isInstance)
                .map(Bidder.class::cast);
    }

    private Optional<User> findUser(String userId) {
        return authenticationService.findById(userId);
    }

    private Optional<User> findAdmin() {
        return authenticationService.getAllUsers().stream()
                .filter(user -> "ADMIN".equalsIgnoreCase(user.getRole()))
                .findFirst();
    }

    private void notifyAdmins(String title, String body) {
        for (User user : authenticationService.getAllUsers()) {
            if ("ADMIN".equalsIgnoreCase(user.getRole())) {
                notifyUser(user.getId(), title, body);
            }
        }
    }

    private void notifyUser(String userId, String title, String body) {
        if (userId == null || userId.isBlank()) {
            return;
        }
        notificationsByUserId
                .computeIfAbsent(userId, ignored -> new ArrayList<>())
                .add(new UserNotification(userId, "AUCTION", title, body, LocalDateTime.now()));
    }

    private String paymentHoldKey(String itemId) {
        return itemId + ":payment";
    }

    private String formatAmount(double amount) {
        return String.format("$%,.2f", amount);
    }

    private double roundCurrency(double amount) {
        return Math.round(amount * 100.0) / 100.0;
    }
}
