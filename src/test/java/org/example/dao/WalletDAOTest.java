package org.example.dao;

import org.example.model.Seller;
import org.example.model.WalletLinkedAccount;
import org.example.model.WalletTransaction;
import org.example.util.CredentialHasher;
import org.junit.jupiter.api.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class WalletDAOTest {

    private static Connection connection;
    private WalletDAO walletDAO;

    private static final String USER_ID = "user-001";

    @BeforeAll
    static void beforeAll() throws Exception {

        connection = DriverManager.getConnection(
                "jdbc:h2:mem:wallet_dao_" + UUID.randomUUID()
                        + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
                "sa",
                ""
        );

        connection.createStatement().execute("""
                CREATE TABLE users (
                    id VARCHAR(36) PRIMARY KEY
                )
                """);

        connection.createStatement().execute("""
                CREATE TABLE bidder_profiles (
                    user_id VARCHAR(36) PRIMARY KEY,
                    wallet_balance DECIMAL(15,2),
                    updated_at TIMESTAMP
                )
                """);

        connection.createStatement().execute("""
                INSERT INTO users(id)
                VALUES ('user-001')
                """);
    }

    @BeforeEach
    void setUp() throws Exception {
        walletDAO = new WalletDAO(connection);
        walletDAO.ensureSchema();
    }

    @AfterEach
    void tearDown() throws Exception {

        connection.createStatement().execute("DELETE FROM wallet_holds");
        connection.createStatement().execute("DELETE FROM wallet_transactions");
        connection.createStatement().execute("DELETE FROM wallet_linked_accounts");
        connection.createStatement().execute("DELETE FROM wallet_accounts");
    }

    @Test
    void ensureWallet_shouldCreateWallet() throws Exception {

        Seller user = new Seller(
                USER_ID,
                "seller1",
                "123456",
                "seller@gmail.com"
        );

        walletDAO.ensureWallet(user, 500.0);

        Optional<Double> balance = walletDAO.findBalance(USER_ID);

        assertTrue(balance.isPresent());
        assertEquals(500.0, balance.get());
    }

    @Test
    void ensureSchema_shouldAddMissingRecoveryColumnsToExistingWalletAccounts() throws Exception {
        try (Connection legacyConnection = DriverManager.getConnection(
                "jdbc:h2:mem:wallet_dao_legacy_" + UUID.randomUUID()
                        + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
                "sa",
                ""
        )) {
            legacyConnection.createStatement().execute("""
                    CREATE TABLE users (
                        id VARCHAR(36) PRIMARY KEY
                    )
                    """);
            legacyConnection.createStatement().execute("""
                    CREATE TABLE bidder_profiles (
                        user_id VARCHAR(36) PRIMARY KEY,
                        wallet_balance DECIMAL(15,2),
                        updated_at TIMESTAMP
                    )
                    """);
            legacyConnection.createStatement().execute("""
                    CREATE TABLE wallet_accounts (
                        user_id VARCHAR(36) PRIMARY KEY,
                        balance DECIMAL(15, 2) NOT NULL DEFAULT 0.00,
                        pin_hash VARCHAR(255) NULL,
                        created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        CONSTRAINT fk_wallet_accounts_user
                            FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
                    )
                    """);

            new WalletDAO(legacyConnection).ensureSchema();

            try (ResultSet rs = legacyConnection.getMetaData().getColumns(
                    legacyConnection.getCatalog(),
                    null,
                    "wallet_accounts",
                    "pin_recovery_code"
            )) {
                assertTrue(rs.next());
                assertTrue(rs.getInt("COLUMN_SIZE") >= 255);
            }
            try (ResultSet rs = legacyConnection.getMetaData().getColumns(
                    legacyConnection.getCatalog(),
                    null,
                    "wallet_accounts",
                    "pin_recovery_expires_at"
            )) {
                assertTrue(rs.next());
            }
        }
    }

    @Test
    void updateBalance_shouldUpdateBalance() throws Exception {

        Seller user = new Seller(
                USER_ID,
                "seller1",
                "123456",
                "seller@gmail.com"
        );

        walletDAO.ensureWallet(user, 100.0);

        walletDAO.updateBalance(USER_ID, 800.0);

        Optional<Double> balance = walletDAO.findBalance(USER_ID);

        assertTrue(balance.isPresent());
        assertEquals(800.0, balance.get());
    }

    @Test
    void updatePinHash_shouldSavePinHash() throws Exception {

        Seller user = new Seller(
                USER_ID,
                "seller1",
                "123456",
                "seller@gmail.com"
        );

        walletDAO.ensureWallet(user, 0);

        String hash = CredentialHasher.hash("1234");

        walletDAO.updatePinHash(USER_ID, hash);

        Optional<String> result = walletDAO.findPinHash(USER_ID);

        assertTrue(result.isPresent());
        assertTrue(CredentialHasher.verify("1234", result.get()));
    }

    @Test
    void consumeRecoveryCode_shouldReturnTrueForValidCode() throws Exception {

        Seller user = new Seller(
                USER_ID,
                "seller1",
                "123456",
                "seller@gmail.com"
        );

        walletDAO.ensureWallet(user, 0);

        String recoveryCode = "ABC123";

        walletDAO.saveRecoveryCode(
                USER_ID,
                CredentialHasher.hash(recoveryCode),
                LocalDateTime.now().plusMinutes(10)
        );

        boolean consumed = walletDAO.consumeRecoveryCode(
                USER_ID,
                recoveryCode
        );

        assertTrue(consumed);
    }

    @Test
    void upsertHold_shouldInsertHold() throws Exception {

        walletDAO.upsertHold(
                USER_ID,
                "HOLD_1",
                "REF_1",
                250.0,
                "Auction hold"
        );

        Map<String, Double> holds = walletDAO.listHolds(USER_ID);

        assertEquals(1, holds.size());
        assertEquals(250.0, holds.get("HOLD_1"));
    }

    @Test
    void deleteHold_shouldDeleteHold() throws Exception {

        walletDAO.upsertHold(
                USER_ID,
                "HOLD_1",
                "REF_1",
                250.0,
                "Auction hold"
        );

        boolean deleted = walletDAO.deleteHold(
                USER_ID,
                "HOLD_1"
        );

        Map<String, Double> holds = walletDAO.listHolds(USER_ID);

        assertTrue(deleted);
        assertTrue(holds.isEmpty());
    }

    @Test
    void addTransaction_shouldInsertTransaction() throws Exception {

        WalletTransaction transaction = new WalletTransaction(
                "tx-001",
                USER_ID,
                "TOP_UP",
                100.0,
                0.0,
                100.0,
                null,
                "Top up wallet",
                LocalDateTime.now()
        );

        walletDAO.addTransaction(transaction);

        List<WalletTransaction> transactions =
                walletDAO.listTransactions(USER_ID);

        assertEquals(1, transactions.size());
        assertEquals("TOP_UP",
                transactions.get(0).transactionType());
    }

    @Test
    void addLinkedAccount_shouldInsertLinkedAccount() throws Exception {

        WalletLinkedAccount account = new WalletLinkedAccount(
                "acc-001",
                USER_ID,
                "Vietcombank",
                "VCB",
                "123456789",
                1000.0,
                true,
                LocalDateTime.now()
        );

        walletDAO.addLinkedAccount(account);

        List<WalletLinkedAccount> accounts =
                walletDAO.listLinkedAccounts(USER_ID);

        assertEquals(1, accounts.size());
        assertEquals("Vietcombank",
                accounts.get(0).accountName());
    }

    @Test
    void updateLinkedAccountBalance_shouldUpdateBalance() throws Exception {

        WalletLinkedAccount account = new WalletLinkedAccount(
                "acc-001",
                USER_ID,
                "Vietcombank",
                "VCB",
                "123456789",
                1000.0,
                true,
                LocalDateTime.now()
        );

        walletDAO.addLinkedAccount(account);

        walletDAO.updateLinkedAccountBalance(
                USER_ID,
                "acc-001",
                5000.0
        );

        Optional<Double> balance =
                walletDAO.findLinkedAccountBalanceForUpdate(
                        USER_ID,
                        "acc-001"
                );

        assertTrue(balance.isPresent());
        assertEquals(5000.0, balance.get());
    }

    @Test
    void deleteLinkedAccount_shouldDeleteAccount() throws Exception {

        WalletLinkedAccount account = new WalletLinkedAccount(
                "acc-001",
                USER_ID,
                "Vietcombank",
                "VCB",
                "123456789",
                1000.0,
                true,
                LocalDateTime.now()
        );

        walletDAO.addLinkedAccount(account);

        boolean deleted =
                walletDAO.deleteLinkedAccount(
                        USER_ID,
                        "acc-001"
                );

        List<WalletLinkedAccount> accounts =
                walletDAO.listLinkedAccounts(USER_ID);

        assertTrue(deleted);
        assertTrue(accounts.isEmpty());
    }

    @Test
    void missingRowsAndNegativeAmountsShouldUseSafeDefaults()
            throws Exception {

        assertTrue(walletDAO.findBalance("missing").isEmpty());
        assertTrue(walletDAO.findBalanceForUpdate("missing").isEmpty());
        assertTrue(walletDAO.findPinHash("missing").isEmpty());
        assertTrue(walletDAO.findLinkedAccountBalanceForUpdate(USER_ID, "missing").isEmpty());
        assertFalse(walletDAO.deleteHold(USER_ID, "missing"));
        assertFalse(walletDAO.deleteLinkedAccount(USER_ID, "missing"));

        Seller user = new Seller(
                USER_ID,
                "seller1",
                "123456",
                "seller@gmail.com"
        );
        walletDAO.ensureWallet(user, 50.0);

        walletDAO.updateBalance(USER_ID, -25.0);
        assertEquals(0.0, walletDAO.findBalance(USER_ID).orElseThrow());

        walletDAO.upsertHold(USER_ID, "HOLD_DELETE", "REF", 25.0, "Temporary hold");
        walletDAO.upsertHold(USER_ID, "HOLD_DELETE", "REF", 0.0, "Delete by zero amount");
        assertTrue(walletDAO.listHolds(USER_ID).isEmpty());
    }

    @Test
    void recoveryCodesShouldRejectMissingInvalidExpiredAndAcceptLegacyPlaintext()
            throws Exception {

        assertFalse(walletDAO.consumeRecoveryCode("missing", "CODE"));

        Seller user = new Seller(
                USER_ID,
                "seller1",
                "123456",
                "seller@gmail.com"
        );
        walletDAO.ensureWallet(user, 0.0);

        assertThrows(
                IllegalArgumentException.class,
                () -> walletDAO.saveRecoveryCode(USER_ID, "plain-code", LocalDateTime.now().plusMinutes(5))
        );
        assertFalse(walletDAO.consumeRecoveryCode(USER_ID, "CODE"));

        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    UPDATE wallet_accounts
                    SET pin_recovery_code = 'LEGACY',
                        pin_recovery_expires_at = DATEADD('MINUTE', 5, CURRENT_TIMESTAMP)
                    WHERE user_id = 'user-001'
                    """);
        }
        assertFalse(walletDAO.consumeRecoveryCode(USER_ID, " "));
        assertTrue(walletDAO.consumeRecoveryCode(USER_ID, "LEGACY"));
        assertFalse(walletDAO.consumeRecoveryCode(USER_ID, "LEGACY"));

        walletDAO.saveRecoveryCode(
                USER_ID,
                CredentialHasher.hash("EXPIRED"),
                LocalDateTime.now().minusMinutes(1)
        );
        assertFalse(walletDAO.consumeRecoveryCode(USER_ID, "EXPIRED"));
    }

    @Test
    void linkedAccountPrimarySelectionAndAllTransactionsShouldRoundTrip()
            throws Exception {

        WalletLinkedAccount first = new WalletLinkedAccount(
                "acc-primary",
                USER_ID,
                "Primary Bank",
                "BANK",
                "111122223333",
                100.0,
                true,
                LocalDateTime.now().minusMinutes(2)
        );
        WalletLinkedAccount second = new WalletLinkedAccount(
                "acc-secondary",
                USER_ID,
                "Second Bank",
                "BANK",
                "444455556666",
                200.0,
                false,
                LocalDateTime.now().minusMinutes(1)
        );

        walletDAO.addLinkedAccount(first);
        walletDAO.addLinkedAccount(second);
        walletDAO.setPrimaryLinkedAccount(USER_ID, "acc-secondary");

        List<WalletLinkedAccount> accounts = walletDAO.listLinkedAccounts(USER_ID);
        assertEquals("acc-secondary", accounts.get(0).id());
        assertTrue(accounts.get(0).primary());
        assertFalse(accounts.get(1).primary());

        WalletTransaction topUp = new WalletTransaction(
                "tx-all-1",
                USER_ID,
                "TOP_UP",
                75.0,
                0.0,
                75.0,
                " ",
                "Top up",
                LocalDateTime.now().minusSeconds(5)
        );
        WalletTransaction withdrawal = new WalletTransaction(
                "tx-all-2",
                USER_ID,
                "WITHDRAWAL",
                -25.0,
                75.0,
                50.0,
                "withdrawal-ref",
                "Withdrawal",
                LocalDateTime.now()
        );
        walletDAO.addTransaction(topUp);
        walletDAO.addTransaction(withdrawal);

        List<WalletTransaction> allTransactions = walletDAO.listAllTransactions();
        assertEquals(2, allTransactions.size());
        assertEquals("tx-all-2", allTransactions.get(0).id());
        assertEquals("withdrawal-ref", allTransactions.get(0).referenceId());
        assertNull(allTransactions.get(1).referenceId());
    }

    @Test
    void legacyTransactionSchemaShouldUseBidderColumns()
            throws Exception {

        try (Connection legacyConnection = DriverManager.getConnection(
                "jdbc:h2:mem:wallet_dao_legacy_tx_" + UUID.randomUUID()
                        + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE",
                "sa",
                ""
        )) {
            legacyConnection.createStatement().execute("""
                    CREATE TABLE wallet_transactions (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        bidder_id VARCHAR(36),
                        auction_id VARCHAR(36),
                        payment_id VARCHAR(36),
                        transaction_type VARCHAR(50),
                        amount DECIMAL(15, 2),
                        balance_before DECIMAL(15, 2),
                        balance_after DECIMAL(15, 2),
                        note VARCHAR(255),
                        created_at TIMESTAMP
                    )
                    """);
            WalletDAO legacyDao = new WalletDAO(legacyConnection);
            legacyDao.addTransaction(new WalletTransaction(
                    "ignored-legacy-id",
                    USER_ID,
                    "BID_RELEASE",
                    30.0,
                    20.0,
                    50.0,
                    "auction-1",
                    "Released legacy hold",
                    LocalDateTime.of(2026, 5, 27, 12, 0)
            ));

            List<WalletTransaction> transactions = legacyDao.listTransactions(USER_ID);
            assertEquals(1, transactions.size());
            assertEquals(USER_ID, transactions.get(0).userId());
            assertEquals("BID_RELEASE", transactions.get(0).transactionType());
            assertNull(transactions.get(0).referenceId());
            assertEquals(1, legacyDao.listAllTransactions().size());
        }
    }

    @Test
    void shouldCloseOwnedConnection()
            throws Exception {

        try (WalletDAO ownedDao = new WalletDAO(
                "jdbc:h2:mem:wallet_owned_" + UUID.randomUUID()
                        + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE",
                "sa",
                ""
        )) {
            assertNotNull(ownedDao);
        }
    }
}
