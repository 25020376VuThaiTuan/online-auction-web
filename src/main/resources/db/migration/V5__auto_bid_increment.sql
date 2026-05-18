CREATE TABLE IF NOT EXISTS auto_bids (
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
