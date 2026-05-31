package org.example.service;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class AutomatedEmailService {
    private static final Logger LOGGER = Logger.getLogger(AutomatedEmailService.class.getName());
    private static final AutomatedEmailService INSTANCE = new AutomatedEmailService();
    private static final String SMTP_HOST_ENV = "AUCTION_EMAIL_SMTP_HOST";
    private static final String SMTP_PORT_ENV = "AUCTION_EMAIL_SMTP_PORT";
    private static final String SMTP_FROM_ENV = "AUCTION_EMAIL_FROM";
    private static final int DEFAULT_SMTP_PORT = 25;
    private static final int SOCKET_TIMEOUT_MILLIS = 5_000;

    private AutomatedEmailService() {
    }

    public static AutomatedEmailService getInstance() {
        return INSTANCE;
    }

    public void sendWalletPinRecovery(String email, String recoveryCode) {
        String subject = "Wallet PIN recovery code";
        String body = "Your wallet PIN recovery code is " + recoveryCode
                + ". It expires in 15 minutes. Requested at " + LocalDateTime.now() + ".";
        sendRecoveryEmail(email, subject, body, "wallet PIN recovery");
    }

    public void sendAccountPasswordRecovery(String email, String recoveryCode) {
        String subject = "Account password recovery code";
        String body = "Your account password recovery code is " + recoveryCode
                + ". It expires in 15 minutes. Requested at " + LocalDateTime.now() + ".";
        sendRecoveryEmail(email, subject, body, "account password recovery");
    }

    private void sendRecoveryEmail(String email, String subject, String body, String purpose) {
        if (!hasSmtpConfig()) {
            LOGGER.fine(() -> "Automated " + purpose + " email prepared for " + email + ".");
            return;
        }

        try {
            sendSmtp(email, subject, body);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Automated " + purpose + " email delivery failed.", e);
        }
    }

    private void sendSmtp(String recipient, String subject, String body) throws IOException {
        String host = System.getenv(SMTP_HOST_ENV).trim();
        int port = smtpPort();
        String from = System.getenv(SMTP_FROM_ENV).trim();

        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), SOCKET_TIMEOUT_MILLIS);
            socket.setSoTimeout(SOCKET_TIMEOUT_MILLIS);
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));

            readResponse(reader);
            command(writer, reader, "HELO localhost");
            command(writer, reader, "MAIL FROM:<" + from + ">");
            command(writer, reader, "RCPT TO:<" + recipient + ">");
            command(writer, reader, "DATA");
            writer.write("From: " + from + "\r\n");
            writer.write("To: " + recipient + "\r\n");
            writer.write("Subject: " + subject + "\r\n");
            writer.write("Content-Type: text/plain; charset=UTF-8\r\n");
            writer.write("\r\n");
            writer.write(body.replace("\r\n", "\n").replace("\n", "\r\n"));
            writer.write("\r\n.\r\n");
            writer.flush();
            readResponse(reader);
            command(writer, reader, "QUIT");
        }
    }

    private void command(BufferedWriter writer, BufferedReader reader, String command) throws IOException {
        writer.write(command);
        writer.write("\r\n");
        writer.flush();
        readResponse(reader);
    }

    private String readResponse(BufferedReader reader) throws IOException {
        String line = reader.readLine();
        if (line == null) {
            throw new IOException("SMTP server closed the connection.");
        }
        while (line.length() >= 4 && line.charAt(3) == '-') {
            line = reader.readLine();
            if (line == null) {
                throw new IOException("SMTP server closed the connection.");
            }
        }
        return line;
    }

    private boolean hasSmtpConfig() {
        return hasText(System.getenv(SMTP_HOST_ENV)) && hasText(System.getenv(SMTP_FROM_ENV));
    }

    private int smtpPort() {
        String configured = System.getenv(SMTP_PORT_ENV);
        if (!hasText(configured)) {
            return DEFAULT_SMTP_PORT;
        }
        try {
            return Integer.parseInt(configured.trim());
        } catch (NumberFormatException e) {
            return DEFAULT_SMTP_PORT;
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
