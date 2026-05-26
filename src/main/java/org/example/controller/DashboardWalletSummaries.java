package org.example.controller;

import org.example.model.Bidder;
import org.example.model.User;
import org.example.model.WalletSummary;

final class DashboardWalletSummaries {
    private DashboardWalletSummaries() {
    }

    static WalletSummary withCurrentFinancials(WalletSummary wallet, User user) {
        if (wallet == null || user == null || !wallet.userId().equals(user.getId())) {
            return wallet;
        }
        if (!(user instanceof Bidder bidder)) {
            return wallet;
        }
        return new WalletSummary(
                wallet.userId(),
                bidder.getBalance(),
                bidder.getLockedBalance(),
                bidder.getAvailableBalance(),
                wallet.pinSet(),
                wallet.linkedAccounts(),
                wallet.transactions()
        );
    }

    static WalletSummary dashboardWallet(User user, WalletSummary walletSnapshot, WalletSummary openedWalletSummary) {
        WalletSummary snapshot = withCurrentFinancials(walletSnapshot, user);
        if (openedWalletSummary == null || user == null || !openedWalletSummary.userId().equals(user.getId())) {
            return snapshot;
        }
        return mergeFinancials(openedWalletSummary, snapshot);
    }

    static WalletSummary mergeFinancials(WalletSummary detailWallet, WalletSummary financialWallet) {
        if (detailWallet == null) {
            return financialWallet;
        }
        if (financialWallet == null || !detailWallet.userId().equals(financialWallet.userId())) {
            return detailWallet;
        }
        return new WalletSummary(
                detailWallet.userId(),
                financialWallet.balance(),
                financialWallet.lockedBalance(),
                financialWallet.availableBalance(),
                financialWallet.pinSet(),
                detailWallet.linkedAccounts(),
                detailWallet.transactions()
        );
    }
}
