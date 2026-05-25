package org.example.dao;

import org.example.model.Seller;
import org.example.model.WalletLinkedAccount;
import org.example.model.WalletTransaction;
import org.example.util.CredentialHasher;
import org.junit.jupiter.api.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class WalletDAOTest {

    private static Connection connection;
    private WalletDAO walletDAO;

    private static final String USER_ID = "user-001";

    @BeforeAll
    static void beforeAll() throws Exception {

        connection = DriverManager.getConnection(
                "jdbc:h2:mem:testdb;MODE=MySQL;DB_CLOSE_DELAY=-1",
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
}