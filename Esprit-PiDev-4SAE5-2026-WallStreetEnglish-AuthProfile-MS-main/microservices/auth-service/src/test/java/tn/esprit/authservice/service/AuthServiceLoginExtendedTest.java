package tn.esprit.authservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;
import tn.esprit.authservice.client.UserServiceClient;
import tn.esprit.authservice.dto.AuthResponse;
import tn.esprit.authservice.dto.LoginRequest;
import tn.esprit.authservice.dto.RegisterRequest;
import tn.esprit.authservice.entity.Role;
import tn.esprit.authservice.entity.User;
import tn.esprit.authservice.repository.UserRepository;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService Login & Register Extended Tests")
class AuthServiceLoginExtendedTest {

    @Mock private UserRepository userRepository;
    @Mock private UserServiceClient userServiceClient;
    @Mock private RabbitTemplate rabbitTemplate;
    @Mock private HttpServletRequest httpServletRequest;
    @Mock private WebClient webClient;

    @InjectMocks
    private AuthService authService;

    private User activeStudent;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authService, "realm", "myapp2");
        ReflectionTestUtils.setField(authService, "clientId", "angular-app");
        ReflectionTestUtils.setField(authService, "serverUrl", "http://localhost:6083");

        activeStudent = User.builder()
                .id(1L)
                .email("student@test.com")
                .role(Role.STUDENT)
                .keycloakId("kc-123")
                .active(true)
                .emailVerified(true)
                .password(new BCryptPasswordEncoder().encode("password123"))
                .build();
    }

    @Nested
    @DisplayName("login - invalid credentials path")
    class LoginInvalidCredentialsTests {

        @Test
        @DisplayName("login throws RuntimeException when Keycloak WebClient is null/fails")
        void login_KeycloakUnavailable_ThrowsInvalidCredentials() {
            LoginRequest req = new LoginRequest();
            req.setEmail("student@test.com");
            req.setPassword("password123");

            // WebClient is a mock that returns null chain — triggers NPE caught as "Invalid credentials"
            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> authService.login(req));
            assertEquals("Invalid credentials", ex.getMessage());
        }
    }

    @Nested
    @DisplayName("register - duplicate email")
    class RegisterDuplicateTests {

        @Test
        @DisplayName("register throws when email already in DB")
        void register_DuplicateEmail_Throws() {
            when(userRepository.findByEmail("student@test.com"))
                    .thenReturn(Optional.of(activeStudent));

            RegisterRequest req = new RegisterRequest();
            req.setEmail("student@test.com");
            req.setPassword("password123");
            req.setConfirmPassword("password123");
            req.setRole(Role.STUDENT);
            req.setFirstName(null);
            req.setLastName(null);

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> authService.register(req));
            assertEquals("Email already exists", ex.getMessage());
            verify(userRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("logout - full path")
    class LogoutFullPathTests {

        @Test
        @DisplayName("Successful logout returns email and message")
        void logout_Success_ReturnsCorrectResponse() {
            when(userRepository.findByEmail("student@test.com"))
                    .thenReturn(Optional.of(activeStudent));
            when(httpServletRequest.getHeader("User-Agent")).thenReturn("Chrome/91");
            when(httpServletRequest.getRemoteAddr()).thenReturn("127.0.0.1");
            doNothing().when(rabbitTemplate).convertAndSend(anyString(), any(Object.class));

            AuthResponse resp = authService.logout("student@test.com", "VOLUNTARY");

            assertEquals("student@test.com", resp.getEmail());
            assertEquals("Logout successful", resp.getMessage());
            assertEquals(Role.STUDENT, resp.getRole());
        }

        @Test
        @DisplayName("Logout with null logoutType still succeeds")
        void logout_NullLogoutType_Succeeds() {
            when(userRepository.findByEmail("student@test.com"))
                    .thenReturn(Optional.of(activeStudent));
            when(httpServletRequest.getHeader("User-Agent")).thenReturn("Firefox");
            when(httpServletRequest.getRemoteAddr()).thenReturn("10.0.0.1");
            doNothing().when(rabbitTemplate).convertAndSend(anyString(), any(Object.class));

            assertDoesNotThrow(() -> authService.logout("student@test.com", null));
        }

        @Test
        @DisplayName("Logout with RabbitMQ error still returns success")
        void logout_RabbitMqError_StillReturnsSuccess() {
            when(userRepository.findByEmail("student@test.com"))
                    .thenReturn(Optional.of(activeStudent));
            when(httpServletRequest.getHeader("User-Agent")).thenReturn("Safari");
            when(httpServletRequest.getRemoteAddr()).thenReturn("192.168.0.1");
            doThrow(new RuntimeException("RabbitMQ unavailable"))
                    .when(rabbitTemplate).convertAndSend(anyString(), any(Object.class));

            AuthResponse resp = authService.logout("student@test.com", "VOLUNTARY");
            assertEquals("Logout successful", resp.getMessage());
        }

        @Test
        @DisplayName("Logout of nonexistent user throws exception")
        void logout_UserNotFound_Throws() {
            when(userRepository.findByEmail("ghost@test.com"))
                    .thenReturn(Optional.empty());

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> authService.logout("ghost@test.com", "VOLUNTARY"));
            assertTrue(ex.getMessage().contains("User not found"));
        }

        @Test
        @DisplayName("TIMEOUT logout type works")
        void logout_TimeoutType_Works() {
            when(userRepository.findByEmail("student@test.com"))
                    .thenReturn(Optional.of(activeStudent));
            when(httpServletRequest.getHeader("User-Agent")).thenReturn("Edge/18");
            when(httpServletRequest.getRemoteAddr()).thenReturn("10.1.1.1");
            doNothing().when(rabbitTemplate).convertAndSend(anyString(), any(Object.class));

            AuthResponse resp = authService.logout("student@test.com", "TIMEOUT");
            assertNotNull(resp);
        }

        @Test
        @DisplayName("FORCED logout type works")
        void logout_ForcedType_Works() {
            when(userRepository.findByEmail("student@test.com"))
                    .thenReturn(Optional.of(activeStudent));
            when(httpServletRequest.getHeader("User-Agent")).thenReturn("Opera/77");
            when(httpServletRequest.getRemoteAddr()).thenReturn("172.16.0.1");
            doNothing().when(rabbitTemplate).convertAndSend(anyString(), any(Object.class));

            AuthResponse resp = authService.logout("student@test.com", "FORCED");
            assertNotNull(resp);
        }
    }
}