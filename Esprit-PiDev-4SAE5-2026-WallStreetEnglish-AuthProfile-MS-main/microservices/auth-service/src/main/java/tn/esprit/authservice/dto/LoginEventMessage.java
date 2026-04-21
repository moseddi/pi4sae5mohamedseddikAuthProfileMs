package tn.esprit.authservice.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Structured message published to RabbitMQ for both login and logout events.
 */
@Data
public class LoginEventMessage {

    // ── Who ──────────────────────────────────────────────────────
    private String email;
    private String role;

    // ── What ─────────────────────────────────────────────────────
    private EventType type;
    private LogoutType logoutType; // only filled for LOGOUT events

    // ── When ─────────────────────────────────────────────────────
    private LocalDateTime timestamp;

    // ── Session linking ──────────────────────────────────────────
    private String sessionId;

    // ── Device / location ────────────────────────────────────────
    private String ipAddress;
    private String browser;
    private String os;
    private String deviceType;

    public enum EventType {
        LOGIN,
        LOGOUT
    }

    public enum LogoutType {
        VOLUNTARY,
        TIMEOUT,
        FORCED
    }
}