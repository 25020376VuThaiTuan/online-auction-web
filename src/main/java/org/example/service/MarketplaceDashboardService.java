package org.example.service;

import org.example.auction.AuctionRules;
import org.example.auction.BidValidationResult;
import org.example.model.ApprovalStatus;
import org.example.model.BankAccount;
import org.example.model.Bid;
import org.example.model.Bidder;
import org.example.model.Item;
import org.example.model.User;
import org.example.viewmodel.AuctionEligibilityEntry;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class MarketplaceDashboardService {
    private static final MarketplaceDashboardService INSTANCE = new MarketplaceDashboardService();

    private final AuthenticationService authenticationService = AuthenticationService.getInstance();
    private final AuctionWorkflowService auctionWorkflowService = AuctionWorkflowService.getInstance();

    private MarketplaceDashboardService() {
    }

    public static MarketplaceDashboardService getInstance() {
        return INSTANCE;
    }

    public User registerManualBidder(String username, String password, String email, String fullName) {
        return authenticationService.registerManualBidder(username, password, email, fullName);
    }

    public User loginWithGoogleToken(String token) {
        return authenticationService.loginWithGoogleToken(token);
    }

    public void updateProfile(User user, String fullName, String phoneNumber, String address) {
        user.setFullName(fullName);
        user.setPhoneNumber(phoneNumber);
        user.setAddress(address);
    }

    public void updateAvatar(User user, String avatarUrl) {
        user.setAvatarUrl(avatarUrl);
    }

    public void addBankAccount(User user, String bankName, String accountHolder, String accountNumber) {
        user.addBankAccount(new BankAccount(bankName, accountHolder, accountNumber));
    }

    public List<AuctionEligibilityEntry> getAuctionEligibilityEntries(User user) {
        List<AuctionEligibilityEntry> entries = new ArrayList<>();
        double availableBalance = user instanceof Bidder bidder ? bidder.getAvailableBalance() : 0.0;

        for (Item item : auctionWorkflowService.getAllItems()) {
            if (!item.isApproved()) {
                continue;
            }
            double requiredDeposit = AuctionRules.requiredDeposit(item.getCurrentPrice());
            double minimumBid = AuctionRules.minimumNextBid(item.getCurrentPrice());
            entries.add(new AuctionEligibilityEntry(
                    item.getId(),
                    item.getItemName(),
                    auctionWorkflowService.getSummary(item.getId()).status().name(),
                    item.getCurrentPrice(),
                    minimumBid,
                    requiredDeposit,
                    availableBalance,
                    availableBalance >= requiredDeposit
            ));
        }

        return entries;
    }

    public BidValidationResult placeBidWithDeposit(String itemId, User user, double amount) {
        if (!(user instanceof Bidder bidder)) {
            return BidValidationResult.rejected(
                    "Only bidder accounts can place bids.",
                    amount,
                    0.0,
                    0.0,
                    auctionWorkflowService.getSummary(itemId).status(),
                    auctionWorkflowService.findItemById(itemId).map(Item::getEndTime).orElse(null)
            );
        }

        double requiredDeposit = AuctionRules.requiredDeposit(auctionWorkflowService.getSummary(itemId).currentPrice());
        if (!bidder.canCoverDeposit(requiredDeposit)) {
            return BidValidationResult.rejected(
                    "Available balance is lower than the temporary deposit requirement.",
                    amount,
                    auctionWorkflowService.getSummary(itemId).currentPrice(),
                    auctionWorkflowService.getSummary(itemId).minimumNextBid(),
                    auctionWorkflowService.getSummary(itemId).status(),
                    auctionWorkflowService.findItemById(itemId).map(Item::getEndTime).orElse(null)
            );
        }

        String previousLeader = auctionWorkflowService.getSummary(itemId).highestBidderId();
        BidValidationResult result = auctionWorkflowService.placeBid(itemId, user, amount);
        if (!result.accepted()) {
            return result;
        }

        if (previousLeader != null && !previousLeader.isBlank() && !previousLeader.equalsIgnoreCase(user.getUsername())) {
            authenticationService.findByUsername(previousLeader)
                    .filter(Bidder.class::isInstance)
                    .map(Bidder.class::cast)
                    .ifPresent(previousBidder -> previousBidder.releaseDeposit(itemId));
        }

        bidder.lockDeposit(itemId, requiredDeposit);
        return result;
    }

    public List<Bid> getBidHistory(String itemId) {
        return auctionWorkflowService.getBidHistory(itemId);
    }

    public List<Item> getSellerItems(User seller) {
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
        return auctionWorkflowService.getPendingApprovalItems();
    }

    public boolean updateItemApproval(String itemId, ApprovalStatus approvalStatus) {
        return auctionWorkflowService.updateApprovalStatus(itemId, approvalStatus);
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
}
