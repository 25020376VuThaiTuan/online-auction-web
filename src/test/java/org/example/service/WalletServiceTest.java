package org.example.service;

import org.example.model.Bidder;
import org.example.model.WalletAuthorization;
import org.example.model.WalletRecoveryResult;
import org.example.model.WalletSummary;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WalletServiceTest {
    private final WalletService walletService = WalletService.getInstance();
    private final AuthenticationService authenticationService = AuthenticationService.getInstance();

    @Test
    void walletRejectsUsersNotRegisteredThroughAuthentication() {
        Bidder bidder = new Bidder(
                "UNREGISTERED-" + UUID.randomUUID().toString().substring(0, 8),
                "unregistered_wallet_user",
                "secret",
                "unregistered@test.local",
                100.0
        );

        assertThrows(IllegalStateException.class, () -> walletService.setPin(bidder, "1234"));
    }

    @Test
    void walletRequiresPinBeforeOpeningOrTransaction() {
        Bidder bidder = bidder("PIN-REQUIRED", 100.0);

        assertThrows(IllegalStateException.class, () -> walletService.getWallet(bidder, "1234"));

        walletService.setPin(bidder, "1234");
        assertThrows(IllegalArgumentException.class, () -> walletService.getWallet(bidder, "9999"));

        walletService.recordTransaction(bidder, "TOP_UP", 50.0, null, "Test top up.", "1234");
        WalletSummary summary = walletService.getWallet(bidder, "1234");

        assertEquals(150.0, summary.balance());
        assertTrue(summary.pinSet());
        assertEquals("TOP_UP", summary.transactions().getFirst().transactionType());
    }

    @Test
    void pinCanOnlyBeSetOnceWithoutRecovery() {
        Bidder bidder = bidder("PIN-ONCE", 100.0);

        walletService.setPin(bidder, "1234");

        assertThrows(IllegalStateException.class, () -> walletService.setPin(bidder, "5678"));
        assertThrows(IllegalArgumentException.class, () -> walletService.getWallet(bidder, "5678"));
        assertEquals(100.0, walletService.getWallet(bidder, "1234").balance());
    }

    @Test
    void recoveryCodeResetsForgottenPin() {
        AtomicReference<String> deliveredCode = new AtomicReference<>();
        WalletService recoveryWalletService = new WalletService((email, recoveryCode) -> deliveredCode.set(recoveryCode));
        Bidder bidder = bidder("PIN-RECOVERY", 200.0);
        recoveryWalletService.setPin(bidder, "2345");

        WalletRecoveryResult recovery = recoveryWalletService.requestPinRecovery(bidder);
        recoveryWalletService.resetPinWithRecoveryCode(bidder, deliveredCode.get(), "3456");

        assertEquals(bidder.getEmail(), recovery.email());
        assertThrows(IllegalArgumentException.class, () -> recoveryWalletService.getWallet(bidder, "2345"));
        assertEquals(200.0, recoveryWalletService.getWallet(bidder, "3456").balance());
    }

    @Test
    void invalidWalletPinAttemptsAreRateLimited() {
        WalletService isolatedWalletService = new WalletService((email, recoveryCode) -> { });
        Bidder bidder = bidder("PIN-RATE-LIMIT", 100.0);
        isolatedWalletService.setPin(bidder, "1234");

        for (int attempt = 0; attempt < 5; attempt++) {
            assertThrows(IllegalArgumentException.class, () -> isolatedWalletService.getWallet(bidder, "9999"));
        }

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> isolatedWalletService.getWallet(bidder, "9999")
        );
        assertEquals("Too many wallet PIN attempts. Try again later.", exception.getMessage());
    }

    @Test
    void linkedAccountsCanBecomePrimaryAndMoveMoney() {
        Bidder bidder = bidder("LINKED-ACCOUNTS", 100.0);
        walletService.setPin(bidder, "4567");
        String accountHolderName = bidder.getFullName();

        WalletSummary first = walletService.addLinkedAccount(
                bidder,
                accountHolderName,
                "Demo Provider",
                "1234567890",
                false,
                "4567"
        );
        assertEquals(1, first.linkedAccounts().size());
        assertTrue(first.linkedAccounts().getFirst().primary());
        assertEquals(0.0, first.linkedAccounts().getFirst().balance());

        WalletSummary second = walletService.addLinkedAccount(
                bidder,
                accountHolderName,
                "Second Provider",
                "999900001111",
                60.0,
                true,
                "4567"
        );
        String primaryAccountId = second.linkedAccounts().stream()
                .filter(account -> account.providerName().equals("Second Provider"))
                .findFirst()
                .orElseThrow()
                .id();
        assertEquals(primaryAccountId, second.linkedAccounts().stream().filter(account -> account.primary()).findFirst().orElseThrow().id());
        assertEquals(100.0, second.balance());
        assertEquals(60.0, second.linkedAccounts().stream()
                .filter(account -> account.id().equals(primaryAccountId))
                .findFirst()
                .orElseThrow()
                .balance());

        WalletSummary sent = walletService.sendMoney(bidder, primaryAccountId, 20.0, "4567");
        assertEquals(80.0, sent.balance());
        assertEquals(80.0, sent.linkedAccounts().stream()
                .filter(account -> account.id().equals(primaryAccountId))
                .findFirst()
                .orElseThrow()
                .balance());
        assertEquals("WITHDRAWAL", sent.transactions().getFirst().transactionType());

        WalletSummary received = walletService.receiveMoney(bidder, primaryAccountId, 15.0, "4567");
        assertEquals(95.0, received.balance());
        assertEquals(65.0, received.linkedAccounts().stream()
                .filter(account -> account.id().equals(primaryAccountId))
                .findFirst()
                .orElseThrow()
                .balance());
        assertEquals("TOP_UP", received.transactions().getFirst().transactionType());
    }

    @Test
    void linkedAccountOpeningBalanceMustBeNonNegative() {
        Bidder bidder = bidder("OPENING-BALANCE", 100.0);
        walletService.setPin(bidder, "2468");

        assertThrows(IllegalArgumentException.class, () -> walletService.addLinkedAccount(
                bidder,
                bidder.getFullName(),
                "Provider",
                "22223333",
                -1.0,
                true,
                "2468"
        ));
    }

    @Test
    void linkedAccountNameMustMatchAccountHolderFullName() {
        Bidder bidder = bidder("NAME-MATCH", 100.0);
        walletService.setPin(bidder, "8642");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> walletService.addLinkedAccount(
                bidder,
                "Different Holder",
                "Provider",
                "55556666",
                true,
                "8642"
        ));

        assertEquals("Bank account name must match the account holder full name.", exception.getMessage());
    }

    @Test
    void walletAuthorizationTokenReplacesRememberedPin() {
        Bidder bidder = bidder("AUTH-TOKEN", 100.0);
        walletService.setPin(bidder, "5678");

        WalletAuthorization authorization = walletService.authorize(bidder, "5678", Duration.ofMinutes(120));
        walletService.recordTransaction(bidder, "TOP_UP", 10.0, null, "Authorized top up.", authorization.token());

        WalletSummary summary = walletService.getWallet(bidder, authorization.token());
        assertEquals(110.0, summary.balance());
        assertThrows(IllegalArgumentException.class, () -> walletService.getWallet(bidder, "wa_missing"));
    }

    @Test
    void topUpRequiresBankAccountBalance() {
        Bidder bidder = bidder("BANK-BALANCE", 100.0);
        walletService.setPin(bidder, "1357");
        WalletSummary summary = walletService.addLinkedAccount(bidder, bidder.getFullName(), "Provider", "22223333", true, "1357");
        String accountId = summary.linkedAccounts().getFirst().id();

        assertThrows(IllegalArgumentException.class, () -> walletService.receiveMoney(bidder, accountId, 1.0, "1357"));
        assertEquals(100.0, walletService.getWallet(bidder, "1357").balance());
        assertEquals(0.0, walletService.getWallet(bidder, "1357").linkedAccounts().getFirst().balance());
    }

    @Test
    void ledgerEventsKeepWalletLinkedToUpdatedBidderBalance() {
        Bidder bidder = bidder("LEDGER-SYNC", 100.0);
        walletService.setPin(bidder, "2468");
        assertEquals(100.0, walletService.getWallet(bidder, "2468").balance());

        bidder.setBalance(75.0);
        walletService.recordLedgerEvent(bidder, "PAYMENT", 25.0, "ITEM-1", "Captured payment.");

        WalletSummary summary = walletService.getWallet(bidder, "2468");
        assertEquals(75.0, summary.balance());
        assertEquals(75.0, bidder.getBalance());
        assertEquals(75.0, summary.transactions().getFirst().balanceAfter());
    }

    @Test
    void removingPrimaryAccountPromotesAnotherAccount() {
        Bidder bidder = bidder("REMOVE-ACCOUNT", 100.0);
        walletService.setPin(bidder, "6789");
        String accountHolderName = bidder.getFullName();
        WalletSummary first = walletService.addLinkedAccount(bidder, accountHolderName, "Provider One", "11112222", true, "6789");
        WalletSummary second = walletService.addLinkedAccount(bidder, accountHolderName, "Provider Two", "33334444", false, "6789");

        String primaryId = first.linkedAccounts().getFirst().id();
        WalletSummary afterRemoval = walletService.removeLinkedAccount(bidder, primaryId, "6789");

        assertEquals(1, afterRemoval.linkedAccounts().size());
        assertEquals("Provider Two", afterRemoval.linkedAccounts().getFirst().providerName());
        assertTrue(afterRemoval.linkedAccounts().getFirst().primary());
        assertEquals(2, second.linkedAccounts().size());
    }

    private Bidder bidder(String label, double balance) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String username = "wallet_" + label.toLowerCase().replaceAll("[^a-z0-9]+", "_") + "_" + suffix;
        Bidder bidder = (Bidder) authenticationService.registerManualBidder(
                username,
                "secret",
                username + "@test.local",
                "Wallet Test " + label + " " + suffix
        );
        bidder.setBalance(balance);
        authenticationService.updateUser(bidder);
        return bidder;
    }
}
