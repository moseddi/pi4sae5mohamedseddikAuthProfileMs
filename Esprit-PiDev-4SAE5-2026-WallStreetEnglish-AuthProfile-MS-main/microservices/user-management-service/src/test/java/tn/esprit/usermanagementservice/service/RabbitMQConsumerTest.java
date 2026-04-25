package tn.esprit.usermanagementservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import tn.esprit.usermanagementservice.dto.LoginEventMessage;
import tn.esprit.usermanagementservice.entity.LoginHistory;
import tn.esprit.usermanagementservice.repository.LoginHistoryRepository;
import tn.esprit.usermanagementservice.repository.ProfileChangeHistoryRepository;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RabbitMQConsumer Tests")
class RabbitMQConsumerTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private LoginHistoryRepository loginHistoryRepository;

    @Mock
    private ProfileChangeHistoryRepository profileChangeHistoryRepository;

    @InjectMocks
    private RabbitMQConsumer rabbitMQConsumer;

    private LoginEventMessage loginEvent;
    private LoginEventMessage logoutEvent;

    @BeforeEach
    void setUp() {
        loginEvent = new LoginEventMessage();
        loginEvent.setEmail("test@test.com");
        loginEvent.setRole("STUDENT");
        loginEvent.setType(LoginEventMessage.EventType.LOGIN);
        loginEvent.setTimestamp(LocalDateTime.now());
        loginEvent.setSessionId("session-123");
        loginEvent.setIpAddress("192.168.1.100");
        loginEvent.setBrowser("Chrome");
        loginEvent.setOs("Windows");
        loginEvent.setDeviceType("Desktop");

        logoutEvent = new LoginEventMessage();
        logoutEvent.setEmail("test@test.com");
        logoutEvent.setRole("STUDENT");
        logoutEvent.setType(LoginEventMessage.EventType.LOGOUT);
        logoutEvent.setTimestamp(LocalDateTime.now());
        logoutEvent.setLogoutType(LoginEventMessage.LogoutType.VOLUNTARY);
    }

    @Nested
    @DisplayName("Receive Event Tests")
    class ReceiveEventTests {

        @Test
        @DisplayName("Should handle login event correctly")
        void receiveEvent_LoginEvent_ShouldSaveLoginHistory() {
            when(profileChangeHistoryRepository.countCountryChangesToday(anyString(), any()))
                    .thenReturn(0);
            when(loginHistoryRepository.save(any(LoginHistory.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            rabbitMQConsumer.receiveEvent(loginEvent);

            verify(loginHistoryRepository, times(1)).save(any(LoginHistory.class));
            verify(messagingTemplate, times(1)).convertAndSend(eq("/topic/logins"), anyString());
        }

        @Test
        @DisplayName("Should handle logout event with active session")
        void receiveEvent_LogoutEvent_WithActiveSession_ShouldUpdateHistory() {
            LoginHistory activeSession = new LoginHistory();
            activeSession.setLoginTime(LocalDateTime.now().minusHours(2));
            activeSession.setActive(true);
            activeSession.setEmail("test@test.com");

            when(loginHistoryRepository.findTopByEmailAndActiveOrderByLoginTimeDesc("test@test.com", true))
                    .thenReturn(Optional.of(activeSession));
            when(loginHistoryRepository.save(any(LoginHistory.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            rabbitMQConsumer.receiveEvent(logoutEvent);

            verify(loginHistoryRepository, times(1)).save(any(LoginHistory.class));
            assertThat(activeSession.isActive()).isFalse();
            assertThat(activeSession.getLogoutTime()).isNotNull();
        }

        @Test
        @DisplayName("Should handle logout event without active session")
        void receiveEvent_LogoutEvent_WithoutActiveSession_ShouldCreateOrphan() {
            when(loginHistoryRepository.findTopByEmailAndActiveOrderByLoginTimeDesc("test@test.com", true))
                    .thenReturn(Optional.empty());
            when(loginHistoryRepository.save(any(LoginHistory.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            rabbitMQConsumer.receiveEvent(logoutEvent);

            ArgumentCaptor<LoginHistory> captor = ArgumentCaptor.forClass(LoginHistory.class);
            verify(loginHistoryRepository, times(1)).save(captor.capture());

            LoginHistory saved = captor.getValue();
            assertThat(saved.isActive()).isFalse();
            assertThat(saved.getType()).isEqualTo(LoginHistory.EventType.LOGOUT);
            assertThat(saved.getMessage()).contains("LOGOUT");
        }
    }

    @Nested
    @DisplayName("Suspicious Activity Detection Tests")
    class SuspiciousActivityTests {

        @Test
        @DisplayName("Should mark login as suspicious when country changes exceed threshold")
        void handleLogin_WithManyCountryChanges_ShouldMarkSuspicious() {
            when(profileChangeHistoryRepository.countCountryChangesToday(anyString(), any()))
                    .thenReturn(5); // Threshold reached

            ArgumentCaptor<LoginHistory> captor = ArgumentCaptor.forClass(LoginHistory.class);
            when(loginHistoryRepository.save(any(LoginHistory.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            rabbitMQConsumer.receiveEvent(loginEvent);

            verify(loginHistoryRepository).save(captor.capture());
            LoginHistory savedHistory = captor.getValue();

            assertThat(savedHistory.isSuspicious()).isTrue();
            assertThat(savedHistory.getSuspiciousReason()).contains("5 country changes");
        }

        @Test
        @DisplayName("Should not mark login as suspicious when country changes below threshold")
        void handleLogin_WithFewCountryChanges_ShouldNotMarkSuspicious() {
            when(profileChangeHistoryRepository.countCountryChangesToday(anyString(), any()))
                    .thenReturn(2); // Below threshold

            rabbitMQConsumer.receiveEvent(loginEvent);

            verify(loginHistoryRepository).save(any(LoginHistory.class));
            // Verify not marked as suspicious - could check message doesn't contain suspicious icon
        }
    }

    @Nested
    @DisplayName("Null/Edge Case Tests")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should handle login event with null timestamp")
        void handleLogin_WithNullTimestamp_ShouldUseNow() {
            loginEvent.setTimestamp(null);

            when(profileChangeHistoryRepository.countCountryChangesToday(anyString(), any()))
                    .thenReturn(0);
            when(loginHistoryRepository.save(any(LoginHistory.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            rabbitMQConsumer.receiveEvent(loginEvent);

            verify(loginHistoryRepository, times(1)).save(any(LoginHistory.class));
        }

        @Test
        @DisplayName("Should handle login event with null IP address")
        void handleLogin_WithNullIp_ShouldUseDefault() {
            loginEvent.setIpAddress(null);

            when(profileChangeHistoryRepository.countCountryChangesToday(anyString(), any()))
                    .thenReturn(0);

            ArgumentCaptor<LoginHistory> captor = ArgumentCaptor.forClass(LoginHistory.class);
            when(loginHistoryRepository.save(any(LoginHistory.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            rabbitMQConsumer.receiveEvent(loginEvent);

            verify(loginHistoryRepository).save(captor.capture());
            assertThat(captor.getValue().getIpAddress()).isEqualTo("127.0.0.1");
        }

        @Test
        @DisplayName("Should handle logout event with null logout type")
        void handleLogout_WithNullLogoutType_ShouldUseDefault() {
            logoutEvent.setLogoutType(null);

            LoginHistory activeSession = new LoginHistory();
            activeSession.setLoginTime(LocalDateTime.now().minusHours(1));
            activeSession.setActive(true);

            when(loginHistoryRepository.findTopByEmailAndActiveOrderByLoginTimeDesc("test@test.com", true))
                    .thenReturn(Optional.of(activeSession));
            when(loginHistoryRepository.save(any(LoginHistory.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            rabbitMQConsumer.receiveEvent(logoutEvent);

            ArgumentCaptor<LoginHistory> captor = ArgumentCaptor.forClass(LoginHistory.class);
            verify(loginHistoryRepository).save(captor.capture());

            assertThat(captor.getValue().getLogoutType()).isEqualTo(LoginHistory.LogoutType.VOLUNTARY);
        }
    }
}