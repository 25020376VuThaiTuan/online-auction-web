package org.example.dao;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AuthSessionDAOTest {

    private Connection connection;

    private AuthSessionDAO dao;

    @BeforeEach
    void setup() throws Exception {

        Class.forName("org.h2.Driver");

        connection =
                DriverManager.getConnection(
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
                INSERT INTO users(id)
                VALUES ('user-1')
                """);

        dao =
                new AuthSessionDAO(connection);

        dao.ensureSchema();
    }

    @AfterEach
    void cleanup() throws Exception {

        if (connection != null) {

            connection.close();
        }
    }

    @Test
    void shouldCreateSession() throws Exception {

        String sessionId =
                UUID.randomUUID().toString();

        dao.createSession(
                sessionId,
                "user-1",
                "token_hash",
                Instant.now(),
                Instant.now().plusSeconds(3600)
        );

        Optional<AuthSessionDAO.PersistedSession>
                result =
                dao.findActiveSessionByTokenHash(
                        "token_hash"
                );

        assertTrue(result.isPresent());
    }
}