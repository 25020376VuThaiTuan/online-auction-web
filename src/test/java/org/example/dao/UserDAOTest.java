package org.example.dao;

import org.example.model.Bidder;
import org.example.model.Seller;
import org.example.model.User;
import org.junit.jupiter.api.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class UserDAOTest {

    private Connection connection;
    private UserDAO userDAO;

    @BeforeEach
    void setUp() throws Exception {
        connection = DriverManager.getConnection(
                "jdbc:h2:mem:user_dao_" + UUID.randomUUID()
                        + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE",
                "sa",
                ""
        );

        createTables();

        userDAO = new UserDAO(connection);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    private void createTables() throws Exception {
        try (Statement st = connection.createStatement()) {

            st.execute("""
                    CREATE TABLE users (
                        id VARCHAR(36) PRIMARY KEY,
                        username VARCHAR(255),
                        email VARCHAR(255),
                        password_hash VARCHAR(255),
                        role VARCHAR(20),
                        full_name VARCHAR(255),
                        phone VARCHAR(20),
                        avatar_url VARCHAR(255),
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        last_login_at TIMESTAMP NULL
                    )
                    """);

            st.execute("""
                    CREATE TABLE bidder_profiles (
                        user_id VARCHAR(36) PRIMARY KEY,
                        wallet_balance DECIMAL(15,2)
                    )
                    """);

            st.execute("""
                    CREATE TABLE seller_profiles (
                        user_id VARCHAR(36) PRIMARY KEY,
                        store_name VARCHAR(255)
                    )
                    """);

            st.execute("""
                    CREATE TABLE admin_profiles (
                        user_id VARCHAR(36) PRIMARY KEY
                    )
                    """);

            st.execute("""
                    CREATE TABLE user_addresses (
                        user_id VARCHAR(36),
                        address_label VARCHAR(50),
                        contact_name VARCHAR(255),
                        line_1 VARCHAR(255),
                        city VARCHAR(100),
                        country_code VARCHAR(10),
                        is_default BOOLEAN
                    )
                    """);
        }
    }

    @Test
    void testAddAndFindBidder() throws Exception {

        Bidder bidder = new Bidder(
                "u1",
                "bidder1",
                "123",
                "bidder@gmail.com",
                500.0
        );

        bidder.setRole("BIDDER");
        bidder.setFullName("Nguyen Van A");

        userDAO.addUser(bidder);

        Optional<User> result = userDAO.findByUsername("bidder1");

        assertTrue(result.isPresent());
        assertEquals("bidder1", result.get().getUsername());
        assertEquals("BIDDER", result.get().getRole());
    }

    @Test
    void testAddAndFindSeller() throws Exception {

        Seller seller = new Seller(
                "s1",
                "seller1",
                "123",
                "seller@gmail.com"
        );

        seller.setRole("SELLER");
        seller.setFullName("Tran Van B");

        userDAO.addUser(seller);

        Optional<User> result = userDAO.findByEmail("seller@gmail.com");

        assertTrue(result.isPresent());
        assertEquals("seller1", result.get().getUsername());
        assertEquals("SELLER", result.get().getRole());
    }

    @Test
    void testGetAllUsers() throws Exception {

        Bidder bidder = new Bidder(
                "u1",
                "bidder1",
                "123",
                "b@gmail.com",
                100
        );

        Seller seller = new Seller(
                "s1",
                "seller1",
                "123",
                "s@gmail.com"
        );

        bidder.setRole("BIDDER");
        seller.setRole("SELLER");

        userDAO.addUser(bidder);
        userDAO.addUser(seller);

        List<User> users = userDAO.getAllUsers();

        assertEquals(2, users.size());
    }

    @Test
    void testUpdateUser() throws Exception {

        Seller seller = new Seller(
                "s1",
                "seller1",
                "123",
                "seller@gmail.com"
        );

        seller.setRole("SELLER");

        userDAO.addUser(seller);

        seller.setFullName("Updated Name");
        seller.setPhoneNumber("0123456789");

        userDAO.updateUser(seller);

        User updated = userDAO.getUserById("s1");

        assertEquals("Updated Name", updated.getFullName());
        assertEquals("0123456789", updated.getPhoneNumber());
    }

    @Test
    void testDeleteUser() throws Exception {

        Seller seller = new Seller(
                "s1",
                "seller1",
                "123",
                "seller@gmail.com"
        );

        seller.setRole("SELLER");

        userDAO.addUser(seller);

        userDAO.deleteUser("s1");

        User deleted = userDAO.getUserById("s1");

        assertNull(deleted);
    }

    @Test
    void testRecordLogin() throws Exception {

        Seller seller = new Seller(
                "s1",
                "seller1",
                "123",
                "seller@gmail.com"
        );

        seller.setRole("SELLER");

        userDAO.addUser(seller);

        userDAO.recordLogin("s1");

        User result = userDAO.getUserById("s1");

        assertNotNull(result);
    }

    @Test
    void testFindByUsernameReturnsEmpty() throws Exception {

        Optional<User> result = userDAO.findByUsername("unknown");

        assertTrue(result.isEmpty());
    }
}
