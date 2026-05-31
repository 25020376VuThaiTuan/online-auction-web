-- Adds admin-controlled account login bans.
-- Apply with:
--   mysql -u root -p auctiondb < src/main/resources/db/migration/V8__account_login_bans.sql

SET @schema_name := DATABASE();

SET @migration_sql := IF(
    NOT EXISTS (
        SELECT 1
        FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = @schema_name
          AND TABLE_NAME = 'users'
          AND COLUMN_NAME = 'account_banned'
    ),
    'ALTER TABLE users ADD COLUMN account_banned BOOLEAN NOT NULL DEFAULT FALSE AFTER avatar_url',
    'SELECT ''users.account_banned already exists'' AS migration_status'
);

PREPARE migration_statement FROM @migration_sql;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;
