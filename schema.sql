CREATE DATABASE IF NOT EXISTS auctiondb
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE auctiondb;

-- Users are modeled in one shared table because Admin, Seller, and Bidder
-- all inherit from User in the current Java model.
CREATE TABLE users (
                       id VARCHAR(36) PRIMARY KEY,
                       username VARCHAR(100) NOT NULL,
                       email VARCHAR(255) NOT NULL,
                       password_hash VARCHAR(255) NOT NULL,
                       role ENUM('ADMIN', 'SELLER', 'BIDDER') NOT NULL,
                       status ENUM('PENDING', 'ACTIVE', 'SUSPENDED', 'BANNED') NOT NULL DEFAULT 'ACTIVE',
                       full_name VARCHAR(150) NULL,
                       phone VARCHAR(30) NULL,
                       avatar_url VARCHAR(500) NULL,
                       last_login_at DATETIME NULL,
                       created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                       updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                       UNIQUE KEY uq_users_username (username),
                       UNIQUE KEY uq_users_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE admin_profiles (
                                user_id VARCHAR(36) PRIMARY KEY,
                                is_super_admin BOOLEAN NOT NULL DEFAULT FALSE,
                                notes VARCHAR(500) NULL,
                                created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                CONSTRAINT fk_admin_profiles_user
                                    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE seller_profiles (
                                 user_id VARCHAR(36) PRIMARY KEY,
                                 store_name VARCHAR(150) NOT NULL,
                                 verification_status ENUM('PENDING', 'VERIFIED', 'REJECTED') NOT NULL DEFAULT 'PENDING',
                                 rating_average DECIMAL(4, 2) NOT NULL DEFAULT 0.00,
                                 total_sales_amount DECIMAL(15, 2) NOT NULL DEFAULT 0.00,
                                 payout_preference ENUM('WALLET', 'MANUAL') NOT NULL DEFAULT 'WALLET',
                                 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                 updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                 CONSTRAINT fk_seller_profiles_user
                                     FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE bidder_profiles (
                                 user_id VARCHAR(36) PRIMARY KEY,
                                 wallet_balance DECIMAL(15, 2) NOT NULL DEFAULT 0.00,
                                 default_bid_limit DECIMAL(15, 2) NULL,
                                 kyc_status ENUM('NOT_REQUIRED', 'PENDING', 'VERIFIED', 'REJECTED') NOT NULL DEFAULT 'NOT_REQUIRED',
                                 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                 updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                 CONSTRAINT fk_bidder_profiles_user
                                     FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
                                 CONSTRAINT chk_bidder_profiles_wallet_balance
                                     CHECK (wallet_balance >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE wallet_accounts (
                                 user_id VARCHAR(36) PRIMARY KEY,
                                 balance DECIMAL(15, 2) NOT NULL DEFAULT 0.00,
                                 pin_hash VARCHAR(255) NULL,
                                 pin_recovery_code VARCHAR(255) NULL,
                                 pin_recovery_expires_at DATETIME NULL,
                                 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                 updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                 CONSTRAINT fk_wallet_accounts_user
                                     FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
                                 CONSTRAINT chk_wallet_accounts_balance
                                     CHECK (balance >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE wallet_linked_accounts (
                                        id VARCHAR(36) PRIMARY KEY,
                                        user_id VARCHAR(36) NOT NULL,
                                        account_name VARCHAR(150) NOT NULL,
                                        provider_name VARCHAR(100) NOT NULL,
                                        account_reference VARCHAR(100) NOT NULL,
                                        balance DECIMAL(15, 2) NOT NULL DEFAULT 0.00,
                                        is_primary BOOLEAN NOT NULL DEFAULT FALSE,
                                        created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                        updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                        CONSTRAINT fk_wallet_linked_accounts_user
                                            FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
                                        CONSTRAINT chk_wallet_linked_accounts_balance
                                            CHECK (balance >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE user_addresses (
                                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                user_id VARCHAR(36) NOT NULL,
                                address_label VARCHAR(100) NOT NULL,
                                contact_name VARCHAR(150) NOT NULL,
                                line_1 VARCHAR(255) NOT NULL,
                                line_2 VARCHAR(255) NULL,
                                city VARCHAR(100) NOT NULL,
                                state_region VARCHAR(100) NULL,
                                postal_code VARCHAR(30) NULL,
                                country_code CHAR(2) NOT NULL,
                                is_default BOOLEAN NOT NULL DEFAULT FALSE,
                                created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                CONSTRAINT fk_user_addresses_user
                                    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE categories (
                            id BIGINT AUTO_INCREMENT PRIMARY KEY,
                            slug VARCHAR(50) NOT NULL,
                            name VARCHAR(100) NOT NULL,
                            description VARCHAR(255) NULL,
                            created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                            UNIQUE KEY uq_categories_slug (slug),
                            UNIQUE KEY uq_categories_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE items (
                       id VARCHAR(36) PRIMARY KEY,
                       seller_id VARCHAR(36) NOT NULL,
                       category_id BIGINT NOT NULL,
                       title VARCHAR(200) NOT NULL,
                       description TEXT NOT NULL,
                       item_condition ENUM('NEW', 'LIKE_NEW', 'USED', 'REFURBISHED', 'DAMAGED') NOT NULL DEFAULT 'USED',
                       location_text VARCHAR(255) NULL,
                       status ENUM('DRAFT', 'PENDING_APPROVAL', 'READY', 'ARCHIVED') NOT NULL DEFAULT 'DRAFT',
                       created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                       updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                       CONSTRAINT fk_items_seller
                           FOREIGN KEY (seller_id) REFERENCES users(id),
                       CONSTRAINT fk_items_category
                           FOREIGN KEY (category_id) REFERENCES categories(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE item_images (
                             id BIGINT AUTO_INCREMENT PRIMARY KEY,
                             item_id VARCHAR(36) NOT NULL,
                             image_url VARCHAR(500) NOT NULL,
                             is_primary BOOLEAN NOT NULL DEFAULT FALSE,
                             sort_order INT NOT NULL DEFAULT 0,
                             created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                             CONSTRAINT fk_item_images_item
                                 FOREIGN KEY (item_id) REFERENCES items(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE electronics_details (
                                     item_id VARCHAR(36) PRIMARY KEY,
                                     brand VARCHAR(100) NOT NULL,
                                     model_name VARCHAR(150) NULL,
                                     warranty_months INT NOT NULL DEFAULT 0,
                                     serial_number VARCHAR(100) NULL,
                                     CONSTRAINT fk_electronics_details_item
                                         FOREIGN KEY (item_id) REFERENCES items(id) ON DELETE CASCADE,
                                     CONSTRAINT chk_electronics_details_warranty
                                         CHECK (warranty_months >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE art_details (
                             item_id VARCHAR(36) PRIMARY KEY,
                             artist VARCHAR(150) NOT NULL,
                             year_created INT NULL,
                             medium VARCHAR(100) NULL,
                             width_cm DECIMAL(10, 2) NULL,
                             height_cm DECIMAL(10, 2) NULL,
                             authenticity_certificate_url VARCHAR(500) NULL,
                             CONSTRAINT fk_art_details_item
                                 FOREIGN KEY (item_id) REFERENCES items(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE vehicle_details (
                                 item_id VARCHAR(36) PRIMARY KEY,
                                 manufacturer VARCHAR(100) NOT NULL,
                                 model_name VARCHAR(150) NOT NULL,
                                 manufacture_year INT NULL,
                                 mileage_km INT NOT NULL DEFAULT 0,
                                 vin VARCHAR(50) NULL,
                                 CONSTRAINT fk_vehicle_details_item
                                     FOREIGN KEY (item_id) REFERENCES items(id) ON DELETE CASCADE,
                                 CONSTRAINT chk_vehicle_details_mileage
                                     CHECK (mileage_km >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE auctions (
                          id VARCHAR(36) PRIMARY KEY,
                          item_id VARCHAR(36) NOT NULL,
                          seller_id VARCHAR(36) NOT NULL,
                          starting_price DECIMAL(15, 2) NOT NULL,
                          reserve_price DECIMAL(15, 2) NULL,
                          buy_now_price DECIMAL(15, 2) NULL,
                          current_price DECIMAL(15, 2) NOT NULL,
                          minimum_increment DECIMAL(15, 2) NOT NULL DEFAULT 1.00,
                          currency_code CHAR(3) NOT NULL DEFAULT 'USD',
                          start_at DATETIME NOT NULL,
                          end_at DATETIME NOT NULL,
                          status ENUM('DRAFT', 'OPEN', 'RUNNING', 'FINISHED', 'PAID', 'CANCELLED') NOT NULL DEFAULT 'OPEN',
                          anti_sniping_window_seconds INT NOT NULL DEFAULT 60,
                          extension_seconds INT NOT NULL DEFAULT 60,
                          extension_count INT NOT NULL DEFAULT 0,
                          max_extensions INT NOT NULL DEFAULT 2147483647,
                          winner_bidder_id VARCHAR(36) NULL,
                          winning_bid_id VARCHAR(36) NULL,
                          closed_at DATETIME NULL,
                          created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                          updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                          CONSTRAINT fk_auctions_item
                              FOREIGN KEY (item_id) REFERENCES items(id),
                          CONSTRAINT fk_auctions_seller
                              FOREIGN KEY (seller_id) REFERENCES users(id),
                          CONSTRAINT fk_auctions_winner_bidder
                              FOREIGN KEY (winner_bidder_id) REFERENCES users(id),
                          CONSTRAINT chk_auctions_price_floor
                              CHECK (starting_price >= 0 AND current_price >= 0),
                          CONSTRAINT chk_auctions_min_increment
                              CHECK (minimum_increment > 0),
                          CONSTRAINT chk_auctions_times
                              CHECK (end_at > start_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE bids (
                      id VARCHAR(36) PRIMARY KEY,
                      auction_id VARCHAR(36) NOT NULL,
                      bidder_id VARCHAR(36) NOT NULL,
                      amount DECIMAL(15, 2) NOT NULL,
                      bid_source ENUM('DESKTOP', 'WEB', 'API', 'AUTO') NOT NULL DEFAULT 'DESKTOP',
                      status ENUM('VALID', 'OUTBID', 'WINNING', 'REJECTED', 'CANCELLED') NOT NULL DEFAULT 'VALID',
                      ip_address VARCHAR(45) NULL,
                      user_agent VARCHAR(255) NULL,
                      placed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      CONSTRAINT fk_bids_auction
                          FOREIGN KEY (auction_id) REFERENCES auctions(id) ON DELETE CASCADE,
                      CONSTRAINT fk_bids_bidder
                          FOREIGN KEY (bidder_id) REFERENCES users(id),
                      CONSTRAINT chk_bids_amount
                          CHECK (amount > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE auctions
    ADD CONSTRAINT fk_auctions_winning_bid
        FOREIGN KEY (winning_bid_id) REFERENCES bids(id);

CREATE TABLE auction_extensions (
                                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                    auction_id VARCHAR(36) NOT NULL,
                                    trigger_bid_id VARCHAR(36) NOT NULL,
                                    previous_end_at DATETIME NOT NULL,
                                    new_end_at DATETIME NOT NULL,
                                    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                    CONSTRAINT fk_auction_extensions_auction
                                        FOREIGN KEY (auction_id) REFERENCES auctions(id) ON DELETE CASCADE,
                                    CONSTRAINT fk_auction_extensions_bid
                                        FOREIGN KEY (trigger_bid_id) REFERENCES bids(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE watchlists (
                            bidder_id VARCHAR(36) NOT NULL,
                            auction_id VARCHAR(36) NOT NULL,
                            created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                            PRIMARY KEY (bidder_id, auction_id),
                            CONSTRAINT fk_watchlists_bidder
                                FOREIGN KEY (bidder_id) REFERENCES users(id) ON DELETE CASCADE,
                              CONSTRAINT fk_watchlists_auction
                                  FOREIGN KEY (auction_id) REFERENCES auctions(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE auto_bids (
                           id BIGINT AUTO_INCREMENT PRIMARY KEY,
                           bidder_id VARCHAR(36) NOT NULL,
                           auction_id VARCHAR(36) NOT NULL,
                           max_limit DECIMAL(15, 2) NOT NULL,
                           bid_increment DECIMAL(15, 2) NOT NULL DEFAULT 0.00,
                           created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                           updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                           UNIQUE KEY uq_auto_bids_bidder_auction (bidder_id, auction_id),
                           CONSTRAINT fk_auto_bids_bidder
                               FOREIGN KEY (bidder_id) REFERENCES users(id) ON DELETE CASCADE,
                           CONSTRAINT fk_auto_bids_auction
                               FOREIGN KEY (auction_id) REFERENCES auctions(id) ON DELETE CASCADE,
                           CONSTRAINT chk_auto_bids_max_limit
                               CHECK (max_limit > 0),
                           CONSTRAINT chk_auto_bids_bid_increment
                               CHECK (bid_increment >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE payments (
                          id VARCHAR(36) PRIMARY KEY,
                          auction_id VARCHAR(36) NOT NULL,
                          winning_bid_id VARCHAR(36) NOT NULL,
                          payer_id VARCHAR(36) NOT NULL,
                          payee_id VARCHAR(36) NOT NULL,
                          gross_amount DECIMAL(15, 2) NOT NULL,
                          platform_fee DECIMAL(15, 2) NOT NULL DEFAULT 0.00,
                          net_amount DECIMAL(15, 2) NOT NULL,
                          provider VARCHAR(50) NOT NULL,
                          provider_reference VARCHAR(100) NULL,
                          status ENUM('PENDING', 'AUTHORIZED', 'PAID', 'FAILED', 'REFUNDED', 'EXPIRED') NOT NULL DEFAULT 'PENDING',
                          paid_at DATETIME NULL,
                          created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                          updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                          CONSTRAINT fk_payments_auction
                              FOREIGN KEY (auction_id) REFERENCES auctions(id),
                          CONSTRAINT fk_payments_bid
                              FOREIGN KEY (winning_bid_id) REFERENCES bids(id),
                          CONSTRAINT fk_payments_payer
                              FOREIGN KEY (payer_id) REFERENCES users(id),
                          CONSTRAINT fk_payments_payee
                              FOREIGN KEY (payee_id) REFERENCES users(id),
                          CONSTRAINT chk_payments_amounts
                              CHECK (gross_amount >= 0 AND platform_fee >= 0 AND net_amount >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE payouts (
                         id VARCHAR(36) PRIMARY KEY,
                         payment_id VARCHAR(36) NOT NULL,
                         seller_id VARCHAR(36) NOT NULL,
                         gross_amount DECIMAL(15, 2) NOT NULL,
                         fee_amount DECIMAL(15, 2) NOT NULL DEFAULT 0.00,
                         net_amount DECIMAL(15, 2) NOT NULL,
                         status ENUM('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED') NOT NULL DEFAULT 'PENDING',
                         processed_at DATETIME NULL,
                         created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                         CONSTRAINT fk_payouts_payment
                             FOREIGN KEY (payment_id) REFERENCES payments(id),
                         CONSTRAINT fk_payouts_seller
                             FOREIGN KEY (seller_id) REFERENCES users(id),
                         CONSTRAINT chk_payouts_amounts
                             CHECK (gross_amount >= 0 AND fee_amount >= 0 AND net_amount >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE wallet_transactions (
                                     id VARCHAR(36) PRIMARY KEY,
                                     user_id VARCHAR(36) NOT NULL,
                                     reference_id VARCHAR(36) NULL,
                                     transaction_type ENUM('TOP_UP', 'WITHDRAWAL', 'BID_HOLD', 'BID_RELEASE', 'PAYMENT', 'REFUND', 'ADJUSTMENT', 'SELLER_PAYOUT', 'ADMIN_FEE', 'PIN_RESET') NOT NULL,
                                     amount DECIMAL(15, 2) NOT NULL,
                                     balance_before DECIMAL(15, 2) NOT NULL,
                                     balance_after DECIMAL(15, 2) NOT NULL,
                                     note VARCHAR(255) NULL,
                                     created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                     CONSTRAINT fk_wallet_transactions_user
                                         FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE wallet_holds (
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

CREATE TABLE notifications (
                               id BIGINT AUTO_INCREMENT PRIMARY KEY,
                               user_id VARCHAR(36) NOT NULL,
                               notification_type VARCHAR(50) NOT NULL,
                               channel ENUM('IN_APP', 'EMAIL', 'SMS') NOT NULL DEFAULT 'IN_APP',
                               title VARCHAR(150) NOT NULL,
                               body TEXT NOT NULL,
                               is_read BOOLEAN NOT NULL DEFAULT FALSE,
                               created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                               read_at DATETIME NULL,
                               CONSTRAINT fk_notifications_user
                                   FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE auth_sessions (
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

CREATE TABLE audit_logs (
                            id BIGINT AUTO_INCREMENT PRIMARY KEY,
                            actor_user_id VARCHAR(36) NULL,
                            entity_type VARCHAR(50) NOT NULL,
                            entity_id VARCHAR(36) NOT NULL,
                            action_type VARCHAR(50) NOT NULL,
                            payload_json JSON NULL,
                            created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                            CONSTRAINT fk_audit_logs_actor
                                FOREIGN KEY (actor_user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_items_seller_status
    ON items (seller_id, status);

CREATE INDEX idx_auctions_status_end_at
    ON auctions (status, end_at);

CREATE INDEX idx_auctions_item
    ON auctions (item_id);

CREATE INDEX idx_bids_auction_amount
    ON bids (auction_id, amount DESC);

CREATE INDEX idx_bids_bidder_time
    ON bids (bidder_id, placed_at DESC);

CREATE INDEX idx_notifications_user_read
    ON notifications (user_id, is_read, created_at DESC);

CREATE INDEX idx_wallet_transactions_user_time
    ON wallet_transactions (user_id, created_at DESC);

CREATE INDEX idx_wallet_linked_accounts_user
    ON wallet_linked_accounts (user_id, is_primary DESC, created_at DESC);

CREATE INDEX idx_auth_sessions_user
    ON auth_sessions (user_id, expires_at);

INSERT INTO categories (slug, name, description)
VALUES
    ('Electronics', 'Electronics', 'Devices, computers, and consumer electronics'),
    ('Art', 'Art', 'Paintings, sculptures, and other art pieces'),
    ('Vehicle', 'Vehicle', 'Cars, motorcycles, and other vehicles');
