package tn.esprit.authservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tn.esprit.authservice.client.UserServiceClient;
import tn.esprit.authservice.dto.AuthResponse;
import tn.esprit.authservice.dto.LoginRequest;
import tn.esprit.authservice.dto.RegisterRequest;
import tn.esprit.authservice.entity.Role;
import tn.esprit.authservice.entity.User;
import tn.esprit.authservice.repository.UserRepository;
import tn.esprit.authservice.service.AuthService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("AuthController Coverage Tests")
class AuthControllerCoverageTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private AuthService authService;
    @MockBean private UserRepository userRepository;
    @MockBean private UserServiceClient userServiceClient;
    @MockBean private JavaMailSender mailSender;
    @MockBean private Keycloak keycloakAdmin;

    // Keycloak mocks
    private RealmResource realmResource;
    private UsersResource usersResource;

    private User testUser;
    private AuthResponse testResponse;

    @BeforeEach
    void setUp() {
        realmResource = mock(RealmResource.class);
        usersResource = mock(UsersResource.class);

        when(keycloakAdmin.realm(anyString())).thenReturn(realmResource);
        when(realmResource.users()).thenReturn(usersResource);

        testUser = User.builder()
                .id(1L).email("student@test.com").role(Role.STUDENT)
                .active(true).emailVerified(true).password("$2a$10$hash").build();

        testResponse = AuthResponse.builder()
                .token("mock-token").email("student@test.com")
                .role(Role.STUDENT).userId(1L).message("Success").build();
    }

    // ─── /register ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/auth/register")
    class RegisterTests {

        @Test
        @DisplayName("Valid request returns 200 with AuthResponse")
        void register_ValidRequest_Returns200() throws Exception {
            RegisterRequest req = new RegisterRequest();
            req.setEmail("new@test.com");
            req.setPassword("password123");
            req.setConfirmPassword("password123");
            req.setRole(Role.STUDENT);

            when(authService.register(any(RegisterRequest.class))).thenReturn(testResponse);

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token").value("mock-token"));
        }

        @Test
        @DisplayName("Service throws RuntimeException returns 400")
        void register_ServiceThrows_Returns400() throws Exception {
            RegisterRequest req = new RegisterRequest();
            req.setEmail("existing@test.com");
            req.setPassword("password123");
            req.setConfirmPassword("password123");

            when(authService.register(any(RegisterRequest.class)))
                    .thenThrow(new RuntimeException("Email already exists"));

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest());
        }
    }

    // ─── /login ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/auth/login")
    class LoginTests {

        @Test
        @DisplayName("Valid login - userServiceClient succeeds")
        void login_ValidCredentials_RecordsLoginAndReturns200() throws Exception {
            LoginRequest req = new LoginRequest();
            req.setEmail("student@test.com");
            req.setPassword("password123");

            when(authService.login(any(LoginRequest.class))).thenReturn(testResponse);
            doNothing().when(userServiceClient).recordUserLogin(anyString());

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token").value("mock-token"))
                    .andExpect(jsonPath("$.email").value("student@test.com"));

            verify(userServiceClient).recordUserLogin("student@test.com");
        }

        @Test
        @DisplayName("Valid login - userServiceClient fails but login still succeeds")
        void login_UserServiceClientFails_StillReturns200() throws Exception {
            LoginRequest req = new LoginRequest();
            req.setEmail("student@test.com");
            req.setPassword("password123");

            when(authService.login(any(LoginRequest.class))).thenReturn(testResponse);
            doThrow(new RuntimeException("service down"))
                    .when(userServiceClient).recordUserLogin(anyString());

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token").value("mock-token"));
        }

        @Test
        @DisplayName("AuthService throws - returns 400")
        void login_ServiceThrows_Returns400() throws Exception {
            LoginRequest req = new LoginRequest();
            req.setEmail("student@test.com");
            req.setPassword("wrong");

            when(authService.login(any(LoginRequest.class)))
                    .thenThrow(new RuntimeException("Invalid credentials"));

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest());
        }
    }

    // ─── /test ─────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/auth/test")
    class TestEndpointTests {

        @Test
        @DisplayName("Returns 200 with working message")
        void test_Returns200() throws Exception {
            mockMvc.perform(get("/api/auth/test"))
                    .andExpect(status().isOk())
                    .andExpect(content().string("Auth service is working!"));
        }
    }

    // ─── /check-password ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/auth/check-password")
    class CheckPasswordTests {

        @Test
        @DisplayName("User found - returns password match info")
        void checkPassword_UserFound_ReturnsInfo() throws Exception {
            LoginRequest req = new LoginRequest();
            req.setEmail("student@test.com");
            req.setPassword("plaintext");

            when(userRepository.findByEmail("student@test.com"))
                    .thenReturn(Optional.of(testUser));

            mockMvc.perform(post("/api/auth/check-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("Password matches:")));
        }

        @Test
        @DisplayName("User not found - returns 400")
        void checkPassword_UserNotFound_Returns400() throws Exception {
            LoginRequest req = new LoginRequest();
            req.setEmail("ghost@test.com");
            req.setPassword("pass");

            when(userRepository.findByEmail("ghost@test.com")).thenReturn(Optional.empty());

            mockMvc.perform(post("/api/auth/check-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest());
        }
    }

    // ─── /forgot-password ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/auth/forgot-password")
    class ForgotPasswordTests {

        @Test
        @DisplayName("User found by email - sends email and returns success")
        void forgotPassword_UserFoundByEmail_SendsEmailAndReturns200() throws Exception {
            UserRepresentation user = new UserRepresentation();
            user.setId("kc-123");
            user.setEmail("student@test.com");
            user.setUsername("student@test.com");

            when(usersResource.list()).thenReturn(List.of(user));
            doNothing().when(mailSender).send(any(SimpleMailMessage.class));

            Map<String, String> body = Map.of("email", "student@test.com");

            mockMvc.perform(post("/api/auth/forgot-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("User found by username - sends email and returns success")
        void forgotPassword_UserFoundByUsername_SendsEmailAndReturns200() throws Exception {
            UserRepresentation user = new UserRepresentation();
            user.setId("kc-456");
            user.setEmail(null); // no email set
            user.setUsername("student@test.com");

            when(usersResource.list()).thenReturn(List.of(user));
            doNothing().when(mailSender).send(any(SimpleMailMessage.class));

            Map<String, String> body = Map.of("email", "student@test.com");

            mockMvc.perform(post("/api/auth/forgot-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("User NOT found - still returns success (security best practice)")
        void forgotPassword_UserNotFound_StillReturnsSuccess() throws Exception {
            UserRepresentation user = new UserRepresentation();
            user.setId("kc-789");
            user.setEmail("other@test.com");
            user.setUsername("other");

            when(usersResource.list()).thenReturn(List.of(user));

            Map<String, String> body = Map.of("email", "notfound@test.com");

            mockMvc.perform(post("/api/auth/forgot-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("Empty user list - still returns success")
        void forgotPassword_EmptyUserList_StillReturnsSuccess() throws Exception {
            when(usersResource.list()).thenReturn(List.of());

            Map<String, String> body = Map.of("email", "student@test.com");

            mockMvc.perform(post("/api/auth/forgot-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("Keycloak throws exception - catches and returns success")
        void forgotPassword_KeycloakThrows_ReturnsSuccessAnyway() throws Exception {
            when(keycloakAdmin.realm(anyString())).thenThrow(new RuntimeException("Keycloak down"));

            Map<String, String> body = Map.of("email", "student@test.com");

            mockMvc.perform(post("/api/auth/forgot-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("Mail send fails - still returns success")
        void forgotPassword_MailSendFails_StillReturnsSuccess() throws Exception {
            UserRepresentation user = new UserRepresentation();
            user.setId("kc-123");
            user.setEmail("student@test.com");
            user.setUsername("student@test.com");

            when(usersResource.list()).thenReturn(List.of(user));
            doThrow(new RuntimeException("SMTP down")).when(mailSender).send(any(SimpleMailMessage.class));

            Map<String, String> body = Map.of("email", "student@test.com");

            mockMvc.perform(post("/api/auth/forgot-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("Multiple users in list - finds correct one by email")
        void forgotPassword_MultipleUsers_FindsCorrectOne() throws Exception {
            UserRepresentation user1 = new UserRepresentation();
            user1.setId("kc-1"); user1.setEmail("other@test.com"); user1.setUsername("other");

            UserRepresentation user2 = new UserRepresentation();
            user2.setId("kc-2"); user2.setEmail("student@test.com"); user2.setUsername("student@test.com");

            UserRepresentation user3 = new UserRepresentation();
            user3.setId("kc-3"); user3.setEmail("admin@test.com"); user3.setUsername("admin");

            when(usersResource.list()).thenReturn(List.of(user1, user2, user3));
            doNothing().when(mailSender).send(any(SimpleMailMessage.class));

            Map<String, String> body = Map.of("email", "student@test.com");

            mockMvc.perform(post("/api/auth/forgot-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }
    }

    // ─── /reset-password ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/auth/reset-password")
    class ResetPasswordTests {

        @Test
        @DisplayName("Missing fields - returns 400")
        void resetPassword_MissingFields_Returns400() throws Exception {
            Map<String, String> body = new HashMap<>();
            body.put("token", null);
            body.put("newPassword", null);
            body.put("confirmPassword", null);

            mockMvc.perform(post("/api/auth/reset-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Missing required fields"));
        }

        @Test
        @DisplayName("Passwords do not match - returns 400")
        void resetPassword_PasswordsMismatch_Returns400() throws Exception {
            Map<String, String> body = Map.of(
                    "token", "some-token",
                    "newPassword", "password1",
                    "confirmPassword", "password2"
            );

            mockMvc.perform(post("/api/auth/reset-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Passwords do not match"));
        }

        @Test
        @DisplayName("Password too short - returns 400")
        void resetPassword_PasswordTooShort_Returns400() throws Exception {
            Map<String, String> body = Map.of(
                    "token", "some-token",
                    "newPassword", "abc",
                    "confirmPassword", "abc"
            );

            mockMvc.perform(post("/api/auth/reset-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Password must be at least 6 characters"));
        }

        @Test
        @DisplayName("Invalid token - returns 400")
        void resetPassword_InvalidToken_Returns400() throws Exception {
            Map<String, String> body = Map.of(
                    "token", "invalid-token-xyz",
                    "newPassword", "newPass123",
                    "confirmPassword", "newPass123"
            );

            mockMvc.perform(post("/api/auth/reset-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Invalid or expired token"));
        }
    }

    // ─── /validate-reset-token ─────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/auth/validate-reset-token")
    class ValidateResetTokenTests {

        @Test
        @DisplayName("Invalid token - returns 400 with valid=false")
        void validateResetToken_InvalidToken_Returns400() throws Exception {
            mockMvc.perform(get("/api/auth/validate-reset-token")
                            .param("token", "nonexistent-token"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.valid").value(false));
        }
    }

    // ─── /logout ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/auth/logout")
    class LogoutTests {

        @Test
        @DisplayName("Logout VOLUNTARY returns 200")
        void logout_Voluntary_Returns200() throws Exception {
            when(authService.logout("student@test.com", "VOLUNTARY"))
                    .thenReturn(AuthResponse.builder()
                            .email("student@test.com").message("Logout successful").build());

            mockMvc.perform(post("/api/auth/logout")
                            .param("email", "student@test.com")
                            .param("logoutType", "VOLUNTARY"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("Logout successful"));
        }

        @Test
        @DisplayName("Logout TIMEOUT returns 200")
        void logout_Timeout_Returns200() throws Exception {
            when(authService.logout("student@test.com", "TIMEOUT"))
                    .thenReturn(AuthResponse.builder()
                            .email("student@test.com").message("Logout successful").build());

            mockMvc.perform(post("/api/auth/logout")
                            .param("email", "student@test.com")
                            .param("logoutType", "TIMEOUT"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Logout FORCED returns 200")
        void logout_Forced_Returns200() throws Exception {
            when(authService.logout("admin@test.com", "FORCED"))
                    .thenReturn(AuthResponse.builder()
                            .email("admin@test.com").message("Logout successful").build());

            mockMvc.perform(post("/api/auth/logout")
                            .param("email", "admin@test.com")
                            .param("logoutType", "FORCED"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Logout service throws - returns 400")
        void logout_ServiceThrows_Returns400() throws Exception {
            when(authService.logout(anyString(), anyString()))
                    .thenThrow(new RuntimeException("Logout failed: User not found"));

            mockMvc.perform(post("/api/auth/logout")
                            .param("email", "ghost@test.com")
                            .param("logoutType", "VOLUNTARY"))
                    .andExpect(status().isBadRequest());
        }
    }
}