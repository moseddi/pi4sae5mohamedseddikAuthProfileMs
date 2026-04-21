package tn.esprit.usermanagementservice.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LoginEventMessage {
    private String email;
    private String role;
    private EventType type;
    private LogoutType logoutType;
    private LocalDateTime timestamp;
    private String sessionId;
    private String ipAddress;
    private String browser;
    private String os;
    private String deviceType;

    public enum EventType {
        LOGIN, LOGOUT
    }

    public enum LogoutType {
        VOLUNTARY, TIMEOUT, FORCED
    }
}