package org.example.server;

import com.sun.net.httpserver.HttpServer;
import org.example.service.AuthenticationService;
import org.example.service.AuctionWorkflowService;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

public final class AuctionApiServerMain {
    private AuctionApiServerMain() {
    }

    public static void main(String[] args) throws IOException {
        int port = resolvePort();
        HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);

        AuctionApiHandler apiHandler = new AuctionApiHandler(
                AuthenticationService.getInstance(),
                AuctionWorkflowService.getInstance(),
                new ApiSessionService(),
                new AuctionRealtimeBroker()
        );

        server.createContext("/api", apiHandler);
        server.setExecutor(Executors.newCachedThreadPool());
        Runtime.getRuntime().addShutdownHook(new Thread(() -> server.stop(1)));
        server.start();

        System.out.println("Auction API server is listening on http://0.0.0.0:" + port + "/api");
        System.out.println("Use POST /api/auth/login to get a token, then call the auction endpoints.");
    }

    private static int resolvePort() {
        String rawPort = System.getenv("AUCTION_API_PORT");
        if (rawPort == null || rawPort.isBlank()) {
            return 8080;
        }
        try {
            int port = Integer.parseInt(rawPort.trim());
            return port > 0 ? port : 8080;
        } catch (NumberFormatException ignored) {
            return 8080;
        }
    }
}
