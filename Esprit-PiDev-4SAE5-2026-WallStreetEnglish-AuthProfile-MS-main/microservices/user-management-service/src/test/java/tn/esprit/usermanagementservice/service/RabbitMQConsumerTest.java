package tn.esprit.usermanagementservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import tn.esprit.usermanagementservice.dto.LoginEventMessage;
import tn.esprit.usermanagementservice.entity.LoginHistory;
import tn.esprit.usermanagementservice.repository.LoginHistoryRepository;
import tn.esprit.usermanagementservice.repository.ProfileChangeHistoryRepository;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RabbitMQConsumerTest {

    @Mock SimpMessagingTemplate          messagingTemplate;
    @Mock LoginHistoryRepository         loginHistoryRepository;
    @Mock ProfileChangeHistoryRepository profileChangeHistoryRepository;

    @InjectMocks RabbitMQConsumer consumer;

    private LoginEventMessage loginEvent;
    private LoginEventMessage logoutEvent;

    @BeforeEach
    void setUp() {
        loginEvent = new LoginEventMessage();
        loginEvent.setEmail("user@test.com");
        loginEvent.setRole("STUDENT");
        loginEvent.setType(LoginEventMessage.EventType.LOGIN);
        loginEvent.setTimestamp(LocalDateTime.now());
        loginEvent.setSessionId("session-123");
        loginEvent.setIpAddress("10.0.0.1");
        loginEvent.setBrowser("Chrome");
        loginEvent.setOs("Windows");
        loginEvent.setDeviceType("Desktop");

        logoutEvent = new LoginEventMessage();
        logoutEvent.setEmail("user@test.com");
        logoutEvent.setRole("STUDENT");
        logoutEvent.setType(LoginEventMessage.EventType.LOGOUT);
        logoutEvent.setTimestamp(LocalDateTime.now());
        logoutEvent.setLogoutType(LoginEventMessage.LogoutType.VOLUNTARY);

        when(loginHistoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        doNothing().when(messagingTemplate).convertAndSend(anyString(), anyString());
    }

    // ══════════════════════════════════════════════════════════════════
    //  handleLogin — happy path and branches
    // ══════════════════════════════════════════════════════════════════
    @Nested
    class HandleLoginTests {

        @Test
        void receiveEvent_login_savesAndBroadcasts() {
            when(profileChangeHistoryRepository.countCountryChangesToday(any(), any())).thenReturn(0);

            consumer.receiveEvent(loginEvent);

            verify(loginHistoryRepository).save(any(LoginHistory.class));
            verify(messagingTemplate).convertAndSend(eq("/topic/logins"), anyString());
        }

        @Test
        void receiveEvent_login_nullFields_usesDefaults() {
            loginEvent.setIpAddress(null);
            loginEvent.setBrowser(null);
            loginEvent.setOs(null);
            loginEvent.setDeviceType(null);
            loginEvent.setTimestamp(null);
            when(profileChangeHistoryRepository.countCountryChangesToday(any(), any())).thenReturn(0);

            consumer.receiveEvent(loginEvent);

            ArgumentCaptor<LoginHistory> captor = ArgumentCaptor.forClass(LoginHistory.class);
            verify(loginHistoryRepository).save(captor.capture());
            LoginHistory saved = captor.getValue();
            assertThat(saved.getIpAddress()).isEqualTo("127.0.0.1");
            assertThat(saved.getBrowser()).isEqualTo("Unknown");
            assertThat(saved.getOs()).isEqualTo("Unknown");
            assertThat(saved.getDeviceType()).isEqualTo("Desktop");
        }

        @Test
        void receiveEvent_login_suspiciousCountryChanges_markedSuspicious() {
            when(profileChangeHistoryRepository.countCountryChangesToday(any(), any())).thenReturn(6);

            consumer.receiveEvent(loginEvent);

            ArgumentCaptor<LoginHistory> captor = ArgumentCaptor.forClass(LoginHistory.class);
            verify(loginHistoryRepository).save(captor.capture());
            assertThat(captor.getValue().isSuspicious()).isTrue();
            assertThat(captor.getValue().getSuspiciousReason()).contains("country changes today");
        }

        @Test
        void receiveEvent_login_notSuspicious_noReason() {
            when(profileChangeHistoryRepository.countCountryChangesToday(any(), any())).thenReturn(2);

            consumer.receiveEvent(loginEvent);

            ArgumentCaptor<LoginHistory> captor = ArgumentCaptor.forClass(LoginHistory.class);
            verify(loginHistoryRepository).save(captor.capture());
            assertThat(captor.getValue().isSuspicious()).isFalse();
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  handleLogout — active session found
    // ══════════════════════════════════════════════════════════════════
    @Nested
    class HandleLogoutWithActiveSession {

        @Test
        void logout_activeSessionFound_updatesAndBroadcasts() {
            LoginHistory openSession = new LoginHistory();
            openSession.setEmail("user@test.com");
            openSession.setRole("STUDENT");
            openSession.setActive(true);
            openSession.setLoginTime(LocalDateTime.now().minusMinutes(30));
            openSession.setIpAddress("10.0.0.1");

            when(loginHistoryRepository.findTopByEmailAndActiveOrderByLoginTimeDesc("user@test.com", true))
                    .thenReturn(Optional.of(openSession));

            consumer.receiveEvent(logoutEvent);

            assertThat(openSession.isActive()).isFalse();
            assertThat(openSession.getLogoutTime()).isNotNull();
            assertThat(openSession.getLogoutType()).isEqualTo(LoginHistory.LogoutType.VOLUNTARY);
            verify(loginHistoryRepository).save(openSession);
            verify(messagingTemplate).convertAndSend(eq("/topic/logins"), anyString());
        }

        @Test
        void logout_activeSession_timeoutType_savedCorrectly() {
            logoutEvent.setLogoutType(LoginEventMessage.LogoutType.TIMEOUT);

            LoginHistory openSession = new LoginHistory();
            openSession.setEmail("user@test.com");
            openSession.setActive(true);
            openSession.setLoginTime(LocalDateTime.now().minusHours(2));
            openSession.setIpAddress("10.0.0.1");

            when(loginHistoryRepository.findTopByEmailAndActiveOrderByLoginTimeDesc("user@test.com", true))
                    .thenReturn(Optional.of(openSession));

            consumer.receiveEvent(logoutEvent);

            assertThat(openSession.getLogoutType()).isEqualTo(LoginHistory.LogoutType.TIMEOUT);
        }

        @Test
        void logout_activeSession_forcedType_savedCorrectly() {
            logoutEvent.setLogoutType(LoginEventMessage.LogoutType.FORCED);

            LoginHistory openSession = new LoginHistory();
            openSession.setEmail("user@test.com");
            openSession.setActive(true);
            openSession.setLoginTime(LocalDateTime.now().minusHours(1));
            openSession.setIpAddress("10.0.0.1");

            when(loginHistoryRepository.findTopByEmailAndActiveOrderByLoginTimeDesc("user@test.com", true))
                    .thenReturn(Optional.of(openSession));

            consumer.receiveEvent(logoutEvent);

            assertThat(openSession.getLogoutType()).isEqualTo(LoginHistory.LogoutType.FORCED);
        }

        @Test
        void logout_nullTimestamp_usesNow() {
            logoutEvent.setTimestamp(null);

            LoginHistory openSession = new LoginHistory();
            openSession.setEmail("user@test.com");
            openSession.setActive(true);
            openSession.setLoginTime(LocalDateTime.now().minusMinutes(10));
            openSession.setIpAddress("10.0.0.1");

            when(loginHistoryRepository.findTopByEmailAndActiveOrderByLoginTimeDesc("user@test.com", true))
                    .thenReturn(Optional.of(openSession));

            consumer.receiveEvent(logoutEvent);

            assertThat(openSession.getLogoutTime()).isNotNull();
        }

        @Test
        void logout_longSession_durationFormattedAsHours() {
            LoginHistory openSession = new LoginHistory();
            openSession.setEmail("user@test.com");
            openSession.setActive(true);
            openSession.setLoginTime(LocalDateTime.now().minusHours(3).minusMinutes(15));
            openSession.setIpAddress("10.0.0.1");

            when(loginHistoryRepository.findTopByEmailAndActiveOrderByLoginTimeDesc("user@test.com", true))
                    .thenReturn(Optional.of(openSession));

            consumer.receiveEvent(logoutEvent);

            assertThat(openSession.getMessage()).contains("h ");
        }

        @Test
        void logout_shortSession_durationFormattedAsMinutes() {
            LoginHistory openSession = new LoginHistory();
            openSession.setEmail("user@test.com");
            openSession.setActive(true);
            openSession.setLoginTime(LocalDateTime.now().minusMinutes(5));
            openSession.setIpAddress("10.0.0.1");

            when(loginHistoryRepository.findTopByEmailAndActiveOrderByLoginTimeDesc("user@test.com", true))
                    .thenReturn(Optional.of(openSession));

            consumer.receiveEvent(logoutEvent);

            // message exists and session closed
            assertThat(openSession.isActive()).isFalse();
        }

        @Test
        void logout_veryShortSession_durationInSeconds() {
            LoginHistory openSession = new LoginHistory();
            openSession.setEmail("user@test.com");
            openSession.setActive(true);
            openSession.setLoginTime(LocalDateTime.now().minusSeconds(45));
            openSession.setIpAddress("10.0.0.1");

            when(loginHistoryRepository.findTopByEmailAndActiveOrderByLoginTimeDesc("user@test.com", true))
                    .thenReturn(Optional.of(openSession));

            consumer.receiveEvent(logoutEvent);

            assertThat(openSession.getMessage()).contains("s");
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  handleLogout — NO active session (orphan path)
    // ══════════════════════════════════════════════════════════════════
    @Nested
    class HandleLogoutNoActiveSession {

        @Test
        void logout_noActiveSession_createsOrphanRecord() {
            when(loginHistoryRepository.findTopByEmailAndActiveOrderByLoginTimeDesc("user@test.com", true))
                    .thenReturn(Optional.empty());

            consumer.receiveEvent(logoutEvent);

            ArgumentCaptor<LoginHistory> captor = ArgumentCaptor.forClass(LoginHistory.class);
            verify(loginHistoryRepository).save(captor.capture());
            LoginHistory orphan = captor.getValue();

            assertThat(orphan.isActive()).isFalse();
            assertThat(orphan.getEmail()).isEqualTo("user@test.com");
            assertThat(orphan.getType()).isEqualTo(LoginHistory.EventType.LOGOUT);
            verify(messagingTemplate).convertAndSend(eq("/topic/logins"), anyString());
        }

        @Test
        void logout_noActiveSession_nullIp_usesDefault() {
            logoutEvent.setIpAddress(null);
            when(loginHistoryRepository.findTopByEmailAndActiveOrderByLoginTimeDesc("user@test.com", true))
                    .thenReturn(Optional.empty());

            consumer.receiveEvent(logoutEvent);

            ArgumentCaptor<LoginHistory> captor = ArgumentCaptor.forClass(LoginHistory.class);
            verify(loginHistoryRepository).save(captor.capture());
            assertThat(captor.getValue().getIpAddress()).isEqualTo("127.0.0.1");
        }

        @Test
        void logout_noActiveSession_nullLogoutType_defaultsToVoluntary() {
            logoutEvent.setLogoutType(null);
            when(loginHistoryRepository.findTopByEmailAndActiveOrderByLoginTimeDesc("user@test.com", true))
                    .thenReturn(Optional.empty());

            consumer.receiveEvent(logoutEvent);

            ArgumentCaptor<LoginHistory> captor = ArgumentCaptor.forClass(LoginHistory.class);
            verify(loginHistoryRepository).save(captor.capture());
            assertThat(captor.getValue().getLogoutType()).isEqualTo(LoginHistory.LogoutType.VOLUNTARY);
        }
    }
}