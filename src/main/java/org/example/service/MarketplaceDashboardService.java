package org.example.service;

import org.example.auction.AuctionRules;
import org.example.auction.AuctionDepositResult;
import org.example.auction.AuctionSettlement;
import org.example.auction.AuctionStatus;
import org.example.auction.AuctionSummary;
import org.example.auction.BidValidationResult;
import org.example.auction.UserNotification;
import org.example.model.ApprovalStatus;
import org.example.model.Bid;
import org.example.model.Bidder;
import org.example.model.Item;
import org.example.model.User;
import org.example.model.WalletAuthorization;
import org.example.model.WalletRecoveryResult;
import org.example.model.WalletSummary;
import org.example.model.WalletTransaction;
import org.example.viewmodel.AuctionEligibilityEntry;

import java.time.LocalDateTime;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class MarketplaceDashboardService {
    private static final MarketplaceDashboardService INSTANCE = new MarketplaceDashboardService();

    private final AuthenticationService authenticationService = AuthenticationService.getInstance();
    private final AuctionWorkflowService auctionWorkflowService = AuctionWorkflowService.getInstance();
    private final AuctionSettlementService settlementService = AuctionSettlementService.getInstance();
    private final WalletService walletService = WalletService.getInstance();

    private MarketplaceDashboardService() {
    }

    public static MarketplaceDashboardService getInstance() {
        return INSTANCE;
    }

    public User registerManualBidder(String username, String password, String email, String fullName) {
        return authenticationService.registerManualBidder(username, password, email, fullName);
    }

    public User registerManualSeller(String username, String password, String email, String fullName) {
        return authenticationService.registerManualSeller(username, password, email, fullName);
    }

    public void updateProfile(User user, String fullName, String phoneNumber, String address) {
        user.setFullName(fullName);
        user.setPhoneNumber(phoneNumber);
        user.setAddress(address);
        authenticationService.updateUser(user);
    }

    public void updateAvatar(User user, String avatarUrl) {
        user.setAvatarUrl(avatarUrl);
        authenticationService.updateUser(user);
    }

    public List<AuctionEligibilityEntry> getAuctionEligibilityEntries(User user) {
        synchronizeAuctionOutcomes();
        List<AuctionEligibilityEntry> entries = new ArrayList<>();
        double availableBalance = user instanceof Bidder bidder ? bidder.getAvailableBalance() : 0.0;

        for (Item item : auctionWorkflowService.getAllItems()) {
            if (!item.isApproved()) {
                continue;
            }
            AuctionSummary summary = auctionWorkflowService.getSummary(item.getId());
            double requiredDeposit = AuctionRules.requiredDeposit(summary.currentPrice());
            double minimumBid = summary.minimumNextBid();
            boolean hasDeposit = settlementService.hasEntryDeposit(item.getId(), user);
            boolean canEnter = !summary.status().isFinished() && (hasDeposit || availableBalance >= requiredDeposit);
            entries.add(new AuctionEligibilityEntry(
                    item.getId(),
                    item.getItemName(),
                    summary.status().name(),
                    item.getCurrentPrice(),
                    minimumBid,
                    requiredDeposit,
                    availableBalance,
                    canEnter,
                    hasDeposit,
                    item.getEndTimeString(),
                    summary.secondsRemaining()
            ));
        }

        return entries;
    }

    public AuctionDepositResult confirmAuctionEntry(String itemId, User user) {
        return confirmAuctionEntry(itemId, user, null);
    }

    public boolean hasConfirmedEntryDeposit(String itemId, User user) {
        return settlementService.hasEntryDeposit(itemId, user);
    }

    public boolean hasWalletPin(User user) {
        return walletService.hasPin(user);
    }

    public WalletSummary getWallet(User user, String walletPin) {
        return walletService.getWallet(user, walletPin);
    }

    public WalletAuthorization authorizeWallet(User user, String walletPin, Duration duration) {
        return walletService.authorize(user, walletPin, duration);
    }

    public void setWalletPin(User user, String newPin) {
        walletService.setPin(user, newPin);
    }

    public WalletRecoveryResult requestWalletPinRecovery(User user) {
        return walletService.requestPinRecovery(user);
    }

    public void resetWalletPin(User user, String recoveryCode, String newPin) {
        walletService.resetPinWithRecoveryCode(user, recoveryCode, newPin);
    }

    public WalletSummary addWalletAccount(
            User user,
            String accountName,
            String providerName,
            String accountReference,
            boolean makePrimary,
            String walletPin
    ) {
        return walletService.addLinkedAccount(user, accountName, providerName, accountReference, makePrimary, walletPin);
    }

    public WalletSummary addWalletAccount(
            User user,
            String accountName,
            String providerName,
            String accountReference,
            double initialBalance,
            boolean makePrimary,
            String walletPin
    ) {
        return walletService.addLinkedAccount(
                user,
                accountName,
                providerName,
                accountReference,
                initialBalance,
                makePrimary,
                walletPin
        );
    }

    public WalletSummary setPrimaryWalletAccount(User user, String accountId, String walletPin) {
        return walletService.setPrimaryLinkedAccount(user, accountId, walletPin);
    }

    public WalletSummary removeWalletAccount(User user, String accountId, String walletPin) {
        return walletService.removeLinkedAccount(user, accountId, walletPin);
    }

    public WalletSummary receiveWalletMoney(User user, String accountId, double amount, String walletPin) {
        return walletService.receiveMoney(user, accountId, amount, walletPin);
    }

    public WalletSummary sendWalletMoney(User user, String accountId, double amount, String walletPin) {
        return walletService.sendMoney(user, accountId, amount, walletPin);
    }

    public List<WalletTransaction> getWalletAuditTransactions(User actor, String userId) {
        return walletService.getTransactionsForAdmin(actor, userId);
    }

    public AuctionDepositResult confirmAuctionEntry(String itemId, User user, String walletPin) {
        walletService.requirePin(user, walletPin);
        synchronizeAuctionOutcomes();
        Optional<Item> item = auctionWorkflowService.findItemById(itemId);
        if (item.isEmpty()) {
            return AuctionDepositResult.rejected("Auction item was not found.", 0.0, 0.0, null);
        }
        return settlementService.lockEntryDeposit(
                item.get(),
                auctionWorkflowService.getSummary(itemId),
                user
        );
    }

    public BidValidationResult placeBidWithDeposit(String itemId, User user, double amount) {
        return placeBidWithDeposit(itemId, user, amount, null);
    }

    public BidValidationResult placeBidWithDeposit(String itemId, User user, double amount, String walletPin) {
        walletService.requirePin(user, walletPin);
        synchronizeAuctionOutcomes();
        return auctionWorkflowService.placeBid(itemId, user, amount);
    }

    public boolean registerAutoBidWithDeposit(String itemId, User user, double maxLimit) {
        return registerAutoBidWithDeposit(itemId, user, maxLimit, null);
    }

    public boolean registerAutoBidWithDeposit(String itemId, User user, double maxLimit, String walletPin) {
        return registerAutoBidWithDeposit(itemId, user, maxLimit, 0.0, walletPin);
    }

    public boolean registerAutoBidWithDeposit(String itemId, User user, double maxLimit, double bidIncrement, String walletPin) {
        walletService.requirePin(user, walletPin);
        synchronizeAuctionOutcomes();
        return auctionWorkflowService.registerAutoBid(itemId, user, maxLimit, bidIncrement);
    }

    public List<Bid> getBidHistory(String itemId) {
        synchronizeAuctionOutcomes();
        return auctionWorkflowService.getBidHistory(itemId);
    }

    public List<Item> getSellerItems(User seller) {
        synchronizeAuctionOutcomes();
        if (isAdmin(seller)) {
            return auctionWorkflowService.getAllItems();
        }
        return auctionWorkflowService.getItemsForSeller(seller.getId());
    }

    public Item addSellerItem(
            User seller,
            String type,
            String itemName,
            String description,
            double startingPrice,
            LocalDateTime startTime,
            LocalDateTime endTime,
            String extraText,
            int extraNumber
    ) {
        return auctionWorkflowService.addSellerItem(
                type,
                itemName,
                description,
                startingPrice,
                startTime,
                endTime,
                extraText,
                extraNumber,
                seller.getId()
        );
    }

    public List<Item> getPendingApprovalItems() {
        synchronizeAuctionOutcomes();
        return auctionWorkflowService.getPendingApprovalItems();
    }

    public boolean updateItemApproval(String itemId, ApprovalStatus approvalStatus) {
        synchronizeAuctionOutcomes();
        return auctionWorkflowService.updateApprovalStatus(itemId, approvalStatus);
    }

    public boolean startAuction(User actor, String itemId) {
        synchronizeAuctionOutcomes();
        Optional<Item> item = auctionWorkflowService.findItemById(itemId);
        if (item.isEmpty() || !canManageSellerItem(actor, item.get())) {
            return false;
        }
        return auctionWorkflowService.startAuction(itemId);
    }

    public boolean finishAuction(User actor, String itemId) {
        synchronizeAuctionOutcomes();
        Optional<Item> item = auctionWorkflowService.findItemById(itemId);
        if (item.isEmpty() || !canManageSellerItem(actor, item.get())) {
            return false;
        }
        boolean finished = auctionWorkflowService.finishAuction(itemId);
        synchronizeAuctionOutcome(itemId);
        return finished;
    }

    public List<User> getAllUsers() {
        return authenticationService.getAllUsers();
    }

    public boolean updateUserRole(String userId, String role) {
        return authenticationService.updateUserRole(userId, role);
    }

    public Optional<User> findUserById(String userId) {
        return authenticationService.findById(userId);
    }

    public Optional<AuctionSettlement> getSettlement(String itemId) {
        synchronizeAuctionOutcomes();
        return settlementService.getSettlement(itemId);
    }

    public List<AuctionSettlement> getAllSettlements() {
        synchronizeAuctionOutcomes();
        return settlementService.getAllSettlements();
    }

    public List<UserNotification> getNotifications(User user) {
        synchronizeAuctionOutcomes();
        return settlementService.getNotificationsFor(user);
    }

    public AuctionSettlement admitWinnerResult(String itemId, User user) {
        return admitWinnerResult(itemId, user, null);
    }

    public AuctionSettlement admitWinnerResult(String itemId, User user, String walletPin) {
        walletService.requirePin(user, walletPin);
        synchronizeAuctionOutcomes();
        return settlementService.admitWinnerResult(itemId, user);
    }

    public AuctionSettlement markGoodsShipped(String itemId, User sellerOrAdmin) {
        return markGoodsShipped(itemId, sellerOrAdmin, null);
    }

    public AuctionSettlement markGoodsShipped(String itemId, User sellerOrAdmin, String walletPin) {
        walletService.requirePin(sellerOrAdmin, walletPin);
        synchronizeAuctionOutcomes();
        return settlementService.markGoodsShipped(itemId, sellerOrAdmin);
    }

    public AuctionSettlement confirmGoodsReceived(String itemId, User user) {
        return confirmGoodsReceived(itemId, user, null);
    }

    public AuctionSettlement confirmGoodsReceived(String itemId, User user, String walletPin) {
        walletService.requirePin(user, walletPin);
        synchronizeAuctionOutcomes();
        return settlementService.confirmGoodsReceived(itemId, user);
    }

    public AuctionSettlement reportGoodsNotReceived(String itemId, User user, String reason) {
        return reportGoodsNotReceived(itemId, user, reason, null);
    }

    public AuctionSettlement reportGoodsNotReceived(String itemId, User user, String reason, String walletPin) {
        walletService.requirePin(user, walletPin);
        synchronizeAuctionOutcomes();
        return settlementService.reportGoodsNotReceived(itemId, user, reason);
    }

    public AuctionSettlement adminUnfreezeRemainingPayment(String itemId, User admin) {
        return adminUnfreezeRemainingPayment(itemId, admin, null);
    }

    public AuctionSettlement adminUnfreezeRemainingPayment(String itemId, User admin, String walletPin) {
        walletService.requirePin(admin, walletPin);
        synchronizeAuctionOutcomes();
        return settlementService.adminUnfreezeRemainingPayment(itemId, admin);
    }

    public AuctionSettlement adminKeepRemainingPaymentFrozen(String itemId, User admin) {
        return adminKeepRemainingPaymentFrozen(itemId, admin, null);
    }

    public AuctionSettlement adminKeepRemainingPaymentFrozen(String itemId, User admin, String walletPin) {
        walletService.requirePin(admin, walletPin);
        synchronizeAuctionOutcomes();
        return settlementService.adminKeepRemainingPaymentFrozen(itemId, admin);
    }

    private void synchronizeAuctionOutcomes() {
        for (Item item : auctionWorkflowService.getAllItems()) {
            synchronizeAuctionOutcome(item.getId());
        }
        settlementService.releaseExpiredBuyerConfirmations();
    }

    private void synchronizeAuctionOutcome(String itemId) {
        Optional<Item> item = auctionWorkflowService.findItemById(itemId);
        if (item.isEmpty()) {
            return;
        }
        AuctionSummary summary = auctionWorkflowService.getSummary(itemId);
        if (summary.status() == AuctionStatus.FINISHED) {
            settlementService.finalizeAuction(item.get(), summary, auctionWorkflowService.getBidHistory(itemId));
        }
    }

    private boolean canManageSellerItem(User actor, Item item) {
        if (actor == null || item == null) {
            return false;
        }
        return isAdmin(actor) || item.getSellerId().equals(actor.getId());
    }

    private boolean isAdmin(User user) {
        return user != null && "ADMIN".equalsIgnoreCase(user.getRole());
    }
}
