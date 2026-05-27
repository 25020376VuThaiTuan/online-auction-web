package org.example.dao;

import org.example.auction.AuctionExtensionConfig;
import org.example.model.ApprovalStatus;
import org.example.model.Item;
import org.example.model.ItemFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ItemDAOTest {

    private Connection connection;

    private ItemDAO dao;

    @BeforeEach
    void setup() throws Exception {

        Class.forName("org.h2.Driver");

        connection =
                DriverManager.getConnection(
                        "jdbc:h2:mem:item_dao_" + UUID.randomUUID()
                                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE",
                        "sa",
                        ""
                );

        Statement st =
                connection.createStatement();

        st.execute("""
                CREATE TABLE categories (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    slug VARCHAR(50),
                    name VARCHAR(50),
                    description VARCHAR(255)
                )
                """);

        st.execute("""
                CREATE TABLE items (
                    id VARCHAR(36) PRIMARY KEY,
                    seller_id VARCHAR(36),
                    category_id BIGINT,
                    title VARCHAR(255),
                    description TEXT,
                    item_condition VARCHAR(50),
                    status VARCHAR(50),
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);

        st.execute("""
                CREATE TABLE auctions (
                    id VARCHAR(36) PRIMARY KEY,
                    item_id VARCHAR(36),
                    seller_id VARCHAR(36),
                    starting_price DECIMAL(19,2),
                    current_price DECIMAL(19,2),
                    minimum_increment DECIMAL(19,2),
                    start_at TIMESTAMP,
                    end_at TIMESTAMP,
                    anti_sniping_window_seconds INT DEFAULT 60,
                    extension_seconds INT DEFAULT 60,
                    extension_count INT DEFAULT 0,
                    max_extensions INT DEFAULT 2147483647,
                    status VARCHAR(50),
                    updated_at TIMESTAMP,
                    closed_at TIMESTAMP
                )
                """);

        st.execute("""
                CREATE TABLE auction_extensions (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    auction_id VARCHAR(36),
                    trigger_bid_id VARCHAR(36),
                    previous_end_at TIMESTAMP,
                    new_end_at TIMESTAMP,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);

        st.execute("""
                CREATE TABLE electronics_details (
                    item_id VARCHAR(36) PRIMARY KEY,
                    brand VARCHAR(255),
                    warranty_months INT
                )
                """);

        st.execute("""
                CREATE TABLE art_details (
                    item_id VARCHAR(36) PRIMARY KEY,
                    artist VARCHAR(255),
                    year_created INT
                )
                """);

        st.execute("""
                CREATE TABLE vehicle_details (
                    item_id VARCHAR(36) PRIMARY KEY,
                    manufacturer VARCHAR(255),
                    model_name VARCHAR(255),
                    mileage_km INT
                )
                """);

        dao = new ItemDAO(connection);
    }

    @AfterEach
    void cleanup() throws Exception {

        if (connection != null) {
            connection.close();
        }
    }

    @Test
    void shouldAddArtItem() throws Exception {

        Item item =
                ItemFactory.createItem(
                        "art",
                        "item-1",
                        "Mona Lisa",
                        "Painting",
                        1000,
                        LocalDateTime.now(),
                        LocalDateTime.now().plusDays(1),
                        "Da Vinci",
                        1503
                );

        item.setSellerId("seller-1");

        item.setApprovalStatus(
                ApprovalStatus.APPROVED
        );

        dao.addItem(
                item,
                "art",
                "Da Vinci",
                1503
        );

        Item result =
                dao.getItemById("item-1");

        assertNotNull(result);

        assertEquals(
                "Mona Lisa",
                result.getItemName()
        );
    }

    @Test
    void shouldReturnAllItems()
            throws Exception {

        Item item =
                ItemFactory.createItem(
                        "electronics",
                        "item-2",
                        "Laptop",
                        "Gaming laptop",
                        2000,
                        LocalDateTime.now(),
                        LocalDateTime.now().plusDays(1),
                        "Dell",
                        24
                );

        item.setSellerId("seller-1");

        item.setApprovalStatus(
                ApprovalStatus.APPROVED
        );

        dao.addItem(
                item,
                "electronics",
                "Dell",
                24
        );

        List<Item> items =
                dao.getAllItems();

        assertFalse(items.isEmpty());
    }

    @Test
    void shouldUpdateCurrentPrice()
            throws Exception {

        Item item =
                ItemFactory.createItem(
                        "vehicle",
                        "item-3",
                        "BMW",
                        "Luxury car",
                        5000,
                        LocalDateTime.now(),
                        LocalDateTime.now().plusDays(1),
                        "X5",
                        10000
                );

        item.setSellerId("seller-1");

        item.setApprovalStatus(
                ApprovalStatus.APPROVED
        );

        dao.addItem(
                item,
                "vehicle",
                "X5",
                10000
        );

        dao.updateCurrentPrice(
                "item-3",
                8000
        );

        Item updated =
                dao.getItemById("item-3");

        assertEquals(
                8000,
                updated.getCurrentPrice()
        );
    }

    @Test
    void shouldRecordAuctionExtension()
            throws Exception {

        LocalDateTime previousEndTime = LocalDateTime.of(2026, 5, 26, 16, 0);
        LocalDateTime extendedEndTime = previousEndTime.plusSeconds(60);

        Item item =
                ItemFactory.createItem(
                        "electronics",
                        "item-4",
                        "Camera",
                        "Vintage camera",
                        500,
                        LocalDateTime.of(2026, 5, 26, 15, 0),
                        previousEndTime,
                        "Nikon",
                        12
                );

        item.setSellerId("seller-1");
        item.setApprovalStatus(ApprovalStatus.APPROVED);

        dao.addItem(item, "electronics", "Nikon", 12);
        dao.recordAuctionExtension(item.getId(), "bid-1", previousEndTime, extendedEndTime);

        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("""
                     SELECT end_at, extension_count
                     FROM auctions
                     WHERE item_id = 'item-4'
                     """)) {
            assertTrue(rs.next());
            assertEquals(extendedEndTime, rs.getTimestamp("end_at").toLocalDateTime());
            assertEquals(1, rs.getInt("extension_count"));
        }

        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("""
                     SELECT COUNT(*) AS total
                     FROM auction_extensions
                     WHERE auction_id = 'item-4'
                     """)) {
            assertTrue(rs.next());
            assertEquals(1, rs.getInt("total"));
        }
    }

    @Test
    void shouldUseOneMinuteMinimumAntiSnipingWindowForLegacyRows()
            throws Exception {

        Item item =
                ItemFactory.createItem(
                        "electronics",
                        "item-5",
                        "Headphones",
                        "Legacy anti-sniping window",
                        100,
                        LocalDateTime.of(2026, 5, 26, 15, 0),
                        LocalDateTime.of(2026, 5, 26, 16, 0),
                        "Sony",
                        12
                );

        item.setSellerId("seller-1");
        item.setApprovalStatus(ApprovalStatus.APPROVED);

        dao.addItem(item, "electronics", "Sony", 12);
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    UPDATE auctions
                    SET anti_sniping_window_seconds = 10
                    WHERE item_id = 'item-5'
                    """);
        }

        AuctionExtensionConfig config = dao.getAuctionExtensionConfig(item.getId());

        assertEquals(60, config.triggerWindowSeconds());
    }

    @Test
    void shouldUpdateAuctionLifecycleFieldsAndLockRows()
            throws Exception {

        LocalDateTime start = LocalDateTime.of(2026, 5, 27, 9, 0);
        LocalDateTime end = start.plusHours(2);
        Item item = ItemFactory.createItem(
                "vehicle",
                "item-lifecycle",
                "Scooter",
                "City scooter",
                750,
                start,
                end,
                "Vision",
                1200
        );
        item.setSellerId("seller-2");
        item.setApprovalStatus(ApprovalStatus.PENDING);

        dao.addItem(item, "vehicle", "Vision", 1200);

        LocalDateTime finishedAt = end.plusMinutes(30);
        dao.updateAuctionProgress(item.getId(), 900.0, finishedAt, "FINISHED");
        dao.updateAuctionWindow(item.getId(), start.minusHours(1), finishedAt.plusHours(1), "OPEN");
        dao.updateApprovalStatus(item.getId(), ApprovalStatus.REJECTED);
        dao.lockAuctionForUpdate(item.getId());

        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("""
                     SELECT i.status AS item_status, a.status AS auction_status,
                            a.current_price, a.start_at, a.end_at
                     FROM items i
                     JOIN auctions a ON a.item_id = i.id
                     WHERE i.id = 'item-lifecycle'
                     """)) {
            assertTrue(rs.next());
            assertEquals("ARCHIVED", rs.getString("item_status"));
            assertEquals("CANCELLED", rs.getString("auction_status"));
            assertEquals(900.0, rs.getDouble("current_price"));
            assertEquals(start.minusHours(1), rs.getTimestamp("start_at").toLocalDateTime());
            assertEquals(finishedAt.plusHours(1), rs.getTimestamp("end_at").toLocalDateTime());
        }

        SQLException missing = assertThrows(
                SQLException.class,
                () -> dao.lockAuctionForUpdate("missing-item")
        );
        assertTrue(missing.getMessage().contains("Auction row not found"));
        assertNull(dao.getItemById("missing-item"));
    }

    @Test
    void shouldHandleMissingExtensionColumnsAndTables()
            throws Exception {

        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE auctions DROP COLUMN extension_count");
            statement.execute("DROP TABLE auction_extensions");
        }

        LocalDateTime previousEndTime = LocalDateTime.of(2026, 5, 27, 11, 0);
        LocalDateTime newEndTime = previousEndTime.plusMinutes(5);
        Item item = ItemFactory.createItem(
                "electronics",
                "item-legacy-extension",
                "Tablet",
                "Legacy extension schema",
                250,
                previousEndTime.minusHours(1),
                previousEndTime,
                "Samsung",
                6
        );
        item.setSellerId("seller-3");
        item.setApprovalStatus(ApprovalStatus.APPROVED);

        dao.addItem(item, "electronics", "Samsung", 6);

        AuctionExtensionConfig config = dao.getAuctionExtensionConfig(item.getId());
        assertEquals(AuctionExtensionConfig.defaults(), config);

        dao.recordAuctionExtension(item.getId(), "bid-legacy", previousEndTime, newEndTime);
        dao.recordAuctionExtension("", "bid-legacy", previousEndTime, newEndTime);
        dao.recordAuctionExtension(item.getId(), "", previousEndTime, newEndTime);
        dao.recordAuctionExtension(item.getId(), "bid-legacy", null, newEndTime);
        dao.recordAuctionExtension(item.getId(), "bid-legacy", previousEndTime, previousEndTime);

        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("""
                     SELECT end_at
                     FROM auctions
                     WHERE item_id = 'item-legacy-extension'
                     """)) {
            assertTrue(rs.next());
            assertEquals(newEndTime, rs.getTimestamp("end_at").toLocalDateTime());
        }
    }

    @Test
    void shouldNormalizeTypesDefaultsAndNullTimestamps()
            throws Exception {

        Item item = ItemFactory.createItem(
                "electronics",
                "item-normalized",
                "Mystery box",
                "Uses normalized fallback type",
                100,
                null,
                null,
                "",
                -5
        );
        item.setSellerId("seller-4");

        dao.addItem(item, "  collectibles  ", "   ", -5);

        Item result = dao.getItemById(item.getId());
        assertNotNull(result);
        assertEquals(ApprovalStatus.APPROVED, result.getApprovalStatus());
        assertNull(result.getStartTime());
        assertNull(result.getEndTime());

        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("""
                     SELECT brand, warranty_months
                     FROM electronics_details
                     WHERE item_id = 'item-normalized'
                     """)) {
            assertTrue(rs.next());
            assertEquals("Unknown", rs.getString("brand"));
            assertEquals(0, rs.getInt("warranty_months"));
        }

        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO categories (slug, name, description)
                    VALUES ('collectibles', 'Collectibles', 'Collectible items')
                    """);
            statement.execute("""
                    INSERT INTO items (id, seller_id, category_id, title, description, item_condition, status)
                    SELECT 'item-raw', 'seller-5', id, 'Raw item', 'Unknown category', 'USED', 'READY'
                    FROM categories
                    WHERE slug = 'collectibles'
                    """);
            statement.execute("""
                    INSERT INTO auctions (
                        id, item_id, seller_id, starting_price, current_price,
                        minimum_increment, start_at, end_at, status
                    ) VALUES (
                        'auction-raw', 'item-raw', 'seller-5', 10.00, 15.00,
                        1.00, NULL, NULL, 'OPEN'
                    )
                    """);
        }

        Item raw = dao.getItemById("item-raw");
        assertNotNull(raw);
        assertEquals(ApprovalStatus.APPROVED, raw.getApprovalStatus());
        assertEquals(15.0, raw.getCurrentPrice());
    }

    @Test
    void shouldCloseOwnedConnection()
            throws Exception {

        try (ItemDAO ownedDao = new ItemDAO(
                "jdbc:h2:mem:item_owned_" + UUID.randomUUID()
                        + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE",
                "sa",
                ""
        )) {
            assertNotNull(ownedDao);
        }
    }
}
