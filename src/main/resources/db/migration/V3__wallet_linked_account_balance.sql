-- Adds balances to linked wallet accounts introduced by V2.

SET @column_exists := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'wallet_linked_accounts'
      AND COLUMN_NAME = 'balance'
);
SET @migration_sql := IF(
    @column_exists = 0,
    'ALTER TABLE wallet_linked_accounts ADD COLUMN balance DECIMAL(15, 2) NOT NULL DEFAULT 0.00 AFTER account_reference',
    'SELECT ''wallet_linked_accounts.balance already exists'' AS migration_status'
);
PREPARE migration_statement FROM @migration_sql;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;

SET @index_exists := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'wallet_linked_accounts'
      AND INDEX_NAME = 'idx_wallet_linked_accounts_user'
);
SET @migration_sql := IF(
    @index_exists = 0,
    'CREATE INDEX idx_wallet_linked_accounts_user ON wallet_linked_accounts (user_id, is_primary DESC, created_at DESC)',
    'SELECT ''idx_wallet_linked_accounts_user already exists'' AS migration_status'
);
PREPARE migration_statement FROM @migration_sql;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;
