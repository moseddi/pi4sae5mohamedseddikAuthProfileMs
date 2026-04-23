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
import tn.esprit.authservice.client.UserServiceClient;
import tn.esprit.authservice.dto.AuthResponse;
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
@DisplayName("AuthService Unit Tests")
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserServiceClient userServiceClient;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private HttpServletRequest httpServletRequest;

    @InjectMocks
    private AuthService authService;

    private RegisterRequest validRegisterRequest;
    private User testUser;

    @BeforeEach
    void setUp() {
        validRegisterRequest = new RegisterRequest();
        validRegisterRequest.setEmail("student@test.com");
        validRegisterRequest.setPassword("password123");
        validRegisterRequest.setConfirmPassword("password123");
        validRegisterRequest.setRole(Role.STUDENT);
        validRegisterRequest.setFirstName("John");
        validRegisterRequest.setLastName("Doe");

        testUser = User.builder()
                .id(1L)
                .email("student@test.com")
                .role(Role.STUDENT)
                .keycloakId("keycloak-123")
                .active(true)
                .emailVerified(true)
                .build();
    }

    @Nested
    @DisplayName("Register Method Tests")
    class RegisterTests {

        @Test
        @DisplayName("Should fail when email already exists")
        void register_EmailAlreadyExists_ShouldThrowException() {
            when(userRepository.findByEmail(validRegisterRequest.getEmail()))
                    .thenReturn(Optional.of(testUser));

            RuntimeException exception = assertThrows(RuntimeException.class, () -> {
                authService.register(validRegisterRequest);
            });

            assertEquals("Email already exists", exception.getMessage());
            verify(userRepository, never()).save(any(User.class));
        }
    }

    @Nested
    @DisplayName("Logout Method Tests")
    class LogoutTests {

        @Test
        @DisplayName("Should successfully logout user")
        void logout_Success_ShouldReturnAuthResponse() {
            when(userRepository.findByEmail("student@test.com"))
                    .thenReturn(Optional.of(testUser));
            when(httpServletRequest.getHeader("User-Agent")).thenReturn("Chrome");
            when(httpServletRequest.getRemoteAddr()).thenReturn("127.0.0.1");
            doNothing().when(rabbitTemplate).convertAndSend(anyString(), any(Object.class));

            AuthResponse response = authService.logout("student@test.com", "VOLUNTARY");

            assertNotNull(response);
            assertEquals("student@test.com", response.getEmail());
            assertEquals("Logout successful", response.getMessage());
            verify(rabbitTemplate, times(1)).convertAndSend(anyString(), any(Object.class));
        }

        @Test
        @DisplayName("Should fail when user not found during logout")
        void logout_UserNotFound_ShouldThrowException() {
            when(userRepository.findByEmail("nonexistent@test.com"))
                    .thenReturn(Optional.empty());

            RuntimeException exception = assertThrows(RuntimeException.class, () -> {
                authService.logout("nonexistent@test.com", "VOLUNTARY");
            });

            assertEquals("Logout failed: User not found in database", exception.getMessage());
        }

        @Test
        @DisplayName("Should handle null logout type (default to VOLUNTARY)")
        void logout_NullLogoutType_ShouldUseVoluntary() {
            when(userRepository.findByEmail("student@test.com"))
                    .thenReturn(Optional.of(testUser));
            when(httpServletRequest.getHeader("User-Agent")).thenReturn("Firefox");
            when(httpServletRequest.getRemoteAddr()).thenReturn("192.168.1.1");
            doNothing().when(rabbitTemplate).convertAndSend(anyString(), any(Object.class));

            AuthResponse response = authService.logout("student@test.com", null);

            assertNotNull(response);
            verify(rabbitTemplate, times(1)).convertAndSend(anyString(), any(Object.class));
        }

        @Test
        @DisplayName("Should handle RabbitMQ exception during logout")
        void logout_RabbitMQException_ShouldStillReturnSuccess() {
            when(userRepository.findByEmail("student@test.com"))
                    .thenReturn(Optional.of(testUser));
            when(httpServletRequest.getHeader("User-Agent")).thenReturn("Safari");
            when(httpServletRequest.getRemoteAddr()).thenReturn("10.0.0.1");

            doThrow(new RuntimeException("RabbitMQ unavailable"))
                    .when(rabbitTemplate).convertAndSend(anyString(), any(Object.class));

            AuthResponse response = authService.logout("student@test.com", "VOLUNTARY");

            assertNotNull(response);
            assertEquals("Logout successful", response.getMessage());
        }
    }
}