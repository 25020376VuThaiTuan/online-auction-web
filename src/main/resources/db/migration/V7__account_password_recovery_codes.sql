-- Adds hashed account password recovery-code storage.

SET @column_exists := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'users'
      AND COLUMN_NAME = 'password_recovery_code'
);
SET @migration_sql := IF(
    @column_exists = 0,
    'ALTER TABLE users ADD COLUMN password_recovery_code VARCHAR(255) NULL AFTER password_hash',
    'ALTER TABLE users MODIFY password_recovery_code VARCHAR(255) NULL'
);
PREPARE migration_statement FROM @migration_sql;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;

SET @column_exists := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'users'
      AND COLUMN_NAME = 'password_recovery_expires_at'
);
SET @migration_sql := IF(
    @column_exists = 0,
    'ALTER TABLE users ADD COLUMN password_recovery_expires_at DATETIME NULL AFTER password_recovery_code',
    'SELECT ''users.password_recovery_expires_at already exists'' AS migration_status'
);
PREPARE migration_statement FROM @migration_sql;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;
