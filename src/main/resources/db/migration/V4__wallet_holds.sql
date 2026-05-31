-- Adds wallet holds for bid authorization and expands wallet transaction types.

CREATE TABLE IF NOT EXISTS wallet_holds (
    user_id VARCHAR(36) NOT NULL,
    hold_key VARCHAR(120) NOT NULL,
    reference_id VARCHAR(36) NULL,
    amount DECIMAL(15, 2) NOT NULL,
    note VARCHAR(255) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, hold_key),
    CONSTRAINT fk_wallet_holds_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT chk_wallet_holds_amount
        CHECK (amount >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE wallet_transactions
    MODIFY transaction_type ENUM(
        'TOP_UP',
        'WITHDRAWAL',
        'BID_HOLD',
        'BID_RELEASE',
        'PAYMENT',
        'REFUND',
        'ADJUSTMENT',
        'SELLER_PAYOUT',
        'ADMIN_FEE',
        'PIN_RESET'
    ) NOT NULL;
