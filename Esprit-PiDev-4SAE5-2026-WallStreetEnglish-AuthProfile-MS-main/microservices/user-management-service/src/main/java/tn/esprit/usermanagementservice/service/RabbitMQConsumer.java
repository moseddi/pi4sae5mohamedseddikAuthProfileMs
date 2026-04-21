package tn.esprit.usermanagementservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import tn.esprit.usermanagementservice.dto.LoginEventMessage;
import tn.esprit.usermanagementservice.entity.LoginHistory;
import tn.esprit.usermanagementservice.repository.LoginHistoryRepository;
import tn.esprit.usermanagementservice.repository.ProfileChangeHistoryRepository;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class RabbitMQConsumer {

    private final SimpMessagingTemplate messagingTemplate;
    private final LoginHistoryRepository loginHistoryRepository;
    private final ProfileChangeHistoryRepository profileChangeHistoryRepository;

    @RabbitListener(queues = "login-events")
    public void receiveEvent(LoginEventMessage event) {
        log.info("📥 RabbitMQ RECEIVED: Type={}, Email={}", event.getType(), event.getEmail());

        if (event.getType() == LoginEventMessage.EventType.LOGIN) {
            handleLogin(event);
        } else if (event.getType() == LoginEventMessage.EventType.LOGOUT) {
            handleLogout(event);
        }
    }

    private void handleLogin(LoginEventMessage event) {
        LocalDateTime now = LocalDateTime.now();

        LoginHistory history = new LoginHistory();
        history.setEmail(event.getEmail());
        history.setRole(event.getRole());
        history.setType(LoginHistory.EventType.LOGIN);
        history.setLoginTime(event.getTimestamp() != null ? event.getTimestamp() : now);
        history.setActive(true);
        history.setSessionId(event.getSessionId());
        history.setIpAddress(event.getIpAddress() != null ? event.getIpAddress() : "127.0.0.1");
        history.setBrowser(event.getBrowser() != null ? event.getBrowser() : "Unknown");
        history.setOs(event.getOs() != null ? event.getOs() : "Unknown");
        history.setDeviceType(event.getDeviceType() != null ? event.getDeviceType() : "Desktop");

        // Check suspicious - country changes
        LocalDateTime startOfDay = now.toLocalDate().atStartOfDay();
        int countryChangesToday = profileChangeHistoryRepository.countCountryChangesToday(event.getEmail(), startOfDay);

        boolean suspicious = false;
        String suspiciousReason = null;

        if (countryChangesToday >= 5) {
            suspicious = true;
            suspiciousReason = "⚠️ " + countryChangesToday + " country changes today";
            log.warn("🚨 COUNTRY SPAM: {} changed country {} times today", event.getEmail(), countryChangesToday);
        }

        history.setSuspicious(suspicious);
        if (suspiciousReason != null) {
            history.setSuspiciousReason(suspiciousReason);
        }

        String timeStr = now.format(DateTimeFormatter.ofPattern("HH:mm:ss"));

        // ✅ FIX: Add suspicious indicator to the message
        String suspiciousIcon = suspicious ? "⚠️ " : "";
        history.setMessage(String.format("%s🔐 LOGIN: %s (%s) from %s at %s",
                suspiciousIcon, event.getEmail(), event.getRole(), history.getIpAddress(), timeStr));

        loginHistoryRepository.save(history);

        // Broadcast to frontend
        messagingTemplate.convertAndSend("/topic/logins", history.getMessage());
        log.info("📡 BROADCAST: {}", history.getMessage());
    }

    private void handleLogout(LoginEventMessage event) {
        LocalDateTime now = LocalDateTime.now();

        Optional<LoginHistory> openSession = loginHistoryRepository
                .findTopByEmailAndActiveOrderByLoginTimeDesc(event.getEmail(), true);

        if (openSession.isPresent()) {
            LoginHistory session = openSession.get();
            LocalDateTime logoutTime = event.getTimestamp() != null ? event.getTimestamp() : now;
            long durationSeconds = Duration.between(session.getLoginTime(), logoutTime).getSeconds();

            session.setActive(false);
            session.setLogoutTime(logoutTime);
            session.setLogoutType(convertLogoutType(event.getLogoutType()));
            session.setSessionDurationSeconds(durationSeconds);
            session.setType(LoginHistory.EventType.LOGOUT);

            String timeStr = now.format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            session.setMessage(String.format("🚪 LOGOUT: %s (%s) after %s from %s at %s",
                    event.getEmail(), event.getRole(), formatDuration(durationSeconds),
                    session.getIpAddress(), timeStr));

            loginHistoryRepository.save(session);
            log.info("💾 LOGOUT SAVED: {}", session.getMessage());
            messagingTemplate.convertAndSend("/topic/logins", session.getMessage());
        } else {
            log.warn("⚠️ NO ACTIVE SESSION for: {}", event.getEmail());
            LoginHistory orphan = new LoginHistory();
            orphan.setEmail(event.getEmail());
            orphan.setRole(event.getRole());
            orphan.setType(LoginHistory.EventType.LOGOUT);
            orphan.setLogoutTime(now);
            orphan.setActive(false);
            orphan.setLogoutType(convertLogoutType(event.getLogoutType()));
            orphan.setIpAddress(event.getIpAddress() != null ? event.getIpAddress() : "127.0.0.1");

            String timeStr = now.format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            orphan.setMessage(String.format("🚪 LOGOUT: %s (%s) at %s",
                    event.getEmail(), event.getRole(), timeStr));

            loginHistoryRepository.save(orphan);
            messagingTemplate.convertAndSend("/topic/logins", orphan.getMessage());
        }
    }

    private LoginHistory.LogoutType convertLogoutType(LoginEventMessage.LogoutType type) {
        if (type == null) return LoginHistory.LogoutType.VOLUNTARY;
        switch (type) {
            case VOLUNTARY: return LoginHistory.LogoutType.VOLUNTARY;
            case TIMEOUT: return LoginHistory.LogoutType.TIMEOUT;
            case FORCED: return LoginHistory.LogoutType.FORCED;
            default: return LoginHistory.LogoutType.VOLUNTARY;
        }
    }

    private String formatDuration(long seconds) {
        if (seconds < 60) return seconds + "s";
        if (seconds < 3600) return (seconds / 60) + "m " + (seconds % 60) + "s";
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        return hours + "h " + minutes + "m";
    }
}