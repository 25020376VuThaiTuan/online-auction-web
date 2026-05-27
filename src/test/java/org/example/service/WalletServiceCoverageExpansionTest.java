package org.example.service;

import org.example.model.Bidder;
import org.example.model.WalletAuthorization;
import org.example.model.WalletSummary;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WalletServiceCoverageExpansionTest {
    private final AuthenticationService authenticationService = AuthenticationService.getInstance();

    @Test
    void walletRejectsInvalidUsersPinsAndLinkedAccountInputs() {
        WalletService service = new WalletService((email, recoveryCode) -> { });
        Bidder bidder = bidder("VALIDATION", 100.0);

        assertThrows(IllegalArgumentException.class, () -> service.getWallet(null, "1234"));
        assertThrows(IllegalArgumentException.class, () -> service.setPin(bidder, "12a4"));
        assertThrows(IllegalArgumentException.class, () -> service.setPin(bidder, "123"));

        service.setPin(bidder, "1234");

        assertThrows(IllegalArgumentException.class, () -> service.addLinkedAccount(
                bidder,
                " ",
                "Provider",
                "1234",
                true,
                "1234"
        ));
        assertThrows(IllegalArgumentException.class, () -> service.addLinkedAccount(
                bidder,
                bidder.getFullName(),
                "",
                "1234",
                true,
                "1234"
        ));
        assertThrows(IllegalArgumentException.class, () -> service.addLinkedAccount(
                bidder,
                bidder.getFullName(),
                "Provider",
                " ",
                true,
                "1234"
        ));
        assertThrows(IllegalArgumentException.class, () -> service.removeLinkedAccount(bidder, "missing", "1234"));
        assertThrows(IllegalArgumentException.class, () -> service.setPrimaryLinkedAccount(bidder, "missing", "1234"));

        Bidder blankNameBidder = new Bidder(bidder.getId(), bidder.getUsername(), bidder.getPasswordHash(), bidder.getEmail(), bidder.getBalance()) {
            @Override
            public String getFullName() {
                return "";
            }
        };
        blankNameBidder.setRole(bidder.getRole());

        WalletSummary summary = service.addLinkedAccount(blankNameBidder, "Any Holder", "Provider", "11112222", false, "1234");

        assertEquals(1, summary.linkedAccounts().size());
        assertEquals("Any Holder", summary.linkedAccounts().getFirst().accountName());
    }

    @Test
    void recoveryFailuresAreRateLimitedAndCodesAreSingleUse() {
        AtomicReference<String> deliveredCode = new AtomicReference<>();
        WalletService service = new WalletService((email, recoveryCode) -> deliveredCode.set(recoveryCode));
        Bidder limited = bidder("RECOVERY-LIMIT", 100.0);
        service.setPin(limited, "2345");
        service.requestPinRecovery(limited);

        for (int attempt = 0; attempt < 5; attempt++) {
            assertThrows(IllegalArgumentException.class,
                    () -> service.resetPinWithRecoveryCode(limited, "bad-code", "3456"));
        }

        IllegalArgumentException limitedException = assertThrows(
                IllegalArgumentException.class,
                () -> service.resetPinWithRecoveryCode(limited, deliveredCode.get(), "3456")
        );
        assertEquals("Too many wallet recovery code attempts. Try again later.", limitedException.getMessage());

        Bidder reusable = bidder("RECOVERY-SINGLE-USE", 100.0);
        service.setPin(reusable, "4567");
        service.requestPinRecovery(reusable);
        String recoveryCode = deliveredCode.get();

        service.resetPinWithRecoveryCode(reusable, recoveryCode, "5678");

        assertEquals(100.0, service.getWallet(reusable, "5678").balance());
        assertThrows(IllegalArgumentException.class,
                () -> service.resetPinWithRecoveryCode(reusable, recoveryCode, "6789"));
    }

    @Test
    void walletAuthorizationTokensAreUserSpecificAndUseDefaultDurationForInvalidDurations() {
        WalletService service = new WalletService((email, recoveryCode) -> { });
        Bidder first = bidder("AUTH-FIRST", 100.0);
        Bidder second = bidder("AUTH-SECOND", 100.0);
        service.setPin(first, "3456");
        service.setPin(second, "4567");

        WalletAuthorization defaultDuration = service.authorize(first, "3456", Duration.ZERO);
        WalletAuthorization negativeDuration = service.authorize(first, "3456", Duration.ofSeconds(-1));

        assertTrue(defaultDuration.expiresAt().isAfter(LocalDateTime.now().plusMinutes(100)));
        assertTrue(negativeDuration.expiresAt().isAfter(LocalDateTime.now().plusMinutes(100)));
        assertEquals(100.0, service.getWallet(first, defaultDuration.token()).balance());
        assertThrows(IllegalArgumentException.class, () -> service.getWallet(second, defaultDuration.token()));
    }

    @Test
    void depositHoldsCanBeLockedReleasedCapturedAndCleared() throws Exception {
        WalletService service = new WalletService((email, recoveryCode) -> { });
        Bidder bidder = bidder("HOLDS", 200.0);
        service.setPin(bidder, "5678");

        assertThrows(IllegalArgumentException.class,
                () -> service.lockDeposit(bidder, " ", 10.0, "REF", "invalid hold"));

        assertEquals(50.0, service.lockDeposit(bidder, "ITEM-1", 50.0, "ITEM-1", "Entry hold."), 0.001);
        assertEquals(50.0, service.lockedAmount(bidder, "ITEM-1"), 0.001);
        assertEquals(150.0, service.getWalletSnapshot(bidder).availableBalance(), 0.001);

        assertEquals(0.0, service.releaseLockedDeposit(bidder, "MISSING", 0.0, "REF", "Nothing."), 0.001);
        assertEquals(50.0, service.releaseLockedDeposit(null, bidder, "ITEM-1", 0.0, "BID_RELEASE", "ITEM-1", "Released."), 0.001);
        assertEquals(0.0, service.lockedAmount(bidder, "ITEM-1"), 0.001);

        assertEquals(40.0, service.lockDeposit(null, bidder, "ITEM-2", 40.0, "ITEM-2", "Entry hold."), 0.001);
        assertEquals(0.0, service.lockDeposit(bidder, "ITEM-2", 0.0, "ITEM-2", "Clear hold."), 0.001);
        assertEquals(0.0, service.lockedAmount(bidder, "ITEM-2"), 0.001);

        assertEquals(30.0, service.lockDeposit(bidder, "ITEM-3", 30.0, "ITEM-3", "Capture hold."), 0.001);
        assertEquals(30.0, service.captureLockedDeposit(bidder, "ITEM-3", 0.0, "PAYMENT_CAPTURE", "ITEM-3", "Captured."), 0.001);
        assertEquals(170.0, service.getWallet(bidder, "5678").balance(), 0.001);
        assertEquals(0.0, service.captureLockedDeposit(bidder, "ITEM-3", 0.0, "PAYMENT_CAPTURE", "ITEM-3", "Captured."), 0.001);
    }

    @Test
    void transfersUsePrimaryAccountsAndRejectInvalidBalances() {
        WalletService service = new WalletService((email, recoveryCode) -> { });
        Bidder bidder = bidder("TRANSFERS", 100.0);
        service.setPin(bidder, "6789");
        WalletSummary opened = service.addLinkedAccount(bidder, bidder.getFullName(), "Provider", "99990000", 10.0, true, "6789");
        String accountId = opened.linkedAccounts().getFirst().id();

        assertThrows(IllegalArgumentException.class, () -> service.sendMoney(bidder, accountId, 0.0, "6789"));
        assertThrows(IllegalArgumentException.class, () -> service.sendMoney(bidder, accountId, 150.0, "6789"));
        assertThrows(IllegalArgumentException.class, () -> service.receiveMoney(bidder, accountId, 15.0, "6789"));

        WalletSummary afterSend = service.sendMoney(bidder, "", 25.0, "6789");
        assertEquals(75.0, afterSend.balance(), 0.001);
        assertEquals(35.0, afterSend.linkedAccounts().getFirst().balance(), 0.001);

        WalletSummary afterReceive = service.receiveMoney(bidder, null, 5.0, "6789");
        assertEquals(80.0, afterReceive.balance(), 0.001);
        assertEquals(30.0, afterReceive.linkedAccounts().getFirst().balance(), 0.001);

        WalletSummary afterBankTopUp = service.topUpLinkedAccount(bidder, "", 20.0, "6789");
        assertEquals(80.0, afterBankTopUp.balance(), 0.001);
        assertEquals(50.0, afterBankTopUp.linkedAccounts().getFirst().balance(), 0.001);

        Bidder noAccount = bidder("NO-PRIMARY", 10.0);
        service.setPin(noAccount, "7890");
        assertThrows(IllegalArgumentException.class, () -> service.sendMoney(noAccount, "", 1.0, "7890"));
    }

    private Bidder bidder(String label, double balance) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String compactLabel = label.toLowerCase().replaceAll("[^a-z0-9]+", "");
        if (compactLabel.length() > 10) {
            compactLabel = compactLabel.substring(0, 10);
        }
        String username = "wex_" + compactLabel + "_" + suffix;
        Bidder bidder = (Bidder) authenticationService.registerManualBidder(
                username,
                "secret",
                username + "@test.local",
                "Wallet Expansion " + label + " " + suffix
        );
        bidder.setBalance(balance);
        authenticationService.updateUser(bidder);
        return bidder;
    }
}
