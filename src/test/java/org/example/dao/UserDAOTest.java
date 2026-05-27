package org.example.dao;

import org.example.model.Admin;
import org.example.model.Bidder;
import org.example.model.Seller;
import org.example.model.User;
import org.example.util.CredentialHasher;
import org.junit.jupiter.api.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLIntegrityConstraintViolationException;
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

    @Test
    void nullAndBlankInputsShouldBeNoOps()
            throws Exception {

        userDAO.addUser(null);
        userDAO.updateUser(null);
        userDAO.recordLogin(" ");

        assertTrue(userDAO.getAllUsers().isEmpty());
    }

    @Test
    void shouldPersistAdminProfileGeneratedEmailAddressAndExistingHash()
            throws Exception {

        String passwordHash = CredentialHasher.hash("admin-secret");
        Admin admin = new Admin(
                "admin-1",
                "admin1",
                passwordHash,
                " "
        );
        admin.setRole(" admin ");
        admin.setFullName("  Ada Admin  ");
        admin.setPhoneNumber(" 0900000000 ");
        admin.setAvatarUrl(" https://example.test/avatar.png ");
        admin.setAddress(" 1 Admin Street ");

        userDAO.addUser(admin);

        User result = userDAO.getUserById("admin-1");
        assertNotNull(result);
        assertEquals("ADMIN", result.getRole());
        assertEquals("admin1@local", result.getEmail());
        assertEquals(passwordHash, result.getPasswordHash());
        assertEquals("Ada Admin", result.getFullName());
        assertEquals("0900000000", result.getPhoneNumber());
        assertEquals("https://example.test/avatar.png", result.getAvatarUrl());
        assertEquals("1 Admin Street", result.getAddress());

        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("""
                     SELECT COUNT(*) AS total
                     FROM admin_profiles
                     WHERE user_id = 'admin-1'
                     """)) {
            assertTrue(rs.next());
            assertEquals(1, rs.getInt("total"));
        }
    }

    @Test
    void shouldDefaultUnknownRoleToBidderProfile()
            throws Exception {

        Seller sellerWithoutRole = new Seller(
                "default-role-1",
                "defaultrole",
                "secret",
                ""
        );

        userDAO.addUser(sellerWithoutRole);

        User result = userDAO.getUserById("default-role-1");
        assertInstanceOf(Bidder.class, result);
        assertEquals("BIDDER", result.getRole());
        assertEquals("defaultrole@local", result.getEmail());
        assertEquals(0.0, ((Bidder) result).getBalance());
    }

    @Test
    void duplicateUsernameOrEmailShouldRollbackAndReportConstraintViolation()
            throws Exception {

        Seller first = new Seller(
                "seller-a",
                "sellerA",
                "secret",
                "seller-a@test.local"
        );
        first.setRole("SELLER");
        Seller second = new Seller(
                "seller-b",
                "sellerB",
                "secret",
                "seller-b@test.local"
        );
        second.setRole("SELLER");

        userDAO.addUser(first);
        userDAO.addUser(second);

        Seller usernameConflict = new Seller(
                "seller-a",
                "sellerB",
                "secret",
                "seller-a-new@test.local"
        );
        usernameConflict.setRole("SELLER");

        SQLIntegrityConstraintViolationException usernameError = assertThrows(
                SQLIntegrityConstraintViolationException.class,
                () -> userDAO.updateUser(usernameConflict)
        );
        assertEquals("Username is already registered.", usernameError.getMessage());
        assertTrue(connection.getAutoCommit());

        Seller emailConflict = new Seller(
                "seller-c",
                "sellerC",
                "secret",
                "seller-b@test.local"
        );
        emailConflict.setRole("SELLER");

        SQLIntegrityConstraintViolationException emailError = assertThrows(
                SQLIntegrityConstraintViolationException.class,
                () -> userDAO.addUser(emailConflict)
        );
        assertEquals("Email address is already registered.", emailError.getMessage());
        assertNull(userDAO.getUserById("seller-c"));
        assertTrue(connection.getAutoCommit());
    }

    @Test
    void blankPasswordShouldFailWithoutLeavingTransactionOpen()
            throws Exception {

        Seller seller = new Seller(
                "bad-password",
                "badpassword",
                " ",
                "bad-password@test.local"
        );
        seller.setRole("SELLER");

        assertThrows(
                IllegalArgumentException.class,
                () -> userDAO.addUser(seller)
        );
        assertNull(userDAO.getUserById("bad-password"));
        assertTrue(connection.getAutoCommit());
    }

    @Test
    void shouldCloseOwnedConnection()
            throws Exception {

        try (UserDAO ownedDao = new UserDAO(
                "jdbc:h2:mem:user_owned_" + UUID.randomUUID()
                        + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE",
                "sa",
                ""
        )) {
            assertNotNull(ownedDao);
        }
    }
}
