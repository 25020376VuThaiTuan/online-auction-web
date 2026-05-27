package org.example.server;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuctionRealtimeBrokerCoverageTest {
    @Test
    void publishHeartbeatAndRemoveExerciseSseClientLifecycle() throws IOException {
        AuctionRealtimeBroker broker = new AuctionRealtimeBroker();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        AuctionRealtimeBroker.SseClient client = broker.register(output);

        broker.publish("auction-updated", Map.of("auctionId", 42));
        client.heartbeat();

        String stream = new String(output.toByteArray(), StandardCharsets.UTF_8);
        assertTrue(stream.contains("event: auction-updated\n"));
        assertTrue(stream.contains("data: {\"auctionId\":42}\n\n"));
        assertTrue(stream.contains(": keepalive\n\n"));
        assertTrue(client.isOpen());

        broker.remove(client);

        assertFalse(client.isOpen());
        assertThrows(IOException.class, () -> client.send("auction-updated", "{}"));
        assertThrows(IOException.class, client::heartbeat);
    }

    @Test
    void publishRemovesAndClosesClientWhenOutputFails() {
        AuctionRealtimeBroker broker = new AuctionRealtimeBroker();
        FailingOutputStream output = new FailingOutputStream();
        AuctionRealtimeBroker.SseClient client = broker.register(output);

        broker.publish("auction-updated", Map.of("auctionId", 7));

        assertFalse(client.isOpen());
        assertTrue(output.closed);

        broker.publish("auction-updated", Map.of("auctionId", 8));
    }

    private static final class FailingOutputStream extends OutputStream {
        private boolean closed;

        @Override
        public void write(int b) throws IOException {
            throw new IOException("boom");
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            throw new IOException("boom");
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
