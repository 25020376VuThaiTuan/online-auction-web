package org.example.server;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpPrincipal;
import org.example.service.AuthenticationService;
import org.example.service.AuctionWorkflowService;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuctionApiHandlerCoverageTest {
    @Test
    void optionsRequestAppliesCorsHeadersAndClosesWithoutBody() throws Exception {
        String previousOrigin = System.getProperty("auction.api.allowedOrigin");
        try {
            System.setProperty("auction.api.allowedOrigin", "http://auction.example.test");
            FakeExchange exchange = exchange("OPTIONS", "/api/health", "");
            exchange.getRequestHeaders().set("Origin", "http://auction.example.test");

            handler().handle(exchange);

            assertEquals(204, exchange.statusCode);
            assertEquals(-1L, exchange.responseLength);
            assertEquals("http://auction.example.test",
                    exchange.getResponseHeaders().getFirst("Access-Control-Allow-Origin"));
            assertEquals("Origin", exchange.getResponseHeaders().getFirst("Vary"));
            assertTrue(exchange.closed);
        } finally {
            if (previousOrigin == null) {
                System.clearProperty("auction.api.allowedOrigin");
            } else {
                System.setProperty("auction.api.allowedOrigin", previousOrigin);
            }
        }
    }

    @Test
    void rootAndUnknownRoutesReturnJsonResponses() throws Exception {
        FakeExchange root = exchange("GET", "/api", "");

        handler().handle(root);

        Map<String, Object> rootPayload = ApiJson.parseObject(root.responseBody());
        assertEquals(200, root.statusCode);
        assertEquals("Online Auction API", rootPayload.get("name"));
        assertTrue(((List<?>) rootPayload.get("endpoints")).contains("/api/health"));

        FakeExchange unknown = exchange("GET", "/api/no%20such", "");
        handler().handle(unknown);

        Map<String, Object> errorPayload = ApiJson.parseObject(unknown.responseBody());
        assertEquals(404, unknown.statusCode);
        assertEquals("Unknown API route.", errorPayload.get("error"));
    }

    @Test
    void methodValidationMalformedInputAndLargeBodiesReturnHttpErrors() throws Exception {
        FakeExchange wrongMethod = exchange("POST", "/api/health", "{}");
        handler().handle(wrongMethod);
        assertEquals(405, wrongMethod.statusCode);
        assertEquals("Method not allowed.", ApiJson.parseObject(wrongMethod.responseBody()).get("error"));

        FakeExchange malformedLogin = exchange("POST", "/api/auth/login", "{\"username\":\"bidder\"}");
        handler().handle(malformedLogin);
        assertEquals(400, malformedLogin.statusCode);
        assertEquals("Field 'password' is required.", ApiJson.parseObject(malformedLogin.responseBody()).get("error"));

        FakeExchange largeBody = exchange("POST", "/api/auth/login", "x".repeat(64 * 1024 + 2));
        handler().handle(largeBody);
        assertEquals(413, largeBody.statusCode);
        assertEquals("Request body is too large.", ApiJson.parseObject(largeBody.responseBody()).get("error"));
    }

    @Test
    void authenticationErrorsAndRateLimitAreMappedToJsonResponses() throws Exception {
        AuctionApiHandler handler = handler();
        FakeExchange badCredentials = exchange("POST", "/api/auth/login", ApiJson.stringify(Map.of(
                "username", "missing-user",
                "password", "wrong-password"
        )));

        handler.handle(badCredentials);

        assertEquals(401, badCredentials.statusCode);
        assertEquals("Invalid username or password.", ApiJson.parseObject(badCredentials.responseBody()).get("error"));

        FakeExchange lastAttempt = null;
        for (int attempt = 0; attempt < 11; attempt++) {
            lastAttempt = exchange("POST", "/api/auth/login", ApiJson.stringify(Map.of(
                    "username", "rate-limited-user",
                    "password", "wrong-password"
            )));
            handler.handle(lastAttempt);
        }

        assertEquals(429, lastAttempt.statusCode);
        assertEquals("Too many login attempts. Try again later.",
                ApiJson.parseObject(lastAttempt.responseBody()).get("error"));
    }

    private AuctionApiHandler handler() {
        return new AuctionApiHandler(
                AuthenticationService.getInstance(),
                AuctionWorkflowService.getInstance(),
                new ApiSessionService(),
                new AuctionRealtimeBroker()
        );
    }

    private FakeExchange exchange(String method, String path, String body) {
        return new FakeExchange(method, URI.create(path), body);
    }

    private static final class FakeExchange extends HttpExchange {
        private final String requestMethod;
        private final URI requestUri;
        private final Headers requestHeaders = new Headers();
        private final Headers responseHeaders = new Headers();
        private final ByteArrayInputStream requestBody;
        private final ByteArrayOutputStream responseBody = new ByteArrayOutputStream();
        private int statusCode;
        private long responseLength;
        private boolean closed;

        private FakeExchange(String requestMethod, URI requestUri, String requestBody) {
            this.requestMethod = requestMethod;
            this.requestUri = requestUri;
            this.requestBody = new ByteArrayInputStream(requestBody.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public Headers getRequestHeaders() {
            return requestHeaders;
        }

        @Override
        public Headers getResponseHeaders() {
            return responseHeaders;
        }

        @Override
        public URI getRequestURI() {
            return requestUri;
        }

        @Override
        public String getRequestMethod() {
            return requestMethod;
        }

        @Override
        public HttpContext getHttpContext() {
            return null;
        }

        @Override
        public void close() {
            closed = true;
        }

        @Override
        public InputStream getRequestBody() {
            return requestBody;
        }

        @Override
        public OutputStream getResponseBody() {
            return responseBody;
        }

        @Override
        public void sendResponseHeaders(int responseCode, long responseLength) {
            this.statusCode = responseCode;
            this.responseLength = responseLength;
        }

        @Override
        public InetSocketAddress getRemoteAddress() {
            return new InetSocketAddress("127.0.0.1", 50123);
        }

        @Override
        public int getResponseCode() {
            return statusCode;
        }

        @Override
        public InetSocketAddress getLocalAddress() {
            return new InetSocketAddress("127.0.0.1", 8080);
        }

        @Override
        public String getProtocol() {
            return "HTTP/1.1";
        }

        @Override
        public Object getAttribute(String name) {
            return null;
        }

        @Override
        public void setAttribute(String name, Object value) {
        }

        @Override
        public void setStreams(InputStream inputStream, OutputStream outputStream) {
        }

        @Override
        public HttpPrincipal getPrincipal() {
            return null;
        }

        private String responseBody() {
            return responseBody.toString(StandardCharsets.UTF_8);
        }
    }
}
