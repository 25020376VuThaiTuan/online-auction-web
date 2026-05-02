-- Database setup for Online Auction System

CREATE DATABASE IF NOT EXISTS auction_db;
USE auction_db;

-- Users table
CREATE TABLE IF NOT EXISTS users (
    id VARCHAR(50) PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL
);

-- Items table
CREATE TABLE IF NOT EXISTS items (
    id VARCHAR(50) PRIMARY KEY,
    item_type VARCHAR(50) NOT NULL,
    item_name VARCHAR(255) NOT NULL,
    description TEXT,
    starting_price DOUBLE NOT NULL,
    current_price DOUBLE NOT NULL,
    seller_id VARCHAR(50),
    approval_status VARCHAR(50) DEFAULT 'APPROVED',
    start_time DATETIME,
    end_time DATETIME,
    extra_str VARCHAR(255),
    extra_int INT,
    FOREIGN KEY (seller_id) REFERENCES users(id) ON DELETE SET NULL
);

-- Bids table
CREATE TABLE IF NOT EXISTS bids (
    id VARCHAR(50) PRIMARY KEY,
    bidder_id VARCHAR(50) NOT NULL,
    item_id VARCHAR(50) NOT NULL,
    amount DOUBLE NOT NULL,
    bid_time DATETIME NOT NULL,
    FOREIGN KEY (bidder_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (item_id) REFERENCES items(id) ON DELETE CASCADE
);

-- Auto Bids table
CREATE TABLE IF NOT EXISTS auto_bids (
    id INT AUTO_INCREMENT PRIMARY KEY,
    bidder_id VARCHAR(50) NOT NULL,
    item_id VARCHAR(50) NOT NULL,
    max_limit DOUBLE NOT NULL,
    FOREIGN KEY (bidder_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (item_id) REFERENCES items(id) ON DELETE CASCADE,
    UNIQUE KEY unique_auto_bid (bidder_id, item_id)
);
