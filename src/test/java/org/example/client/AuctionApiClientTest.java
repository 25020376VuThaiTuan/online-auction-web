package org.example.client;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuctionApiClientTest {
    @Test
    void connectivityFailureIsTrueForIoExceptions() {
        AuctionApiClient.ApiClientException failure =
                new AuctionApiClient.ApiClientException("Could not reach server", new IOException("Connection refused"));

        assertTrue(AuctionApiClient.isConnectivityFailure(failure));
    }

    @Test
    void connectivityFailureIsTrueForNestedIoExceptions() {
        AuctionApiClient.ApiClientException failure = new AuctionApiClient.ApiClientException(
                "Could not reach server",
                new IllegalStateException("wrapper", new IOException("Connection refused"))
        );

        assertTrue(AuctionApiClient.isConnectivityFailure(failure));
    }

    @Test
    void connectivityFailureIsFalseForApplicationErrors() {
        AuctionApiClient.ApiClientException failure =
                new AuctionApiClient.ApiClientException("Password does not match.");

        assertFalse(AuctionApiClient.isConnectivityFailure(failure));
    }
}
