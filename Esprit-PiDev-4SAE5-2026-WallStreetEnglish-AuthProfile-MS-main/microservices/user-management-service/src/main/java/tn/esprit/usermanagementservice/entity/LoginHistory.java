package tn.esprit.usermanagementservice.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "login_history")
@Data
public class LoginHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ── Core identity ────────────────────────────────────────────
    private String email;
    private String role;           // STUDENT / TUTOR / ADMIN
    private String message;        // human-readable summary

    // ── Event type ───────────────────────────────────────────────
    @Enumerated(EnumType.STRING)
    private EventType type;        // LOGIN or LOGOUT

    // ── Timestamps ───────────────────────────────────────────────
    private LocalDateTime loginTime;
    private LocalDateTime logoutTime;  // null until logout is recorded

    // ── Session tracking ─────────────────────────────────────────
    private String sessionId;              // links login ↔ logout pair
    private boolean active;               // true = session still open
    private Long sessionDurationSeconds;  // calculated on logout

    // ── Logout details ───────────────────────────────────────────
    @Enumerated(EnumType.STRING)
    private LogoutType logoutType;  // VOLUNTARY, TIMEOUT, FORCED — null for login events

    // ── Device / location info ───────────────────────────────────
    private String ipAddress;   // "127.0.0.1" locally, real IP in production
    private String browser;
    private String os;
    private String deviceType;

    // ── Security flags ───────────────────────────────────────────
    private boolean suspicious;
    private String suspiciousReason;

    // ── Enums ────────────────────────────────────────────────────
    public enum EventType {
        LOGIN,
        LOGOUT
    }

    public enum LogoutType {
        VOLUNTARY,   // user clicked Sign out
        TIMEOUT,     // session expired
        FORCED       // admin revoked the session
    }
}