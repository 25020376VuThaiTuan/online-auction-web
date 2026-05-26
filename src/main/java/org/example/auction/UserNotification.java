package org.example.auction;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class UserNotification {
    private static final DateTimeFormatter DISPLAY_DATE_TIME = DateTimeFormatter.ofPattern("dd/MM HH:mm");

    private final String userId;
    private final String type;
    private final String title;
    private final String body;
    private final LocalDateTime createdAt;
    private boolean read;

    public UserNotification(String userId, String type, String title, String body, LocalDateTime createdAt) {
        this.userId = userId;
        this.type = type == null || type.isBlank() ? "INFO" : type.trim();
        this.title = title == null ? "" : title.trim();
        this.body = body == null ? "" : body.trim();
        this.createdAt = createdAt == null ? LocalDateTime.now() : createdAt;
    }

    public String getUserId() {
        return userId;
    }

    public String getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public String getBody() {
        return body;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public boolean isRead() {
        return read;
    }

    public void markRead() {
        read = true;
    }

    public String getDisplayText() {
        return DISPLAY_DATE_TIME.format(createdAt) + " - " + title + ": " + body;
    }
}
