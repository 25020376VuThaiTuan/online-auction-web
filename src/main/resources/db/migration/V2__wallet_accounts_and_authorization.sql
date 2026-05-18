CREATE TABLE IF NOT EXISTS wallet_linked_accounts (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    account_name VARCHAR(150) NOT NULL,
    provider_name VARCHAR(100) NOT NULL,
    account_reference VARCHAR(100) NOT NULL,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_wallet_linked_accounts_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
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

CREATE INDEX idx_wallet_linked_accounts_user
    ON wallet_linked_accounts (user_id, is_primary DESC, created_at DESC);
