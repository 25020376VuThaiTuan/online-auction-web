package org.example.dao;

import org.example.model.Bid;
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

class BidDAOTest {

    private Connection connection;

    private BidDAO dao;

    @BeforeEach
    void setup() throws Exception {

        Class.forName("org.h2.Driver");

        connection =
                DriverManager.getConnection(
                        "jdbc:h2:mem:bid_dao_" + UUID.randomUUID()
                                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE",
                        "sa",
                        ""
                );

        Statement st = connection.createStatement();

        st.execute("""
                CREATE TABLE auctions (
                    id VARCHAR(36) PRIMARY KEY,
                    current_price DECIMAL(19,2),
                    winner_bidder_id VARCHAR(36),
                    winning_bid_id VARCHAR(36),
                    updated_at TIMESTAMP
                )
                """);

        st.execute("""
                CREATE TABLE bids (
                    id VARCHAR(36) PRIMARY KEY,
                    auction_id VARCHAR(36),
                    bidder_id VARCHAR(36),
                    amount DECIMAL(19,2),
                    bid_source VARCHAR(50),
                    status VARCHAR(50),
                    placed_at TIMESTAMP
                )
                """);

        st.execute("""
                INSERT INTO auctions(id)
                VALUES ('auction-1')
                """);

        dao = new BidDAO(connection);
    }

    @AfterEach
    void cleanup() throws Exception {

        if (connection != null) {
            connection.close();
        }
    }

    @Test
    void shouldAddBid() throws Exception {

        Bid bid = new Bid(
                "bid-1",
                "bidder-1",
                "auction-1",
                5000,
                LocalDateTime.now()
        );

        dao.addBid(bid);

        List<Bid> bids =
                dao.getBidsForItem("auction-1");

        assertEquals(1, bids.size());
    }

    @Test
    void shouldReturnBidsForAuction() throws Exception {

        Bid bid1 = new Bid(
                "bid-1",
                "user-1",
                "auction-1",
                1000,
                LocalDateTime.now()
        );

        Bid bid2 = new Bid(
                "bid-2",
                "user-2",
                "auction-1",
                2000,
                LocalDateTime.now()
        );

        dao.addBid(bid1);
        dao.addBid(bid2);

        List<Bid> bids =
                dao.getBidsForItem("auction-1");

        assertEquals(2, bids.size());
    }

    @Test
    void shouldReturnEmptyList() throws Exception {

        List<Bid> bids =
                dao.getBidsForItem("unknown");

        assertTrue(bids.isEmpty());
    }
}
