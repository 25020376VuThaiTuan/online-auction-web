package org.example.server;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantLock;

public final class AuctionRealtimeBroker {
    private final CopyOnWriteArrayList<SseClient> clients = new CopyOnWriteArrayList<>();

    public SseClient register(OutputStream outputStream) {
        SseClient client = new SseClient(outputStream);
        clients.add(client);
        return client;
    }

    public void remove(SseClient client) {
        clients.remove(client);
        client.close();
    }

    public void publish(String eventName, Map<String, Object> payload) {
        String encodedPayload = ApiJson.stringify(payload);
        for (SseClient client : clients) {
            try {
                client.send(eventName, encodedPayload);
            } catch (IOException e) {
                remove(client);
            }
        }
    }

    public static final class SseClient implements Closeable {
        private final OutputStream outputStream;
        private final ReentrantLock writeLock = new ReentrantLock();
        private volatile boolean open = true;

        private SseClient(OutputStream outputStream) {
            this.outputStream = outputStream;
        }

        public boolean isOpen() {
            return open;
        }

        public void send(String eventName, String payload) throws IOException {
            if (!open) {
                throw new IOException("SSE client is already closed.");
            }

            writeLock.lock();
            try {
                outputStream.write(("event: " + eventName + "\n").getBytes(StandardCharsets.UTF_8));
                outputStream.write(("data: " + payload + "\n\n").getBytes(StandardCharsets.UTF_8));
                outputStream.flush();
            } finally {
                writeLock.unlock();
            }
        }

        public void heartbeat() throws IOException {
            if (!open) {
                throw new IOException("SSE client is already closed.");
            }

            writeLock.lock();
            try {
                outputStream.write(": keepalive\n\n".getBytes(StandardCharsets.UTF_8));
                outputStream.flush();
            } finally {
                writeLock.unlock();
            }
        }

        @Override
        public void close() {
            if (!open) {
                return;
            }
            open = false;
            try {
                outputStream.close();
            } catch (IOException ignored) {
            }
        }
    }
}
