package org.example.service;

import org.example.dao.DatabaseConfig;
import org.example.dao.UserDAO;
import org.example.dao.WalletDAO;
import org.example.model.Bidder;
import org.example.model.User;
import org.example.model.WalletAuthorization;
import org.example.model.WalletLinkedAccount;
import org.example.model.WalletRecoveryResult;
import org.example.model.WalletSummary;
import org.example.model.WalletTransaction;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class WalletService {
    private static final WalletService INSTANCE = new WalletService();
    private static final int RECOVERY_CODE_LENGTH = 6;
    private static final String AUTHORIZATION_TOKEN_PREFIX = "wa_";
    private static final double CURRENCY_EPSILON = 0.000001;

    private final AutomatedEmailService emailService = AutomatedEmailService.getInstance();
    private final Map<String, String> pinHashesByUserId = new HashMap<>();
    private final Map<String, String> recoveryCodesByUserId = new HashMap<>();
    private final Map<String, Double> balancesByUserId = new HashMap<>();
    private final Map<String, Map<String, Double>> lockedDepositsByUserId = new HashMap<>();
    private final Map<String, List<WalletTransaction>> transactionsByUserId = new HashMap<>();
    private final Map<String, List<WalletLinkedAccount>> linkedAccountsByUserId = new HashMap<>();
    private final Map<String, WalletAuthorizationState> authorizationsByToken = new HashMap<>();

    private WalletService() {
    }

    public static WalletService getInstance() {
        return INSTANCE;
    }

    public synchronized WalletSummary getWallet(User user, String pin) {
        ensureWallet(user);
        requirePin(user, pin);
        return new WalletSummary(
                user.getId(),
                balanceOf(user),
                lockedBalanceOf(user),
                availableBalanceOf(user),
                hasPin(user),
                linkedAccountsFor(user),
                transactionsFor(user)
        );
    }

    public synchronized WalletSummary getWalletSnapshot(User user) {
        ensureWallet(user);
        return new WalletSummary(
                user.getId(),
                balanceOf(user),
                lockedBalanceOf(user),
                availableBalanceOf(user),
                isPinSet(user),
                linkedAccountsFor(user),
                List.of()
        );
    }

    public synchronized boolean hasPin(User user) {
        ensureWallet(user);
        return isPinSet(user);
    }

    public synchronized void setPin(User user, String newPin) {
        ensureWallet(user);
        if (isPinSet(user)) {
            throw new IllegalStateException("Wallet PIN can only be set once. Use PIN recovery to reset it.");
        }
        savePin(user, newPin, "Wallet PIN was set.");
    }

    private void savePin(User user, String newPin, String note) {
        validatePin(newPin);
        String pinHash = hashPin(user.getId(), newPin);
        if (databaseEnabled()) {
            try (Connection conn = DatabaseConfig.fromEnvironment().openConnection()) {
                boolean originalAutoCommit = conn.getAutoCommit();
                try {
                    conn.setAutoCommit(false);
                    requirePersistedUser(conn, user);
                    WalletDAO walletDAO = new WalletDAO(conn);
                    walletDAO.ensureWallet(user, balanceOf(user));
                    walletDAO.updatePinHash(user.getId(), pinHash);
                    double balance = balanceOf(user);
                    WalletTransaction transaction = walletTransaction(
                            user,
                            "PIN_RESET",
                            0.0,
                            balance,
                            balance,
                            null,
                            note
                    );
                    walletDAO.addTransaction(transaction);
                    conn.commit();
                    pinHashesByUserId.put(user.getId(), pinHash);
                    transactionsByUserId.computeIfAbsent(user.getId(), ignored -> new ArrayList<>()).add(0, transaction);
                } catch (SQLException | RuntimeException e) {
                    conn.rollback();
                    throw e;
                } finally {
                    conn.setAutoCommit(originalAutoCommit);
                }
            } catch (SQLException e) {
                throw databaseFailure("Wallet PIN save failed", e);
            }
            return;
        }

        pinHashesByUserId.put(user.getId(), pinHash);
        recordSystemEvent(user, "PIN_RESET", 0.0, null, note);
    }

    public synchronized WalletRecoveryResult requestPinRecovery(User user) {
        ensureWallet(user);
        String recoveryCode = recoveryCode();
        if (databaseEnabled()) {
            try (WalletDAO walletDAO = WalletDAO.fromEnvironment()) {
                walletDAO.saveRecoveryCode(user.getId(), recoveryCode, LocalDateTime.now().plusMinutes(15));
            } catch (SQLException e) {
                throw databaseFailure("Wallet recovery save failed", e);
            }
            recoveryCodesByUserId.put(user.getId(), recoveryCode);
            emailService.sendWalletPinRecovery(user.getEmail(), recoveryCode);
            return new WalletRecoveryResult(
                    true,
                    "A wallet PIN recovery code was sent to the account email.",
                    user.getEmail(),
                    recoveryCode
            );
        }

        recoveryCodesByUserId.put(user.getId(), recoveryCode);
        emailService.sendWalletPinRecovery(user.getEmail(), recoveryCode);
        return new WalletRecoveryResult(
                true,
                "A wallet PIN recovery code was sent to the account email.",
                user.getEmail(),
                recoveryCode
        );
    }

    public synchronized void resetPinWithRecoveryCode(User user, String recoveryCode, String newPin) {
        ensureWallet(user);
        validatePin(newPin);
        if (databaseEnabled()) {
            String pinHash = hashPin(user.getId(), newPin);
            try (Connection conn = DatabaseConfig.fromEnvironment().openConnection()) {
                boolean originalAutoCommit = conn.getAutoCommit();
                try {
                    conn.setAutoCommit(false);
                    requirePersistedUser(conn, user);
                    WalletDAO walletDAO = new WalletDAO(conn);
                    walletDAO.ensureWallet(user, balanceOf(user));
                    boolean accepted = walletDAO.consumeRecoveryCode(user.getId(), recoveryCode);
                    if (!accepted) {
                        conn.rollback();
                        throw new IllegalArgumentException("Wallet PIN recovery code is invalid or expired.");
                    }
                    walletDAO.updatePinHash(user.getId(), pinHash);
                    double balance = balanceOf(user);
                    WalletTransaction transaction = walletTransaction(
                            user,
                            "PIN_RESET",
                            0.0,
                            balance,
                            balance,
                            null,
                            "Wallet PIN was reset."
                    );
                    walletDAO.addTransaction(transaction);
                    conn.commit();
                    recoveryCodesByUserId.remove(user.getId());
                    pinHashesByUserId.put(user.getId(), pinHash);
                    transactionsByUserId.computeIfAbsent(user.getId(), ignored -> new ArrayList<>()).add(0, transaction);
                } catch (SQLException | RuntimeException e) {
                    if (!conn.getAutoCommit()) {
                        conn.rollback();
                    }
                    throw e;
                } finally {
                    conn.setAutoCommit(originalAutoCommit);
                }
            } catch (SQLException e) {
                throw databaseFailure("Wallet PIN reset failed", e);
            }
            return;
        }

        boolean accepted = recoveryCode != null && recoveryCode.equals(recoveryCodesByUserId.get(user.getId()));
        if (!accepted) {
            throw new IllegalArgumentException("Wallet PIN recovery code is invalid or expired.");
        }
        recoveryCodesByUserId.remove(user.getId());
        savePin(user, newPin, "Wallet PIN was reset.");
    }

    public synchronized WalletAuthorization authorize(User user, String pin, Duration duration) {
        requirePin(user, pin);
        Duration safeDuration = duration == null || duration.isNegative() || duration.isZero()
                ? Duration.ofMinutes(120)
                : duration;
        WalletAuthorization authorization = new WalletAuthorization(
                AUTHORIZATION_TOKEN_PREFIX + UUID.randomUUID(),
                LocalDateTime.now().plus(safeDuration)
        );
        authorizationsByToken.put(authorization.token(), new WalletAuthorizationState(user.getId(), authorization.expiresAt()));
        return authorization;
    }

    public synchronized void requirePin(User user, String pin) {
        ensureWallet(user);
        if (isAuthorized(user, pin)) {
            return;
        }
        Optional<String> pinHash = findPinHash(user);
        if (pinHash.isEmpty() || pinHash.get().isBlank()) {
            throw new IllegalStateException("Set a wallet PIN before opening the wallet or making transactions.");
        }
        if (pin == null || pin.isBlank() || !pinHash.get().equals(hashPin(user.getId(), pin))) {
            throw new IllegalArgumentException("Wallet PIN is incorrect.");
        }
    }

    public synchronized void recordTransaction(
            User user,
            String transactionType,
            double amountDelta,
            String referenceId,
            String note,
            String pin
    ) {
        requirePin(user, pin);
        applyTransaction(user, transactionType, amountDelta, referenceId, note);
    }

    public synchronized WalletSummary addLinkedAccount(
            User user,
            String accountName,
            String providerName,
            String accountReference,
            boolean makePrimary,
            String pin
    ) {
        return addLinkedAccount(user, accountName, providerName, accountReference, 0.0, makePrimary, pin);
    }

    public synchronized WalletSummary addLinkedAccount(
            User user,
            String accountName,
            String providerName,
            String accountReference,
            double initialBalance,
            boolean makePrimary,
            String pin
    ) {
        requirePin(user, pin);
        if (isBlank(accountName) || isBlank(providerName) || isBlank(accountReference)) {
            throw new IllegalArgumentException("Account name, provider, and reference are required.");
        }
        double accountBalance = validateAccountBalance(initialBalance);

        List<WalletLinkedAccount> existingAccounts = new ArrayList<>(linkedAccountsFor(user));
        boolean primary = makePrimary || existingAccounts.isEmpty();
        WalletLinkedAccount account = new WalletLinkedAccount(
                UUID.randomUUID().toString(),
                user.getId(),
                accountName.trim(),
                providerName.trim(),
                accountReference.trim(),
                accountBalance,
                primary,
                LocalDateTime.now()
        );
        if (primary) {
            existingAccounts = existingAccounts.stream()
                    .map(existing -> withPrimary(existing, false))
                    .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        }

        if (databaseEnabled()) {
            try (WalletDAO walletDAO = WalletDAO.fromEnvironment()) {
                walletDAO.addLinkedAccount(account);
            } catch (SQLException e) {
                throw databaseFailure("Wallet linked account save failed", e);
            }
            return getWallet(user, pin);
        }

        existingAccounts.add(0, account);
        linkedAccountsByUserId.put(user.getId(), existingAccounts);
        return getWallet(user, pin);
    }

    public synchronized WalletSummary removeLinkedAccount(User user, String accountId, String pin) {
        requirePin(user, pin);
        List<WalletLinkedAccount> accounts = new ArrayList<>(linkedAccountsFor(user));
        WalletLinkedAccount removed = accounts.stream()
                .filter(account -> account.id().equals(accountId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Wallet account was not found."));
        accounts.removeIf(account -> account.id().equals(accountId));
        if (removed.primary() && !accounts.isEmpty()) {
            WalletLinkedAccount nextPrimary = accounts.get(0);
            accounts = accounts.stream()
                    .map(account -> withPrimary(account, account.id().equals(nextPrimary.id())))
                    .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        }

        if (databaseEnabled()) {
            try (Connection conn = DatabaseConfig.fromEnvironment().openConnection()) {
                boolean originalAutoCommit = conn.getAutoCommit();
                try {
                    conn.setAutoCommit(false);
                    WalletDAO walletDAO = new WalletDAO(conn);
                    boolean deleted = walletDAO.deleteLinkedAccount(user.getId(), accountId);
                    if (!deleted) {
                        throw new IllegalArgumentException("Wallet account was not found.");
                    }
                    Optional<WalletLinkedAccount> primaryAccount = accounts.stream()
                            .filter(WalletLinkedAccount::primary)
                            .findFirst();
                    if (primaryAccount.isPresent()) {
                        walletDAO.setPrimaryLinkedAccount(user.getId(), primaryAccount.get().id());
                    }
                    conn.commit();
                } catch (SQLException | RuntimeException e) {
                    conn.rollback();
                    throw e;
                } finally {
                    conn.setAutoCommit(originalAutoCommit);
                }
            } catch (SQLException e) {
                throw databaseFailure("Wallet account removal failed", e);
            }
            return getWallet(user, pin);
        }

        linkedAccountsByUserId.put(user.getId(), accounts);
        return getWallet(user, pin);
    }

    public synchronized WalletSummary setPrimaryLinkedAccount(User user, String accountId, String pin) {
        requirePin(user, pin);
        List<WalletLinkedAccount> accounts = linkedAccountsFor(user);
        if (accounts.stream().noneMatch(account -> account.id().equals(accountId))) {
            throw new IllegalArgumentException("Wallet account was not found.");
        }
        List<WalletLinkedAccount> updated = accounts.stream()
                .map(account -> withPrimary(account, account.id().equals(accountId)))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));

        if (databaseEnabled()) {
            try (WalletDAO walletDAO = WalletDAO.fromEnvironment()) {
                walletDAO.setPrimaryLinkedAccount(user.getId(), accountId);
            } catch (SQLException e) {
                throw databaseFailure("Wallet primary account save failed", e);
            }
            return getWallet(user, pin);
        }

        linkedAccountsByUserId.put(user.getId(), updated);
        return getWallet(user, pin);
    }

    public synchronized WalletSummary receiveMoney(User user, String accountId, double amount, String pin) {
        requirePin(user, pin);
        WalletLinkedAccount account = requireLinkedAccount(user, accountId);
        validateTransferAmount(amount);
        applyLinkedAccountTransfer(
                user,
                account,
                "TOP_UP",
                amount,
                -amount,
                "Received from " + account.displayName() + "."
        );
        return getWallet(user, pin);
    }

    public synchronized WalletSummary sendMoney(User user, String accountId, double amount, String pin) {
        requirePin(user, pin);
        WalletLinkedAccount account = requireLinkedAccount(user, accountId);
        validateTransferAmount(amount);
        if (amount > availableBalanceOf(user)) {
            throw new IllegalArgumentException("Wallet available balance is not enough for this transfer.");
        }
        applyLinkedAccountTransfer(
                user,
                account,
                "WITHDRAWAL",
                -amount,
                amount,
                "Sent to " + account.displayName() + "."
        );
        return getWallet(user, pin);
    }

    synchronized void recordSystemEvent(
            User user,
            String transactionType,
            double amountDelta,
            String referenceId,
            String note
    ) {
        ensureWallet(user);
        applyTransaction(user, transactionType, amountDelta, referenceId, note);
    }

    synchronized double lockDeposit(
            Bidder bidder,
            String holdKey,
            double totalHoldAmount,
            String referenceId,
            String note
    ) {
        ensureWallet(bidder, true);
        if (holdKey == null || holdKey.isBlank()) {
            throw new IllegalArgumentException("A valid auction reference is required for a wallet hold.");
        }

        double safeTotalHoldAmount = roundCurrency(totalHoldAmount);
        double existingHold = roundCurrency(bidder.getLockedAmount(holdKey));
        double additionalHold = roundCurrency(Math.max(0.0, safeTotalHoldAmount - existingHold));
        if (additionalHold > availableBalanceOf(bidder)) {
            throw new IllegalArgumentException("Wallet available balance is not enough for this hold.");
        }

        bidder.lockDeposit(holdKey, safeTotalHoldAmount);
        lockedDepositsFor(bidder).put(holdKey, safeTotalHoldAmount);
        persistHold(bidder, holdKey, safeTotalHoldAmount, referenceId, note);
        AuthenticationService.getInstance().updateUser(bidder);
        if (additionalHold > 0.0) {
            recordLedgerEvent(bidder, "BID_HOLD", additionalHold, referenceId, note);
        }
        return safeTotalHoldAmount;
    }

    synchronized double releaseLockedDeposit(
            Bidder bidder,
            String holdKey,
            double fallbackAmount,
            String referenceId,
            String note
    ) {
        return releaseLockedDeposit(bidder, holdKey, fallbackAmount, "BID_RELEASE", referenceId, note);
    }

    synchronized double releaseLockedDeposit(
            Bidder bidder,
            String holdKey,
            double fallbackAmount,
            String transactionType,
            String referenceId,
            String note
    ) {
        ensureWallet(bidder, true);
        double amount = heldAmount(bidder, holdKey, fallbackAmount);
        if (amount <= 0.0) {
            return 0.0;
        }

        bidder.releaseDeposit(holdKey);
        lockedDepositsFor(bidder).remove(holdKey);
        deleteHold(bidder, holdKey);
        AuthenticationService.getInstance().updateUser(bidder);
        recordLedgerEvent(bidder, transactionType, amount, referenceId, note);
        return amount;
    }

    synchronized double captureLockedDeposit(
            Bidder bidder,
            String holdKey,
            double fallbackAmount,
            String transactionType,
            String referenceId,
            String note
    ) {
        ensureWallet(bidder, true);
        double amount = heldAmount(bidder, holdKey, fallbackAmount);
        if (amount <= 0.0) {
            return 0.0;
        }

        bidder.releaseDeposit(holdKey);
        lockedDepositsFor(bidder).remove(holdKey);
        deleteHold(bidder, holdKey);
        applyTransaction(bidder, transactionType, -amount, referenceId, note);
        AuthenticationService.getInstance().updateUser(bidder);
        return amount;
    }

    synchronized void recordLedgerEvent(
            User user,
            String transactionType,
            double amount,
            String referenceId,
            String note
    ) {
        ensureWallet(user, true);
        double balance = balanceOf(user);
        WalletTransaction transaction = new WalletTransaction(
                UUID.randomUUID().toString(),
                user.getId(),
                transactionType,
                Math.abs(amount),
                balance,
                balance,
                referenceId,
                note == null ? "" : note,
                LocalDateTime.now()
        );

        if (databaseEnabled()) {
            try (Connection conn = DatabaseConfig.fromEnvironment().openConnection()) {
                boolean originalAutoCommit = conn.getAutoCommit();
                try {
                    conn.setAutoCommit(false);
                    WalletDAO walletDAO = new WalletDAO(conn);
                    walletDAO.updateBalance(user.getId(), balance);
                    walletDAO.addTransaction(transaction);
                    conn.commit();
                } catch (SQLException | RuntimeException e) {
                    conn.rollback();
                    throw e;
                } finally {
                    conn.setAutoCommit(originalAutoCommit);
                }
            } catch (SQLException e) {
                throw databaseFailure("Wallet ledger save failed", e);
            }
        }
        transactionsByUserId.computeIfAbsent(user.getId(), ignored -> new ArrayList<>()).add(0, transaction);
    }

    private void applyTransaction(User user, String transactionType, double amountDelta, String referenceId, String note) {
        double before = balanceOf(user);
        WalletTransaction transaction;

        if (databaseEnabled()) {
            try (Connection conn = DatabaseConfig.fromEnvironment().openConnection()) {
                boolean originalAutoCommit = conn.getAutoCommit();
                try {
                    conn.setAutoCommit(false);
                    WalletDAO walletDAO = new WalletDAO(conn);
                    walletDAO.ensureWallet(user, before);
                    before = walletDAO.findBalanceForUpdate(user.getId()).orElse(before);
                    double after = balanceAfter(before, amountDelta);
                    transaction = walletTransaction(user, transactionType, amountDelta, before, after, referenceId, note);
                    walletDAO.updateBalance(user.getId(), after);
                    walletDAO.addTransaction(transaction);
                    conn.commit();
                } catch (SQLException | RuntimeException e) {
                    conn.rollback();
                    throw e;
                } finally {
                    conn.setAutoCommit(originalAutoCommit);
                }
            } catch (SQLException e) {
                throw databaseFailure("Wallet transaction save failed", e);
            }
        } else {
            double after = balanceAfter(before, amountDelta);
            transaction = walletTransaction(user, transactionType, amountDelta, before, after, referenceId, note);
        }

        balancesByUserId.put(user.getId(), transaction.balanceAfter());
        if (user instanceof Bidder bidder) {
            bidder.setBalance(transaction.balanceAfter());
        }
        transactionsByUserId.computeIfAbsent(user.getId(), ignored -> new ArrayList<>()).add(0, transaction);
    }

    private void applyLinkedAccountTransfer(
            User user,
            WalletLinkedAccount account,
            String transactionType,
            double walletAmountDelta,
            double accountAmountDelta,
            String note
    ) {
        double before = balanceOf(user);
        double updatedAccountBalance;
        WalletTransaction transaction;

        if (databaseEnabled()) {
            try (Connection conn = DatabaseConfig.fromEnvironment().openConnection()) {
                boolean originalAutoCommit = conn.getAutoCommit();
                try {
                    conn.setAutoCommit(false);
                    WalletDAO walletDAO = new WalletDAO(conn);
                    walletDAO.ensureWallet(user, before);
                    before = walletDAO.findBalanceForUpdate(user.getId()).orElse(before);
                    double accountBefore = walletDAO.findLinkedAccountBalanceForUpdate(user.getId(), account.id())
                            .orElseThrow(() -> new IllegalArgumentException("Wallet account was not found."));
                    updatedAccountBalance = accountBalanceAfter(accountBefore, accountAmountDelta);
                    double after = balanceAfter(before, walletAmountDelta);
                    transaction = walletTransaction(user, transactionType, walletAmountDelta, before, after, account.id(), note);
                    walletDAO.updateBalance(user.getId(), after);
                    walletDAO.updateLinkedAccountBalance(user.getId(), account.id(), updatedAccountBalance);
                    walletDAO.addTransaction(transaction);
                    conn.commit();
                } catch (SQLException | RuntimeException e) {
                    conn.rollback();
                    throw e;
                } finally {
                    conn.setAutoCommit(originalAutoCommit);
                }
            } catch (SQLException e) {
                throw databaseFailure("Wallet account transfer save failed", e);
            }
        } else {
            updatedAccountBalance = accountBalanceAfter(account.balance(), accountAmountDelta);
            double after = balanceAfter(before, walletAmountDelta);
            transaction = walletTransaction(user, transactionType, walletAmountDelta, before, after, account.id(), note);
        }

        balancesByUserId.put(user.getId(), transaction.balanceAfter());
        if (user instanceof Bidder bidder) {
            bidder.setBalance(transaction.balanceAfter());
        }
        updateLinkedAccountBalanceInMemory(user, account.id(), updatedAccountBalance);
        transactionsByUserId.computeIfAbsent(user.getId(), ignored -> new ArrayList<>()).add(0, transaction);
    }

    private void ensureWallet(User user) {
        ensureWallet(user, false);
    }

    private void ensureWallet(User user, boolean preferUserBalance) {
        if (user == null || user.getId() == null || user.getId().isBlank()) {
            throw new IllegalArgumentException("A valid user is required for wallet operations.");
        }
        Double cachedBalance = balancesByUserId.get(user.getId());
        double userBalance = balanceOf(user);
        double initialBalance = preferUserBalance || cachedBalance == null ? userBalance : cachedBalance;
        if (databaseEnabled()) {
            try (Connection conn = DatabaseConfig.fromEnvironment().openConnection()) {
                WalletDAO walletDAO = new WalletDAO(conn);
                walletDAO.ensureSchema();
                boolean originalAutoCommit = conn.getAutoCommit();
                try {
                    conn.setAutoCommit(false);
                    requirePersistedUser(conn, user);
                    walletDAO.ensureWallet(user, initialBalance);
                    double synchronizedBalance = walletDAO.findBalance(user.getId()).orElse(initialBalance);
                    if (preferUserBalance && differs(synchronizedBalance, userBalance)) {
                        walletDAO.updateBalance(user.getId(), userBalance);
                        synchronizedBalance = userBalance;
                    }
                    conn.commit();
                    balancesByUserId.put(user.getId(), synchronizedBalance);
                    if (user instanceof Bidder bidder) {
                        bidder.setBalance(synchronizedBalance);
                    }
                    walletDAO.findPinHash(user.getId()).ifPresentOrElse(
                            hash -> pinHashesByUserId.put(user.getId(), hash),
                            () -> pinHashesByUserId.remove(user.getId())
                    );
                    transactionsByUserId.put(user.getId(), walletDAO.listTransactions(user.getId()));
                    linkedAccountsByUserId.put(user.getId(), walletDAO.listLinkedAccounts(user.getId()));
                    Map<String, Double> holds = walletDAO.listHolds(user.getId());
                    lockedDepositsByUserId.put(user.getId(), new HashMap<>(holds));
                    if (user instanceof Bidder bidder) {
                        bidder.replaceLockedDeposits(holds);
                    }
                } catch (SQLException | RuntimeException e) {
                    conn.rollback();
                    throw e;
                } finally {
                    conn.setAutoCommit(originalAutoCommit);
                }
            } catch (SQLException e) {
                throw databaseFailure("Wallet database sync failed", e);
            }
            return;
        }

        requireKnownUser(user);
        if (user instanceof Bidder bidder) {
            balancesByUserId.put(user.getId(), bidder.getBalance());
            Map<String, Double> holds = lockedDepositsFor(user);
            if (holds.isEmpty()) {
                holds.putAll(bidder.getLockedDepositsByAuctionId());
            } else {
                bidder.replaceLockedDeposits(holds);
            }
        } else {
            balancesByUserId.putIfAbsent(user.getId(), initialBalance);
        }
        transactionsByUserId.computeIfAbsent(user.getId(), ignored -> new ArrayList<>());
        linkedAccountsByUserId.computeIfAbsent(user.getId(), ignored -> new ArrayList<>());
    }

    private void requirePersistedUser(Connection conn, User user) throws SQLException {
        UserDAO userDAO = new UserDAO(conn);
        if (userDAO.getUserById(user.getId()) != null) {
            return;
        }
        throw new SQLException("Wallet operations require an authenticated persisted user: " + user.getId());
    }

    private void requireKnownUser(User user) {
        User knownUser = AuthenticationService.getInstance().findById(user.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "Wallet operations require an authenticated registered user: " + user.getId()
                ));
        if (!safeValue(knownUser.getUsername()).equals(safeValue(user.getUsername()))) {
            throw new IllegalStateException("Wallet operations require the logged-in account credentials.");
        }
    }

    private boolean differs(double left, double right) {
        return Math.abs(left - right) > CURRENCY_EPSILON;
    }

    private Optional<String> findPinHash(User user) {
        String hash = pinHashesByUserId.get(user.getId());
        if (hash != null) {
            return Optional.of(hash);
        }
        return Optional.empty();
    }

    private boolean isPinSet(User user) {
        return findPinHash(user).filter(hash -> !hash.isBlank()).isPresent();
    }

    private List<WalletTransaction> transactionsFor(User user) {
        return List.copyOf(transactionsByUserId.getOrDefault(user.getId(), List.of()));
    }

    private List<WalletLinkedAccount> linkedAccountsFor(User user) {
        return List.copyOf(linkedAccountsByUserId.getOrDefault(user.getId(), List.of()));
    }

    public synchronized List<WalletTransaction> getTransactionsForAdmin(User actor, String userId) {
        if (actor == null || !"ADMIN".equalsIgnoreCase(actor.getRole())) {
            throw new IllegalStateException("Admin role required.");
        }
        if (databaseEnabled()) {
            try (WalletDAO walletDAO = WalletDAO.fromEnvironment()) {
                walletDAO.ensureSchema();
                if (isBlank(userId)) {
                    return walletDAO.listAllTransactions();
                }
                return walletDAO.listTransactions(userId);
            } catch (SQLException e) {
                throw databaseFailure("Wallet audit lookup failed", e);
            }
        }
        if (!isBlank(userId)) {
            return List.copyOf(transactionsByUserId.getOrDefault(userId, List.of()));
        }
        return transactionsByUserId.values().stream()
                .flatMap(List::stream)
                .sorted((left, right) -> right.createdAt().compareTo(left.createdAt()))
                .toList();
    }

    private WalletLinkedAccount requireLinkedAccount(User user, String accountId) {
        if (isBlank(accountId)) {
            return linkedAccountsFor(user).stream()
                    .filter(WalletLinkedAccount::primary)
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Select a wallet account first."));
        }
        return linkedAccountsFor(user).stream()
                .filter(account -> account.id().equals(accountId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Wallet account was not found."));
    }

    private WalletLinkedAccount withPrimary(WalletLinkedAccount account, boolean primary) {
        return new WalletLinkedAccount(
                account.id(),
                account.userId(),
                account.accountName(),
                account.providerName(),
                account.accountReference(),
                account.balance(),
                primary,
                account.createdAt()
        );
    }

    private void updateLinkedAccountBalanceInMemory(User user, String accountId, double balance) {
        List<WalletLinkedAccount> updated = linkedAccountsFor(user).stream()
                .map(account -> account.id().equals(accountId) ? withBalance(account, balance) : account)
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        linkedAccountsByUserId.put(user.getId(), updated);
    }

    private WalletLinkedAccount withBalance(WalletLinkedAccount account, double balance) {
        return new WalletLinkedAccount(
                account.id(),
                account.userId(),
                account.accountName(),
                account.providerName(),
                account.accountReference(),
                balance,
                account.primary(),
                account.createdAt()
        );
    }

    private double balanceOf(User user) {
        if (user instanceof Bidder bidder) {
            return bidder.getBalance();
        }
        return balancesByUserId.getOrDefault(user.getId(), 0.0);
    }

    private double lockedBalanceOf(User user) {
        Map<String, Double> holds = lockedDepositsByUserId.get(user.getId());
        if (holds != null && !holds.isEmpty()) {
            return holds.values().stream().mapToDouble(Double::doubleValue).sum();
        }
        return user instanceof Bidder bidder ? bidder.getLockedBalance() : 0.0;
    }

    private double availableBalanceOf(User user) {
        return user instanceof Bidder ? Math.max(0.0, balanceOf(user) - lockedBalanceOf(user)) : balanceOf(user);
    }

    private boolean isAuthorized(User user, String credential) {
        if (credential == null || !credential.startsWith(AUTHORIZATION_TOKEN_PREFIX)) {
            return false;
        }
        WalletAuthorizationState state = authorizationsByToken.get(credential);
        if (state == null || !state.userId().equals(user.getId()) || state.expiresAt().isBefore(LocalDateTime.now())) {
            authorizationsByToken.remove(credential);
            return false;
        }
        return true;
    }

    private double balanceAfter(double before, double amountDelta) {
        double after = before + amountDelta;
        if (after < -0.000001) {
            throw new IllegalArgumentException("Wallet available balance is not enough for this transaction.");
        }
        return Math.max(0.0, after);
    }

    private double accountBalanceAfter(double before, double amountDelta) {
        double after = before + amountDelta;
        if (after < -0.000001) {
            throw new IllegalArgumentException("Bank account balance is not enough for this transfer.");
        }
        return Math.max(0.0, after);
    }

    private double heldAmount(Bidder bidder, String holdKey, double fallbackAmount) {
        if (holdKey == null || holdKey.isBlank()) {
            return roundCurrency(fallbackAmount);
        }
        double lockedAmount = lockedDepositsFor(bidder).getOrDefault(holdKey, bidder.getLockedAmount(holdKey));
        return roundCurrency(lockedAmount > 0.0 ? lockedAmount : fallbackAmount);
    }

    private Map<String, Double> lockedDepositsFor(User user) {
        return lockedDepositsByUserId.computeIfAbsent(user.getId(), ignored -> new HashMap<>());
    }

    private void persistHold(User user, String holdKey, double amount, String referenceId, String note) {
        if (!databaseEnabled()) {
            return;
        }
        try (WalletDAO walletDAO = WalletDAO.fromEnvironment()) {
            walletDAO.ensureSchema();
            walletDAO.upsertHold(user.getId(), holdKey, referenceId, amount, note);
        } catch (SQLException e) {
            throw databaseFailure("Wallet hold save failed", e);
        }
    }

    private void deleteHold(User user, String holdKey) {
        if (!databaseEnabled()) {
            return;
        }
        try (WalletDAO walletDAO = WalletDAO.fromEnvironment()) {
            walletDAO.ensureSchema();
            walletDAO.deleteHold(user.getId(), holdKey);
        } catch (SQLException e) {
            throw databaseFailure("Wallet hold removal failed", e);
        }
    }

    private double roundCurrency(double amount) {
        return Math.round(Math.max(0.0, amount) * 100.0) / 100.0;
    }

    private WalletTransaction walletTransaction(
            User user,
            String transactionType,
            double amountDelta,
            double before,
            double after,
            String referenceId,
            String note
    ) {
        return new WalletTransaction(
                UUID.randomUUID().toString(),
                user.getId(),
                transactionType,
                Math.abs(amountDelta),
                before,
                after,
                referenceId,
                note == null ? "" : note,
                LocalDateTime.now()
        );
    }

    private void validatePin(String pin) {
        if (pin == null || !pin.matches("\\d{4,8}")) {
            throw new IllegalArgumentException("Wallet PIN must be 4 to 8 digits.");
        }
    }

    private void validateTransferAmount(double amount) {
        if (!Double.isFinite(amount) || amount <= 0.0) {
            throw new IllegalArgumentException("Transfer amount must be greater than zero.");
        }
    }

    private double validateAccountBalance(double balance) {
        if (!Double.isFinite(balance) || balance < 0.0) {
            throw new IllegalArgumentException("Bank account balance must be zero or greater.");
        }
        return balance;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String safeValue(String value) {
        return value == null ? "" : value.trim();
    }

    private String hashPin(String userId, String pin) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((userId + ":" + pin).getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte value : hash) {
                builder.append(String.format("%02x", value));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable.", e);
        }
    }

    private String recoveryCode() {
        String raw = String.valueOf(Math.abs(UUID.randomUUID().hashCode()));
        if (raw.length() >= RECOVERY_CODE_LENGTH) {
            return raw.substring(0, RECOVERY_CODE_LENGTH);
        }
        return String.format("%1$" + RECOVERY_CODE_LENGTH + "s", raw).replace(' ', '0');
    }

    private boolean databaseEnabled() {
        String problem = DatabaseConfig.environmentProblem();
        if (problem != null) {
            throw new IllegalStateException(problem);
        }
        return DatabaseConfig.hasEnvironmentConfig();
    }

    private IllegalStateException databaseFailure(String operation, SQLException e) {
        return new IllegalStateException(operation + ": " + e.getMessage(), e);
    }

    private record WalletAuthorizationState(String userId, LocalDateTime expiresAt) {
    }
}
