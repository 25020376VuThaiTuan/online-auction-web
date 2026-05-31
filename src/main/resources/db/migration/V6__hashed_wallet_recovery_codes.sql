-- Widens wallet recovery-code storage so generated recovery codes can be hashed.

SET @column_exists := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'wallet_accounts'
      AND COLUMN_NAME = 'pin_recovery_code'
);
SET @migration_sql := IF(
    @column_exists = 0,
    'ALTER TABLE wallet_accounts ADD COLUMN pin_recovery_code VARCHAR(255) NULL AFTER pin_hash',
    'ALTER TABLE wallet_accounts MODIFY pin_recovery_code VARCHAR(255) NULL'
);
PREPARE migration_statement FROM @migration_sql;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;

SET @column_exists := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'wallet_accounts'
      AND COLUMN_NAME = 'pin_recovery_expires_at'
);
SET @migration_sql := IF(
    @column_exists = 0,
    'ALTER TABLE wallet_accounts ADD COLUMN pin_recovery_expires_at DATETIME NULL AFTER pin_recovery_code',
    'SELECT ''wallet_accounts.pin_recovery_expires_at already exists'' AS migration_status'
);
PREPARE migration_statement FROM @migration_sql;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;
