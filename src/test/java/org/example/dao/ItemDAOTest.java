package org.example.dao;

import org.example.model.ApprovalStatus;
import org.example.model.Item;
import org.example.model.ItemFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
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
                    status VARCHAR(50),
                    updated_at TIMESTAMP,
                    closed_at TIMESTAMP
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
}
