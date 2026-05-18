package org.example.server;

import com.sun.net.httpserver.HttpServer;
import org.example.service.AuthenticationService;
import org.example.service.AuctionWorkflowService;

import java.io.IOException;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

public final class AuctionApiServerMain {
    private static final String HOST = "0.0.0.0";
    private static final int DEFAULT_PORT = 8081;
    private static final int DEFAULT_PORT_FALLBACK_ATTEMPTS = 10;

    private AuctionApiServerMain() {
    }

    public static void main(String[] args) throws IOException {
        PortSelection portSelection = resolvePortSelection(args);
        HttpServer server = createServer(portSelection);
        int port = server.getAddress().getPort();

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

        if (!portSelection.explicit() && port != portSelection.port()) {
            System.out.println("Port " + portSelection.port() + " is already in use. Started on port " + port + " instead.");
        }
        System.out.println("Auction API server is listening on http://" + HOST + ":" + port + "/api");
        System.out.println("Use POST /api/auth/login to get a token, then call the auction endpoints.");
    }

    private static HttpServer createServer(PortSelection portSelection) throws IOException {
        if (portSelection.explicit()) {
            try {
                return HttpServer.create(new InetSocketAddress(HOST, portSelection.port()), 0);
            } catch (BindException exception) {
                throw new IOException(
                        "Port " + portSelection.port() + " is already in use. "
                                + "Stop the process using that port, or choose another one with "
                                + "AUCTION_API_PORT, -Dauction.api.port, or --port.",
                        exception
                );
            }
        }

        BindException lastBindException = null;
        for (int offset = 0; offset < DEFAULT_PORT_FALLBACK_ATTEMPTS; offset++) {
            int candidatePort = portSelection.port() + offset;
            try {
                return HttpServer.create(new InetSocketAddress(HOST, candidatePort), 0);
            } catch (BindException exception) {
                lastBindException = exception;
            }
        }

        throw new IOException(
                "Ports " + portSelection.port() + "-" + (portSelection.port() + DEFAULT_PORT_FALLBACK_ATTEMPTS - 1)
                        + " are already in use. Stop one of the running servers or choose a different port.",
                lastBindException
        );
    }

    static int resolvePort(String[] args) {
        return resolvePortSelection(args).port();
    }

    static PortSelection resolvePortSelection(String[] args) {
        String rawPort = resolvePortArgument(args);
        boolean explicit = rawPort != null && !rawPort.isBlank();
        if (rawPort == null || rawPort.isBlank()) {
            rawPort = System.getProperty("auction.api.port");
            explicit = rawPort != null && !rawPort.isBlank();
        }
        if (rawPort == null || rawPort.isBlank()) {
            rawPort = System.getenv("AUCTION_API_PORT");
            explicit = rawPort != null && !rawPort.isBlank();
        }
        if (rawPort == null || rawPort.isBlank()) {
            return new PortSelection(DEFAULT_PORT, false);
        }
        try {
            int port = Integer.parseInt(rawPort.trim());
            return new PortSelection(port > 0 ? port : DEFAULT_PORT, explicit);
        } catch (NumberFormatException ignored) {
            return new PortSelection(DEFAULT_PORT, false);
        }
    }

    private static String resolvePortArgument(String[] args) {
        if (args == null) {
            return null;
        }
        for (int index = 0; index < args.length; index++) {
            String arg = args[index];
            if (arg == null || arg.isBlank()) {
                continue;
            }
            if (arg.startsWith("--port=")) {
                return arg.substring("--port=".length());
            }
            if ("--port".equals(arg) && index + 1 < args.length) {
                return args[index + 1];
            }
        }
        return null;
    }

    record PortSelection(int port, boolean explicit) {
    }
}
