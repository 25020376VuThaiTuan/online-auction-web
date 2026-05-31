-- Adds per-auction automatic bid increments.

SET @column_exists := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'auto_bids'
      AND COLUMN_NAME = 'bid_increment'
);
SET @migration_sql := IF(
    @column_exists = 0,
    'ALTER TABLE auto_bids ADD COLUMN bid_increment DECIMAL(15, 2) NOT NULL DEFAULT 0.00 AFTER max_limit',
    'SELECT ''auto_bids.bid_increment already exists'' AS migration_status'
);
PREPARE migration_statement FROM @migration_sql;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;
