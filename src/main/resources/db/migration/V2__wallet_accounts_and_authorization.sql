-- Adds wallet persistence and API session storage for existing remote schemas.
-- Run with auctiondb selected, for example:
--   mysql -u root -p auctiondb < src/main/resources/db/migration/V2__wallet_accounts_and_authorization.sql

CREATE TABLE IF NOT EXISTS wallet_accounts (
    user_id VARCHAR(36) PRIMARY KEY,
    balance DECIMAL(15, 2) NOT NULL DEFAULT 0.00,
    pin_hash VARCHAR(255) NULL,
    pin_recovery_code VARCHAR(32) NULL,
    pin_recovery_expires_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_wallet_accounts_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT chk_wallet_accounts_balance
        CHECK (balance >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO wallet_accounts (user_id, balance)
SELECT bp.user_id, COALESCE(bp.wallet_balance, 0.00)
FROM bidder_profiles bp
JOIN users u ON u.id = bp.user_id
ON DUPLICATE KEY UPDATE updated_at = CURRENT_TIMESTAMP;

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

CREATE TABLE IF NOT EXISTS wallet_transactions (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    reference_id VARCHAR(36) NULL,
    transaction_type ENUM(
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
    ) NOT NULL,
    amount DECIMAL(15, 2) NOT NULL,
    balance_before DECIMAL(15, 2) NOT NULL,
    balance_after DECIMAL(15, 2) NOT NULL,
    note VARCHAR(255) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_wallet_transactions_user
        FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS auth_sessions (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    refresh_token_hash VARCHAR(255) NOT NULL,
    user_agent VARCHAR(255) NULL,
    ip_address VARCHAR(45) NULL,
    expires_at DATETIME NOT NULL,
    revoked_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_auth_sessions_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
