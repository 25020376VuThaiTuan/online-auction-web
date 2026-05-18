ALTER TABLE wallet_linked_accounts
    ADD COLUMN balance DECIMAL(15, 2) NOT NULL DEFAULT 0.00 AFTER account_reference;

ALTER TABLE wallet_linked_accounts
    ADD CONSTRAINT chk_wallet_linked_accounts_balance
        CHECK (balance >= 0);
