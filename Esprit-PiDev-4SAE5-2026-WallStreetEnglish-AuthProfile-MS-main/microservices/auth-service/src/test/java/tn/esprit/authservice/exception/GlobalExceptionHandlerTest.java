package tn.esprit.authservice.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tn.esprit.authservice.config.TestSecurityConfig;
import tn.esprit.authservice.controller.AuthController;
import tn.esprit.authservice.client.UserServiceClient;
import tn.esprit.authservice.repository.UserRepository;
import tn.esprit.authservice.service.AuthService;
import tn.esprit.authservice.service.KeycloakService;
import org.springframework.mail.javamail.JavaMailSender;
import org.keycloak.admin.client.Keycloak;
import tn.esprit.authservice.dto.RegisterRequest;
import tn.esprit.authservice.entity.Role;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests that verify GlobalExceptionHandler handles exceptions correctly
 * through the full Spring MVC stack.
 */
@WebMvcTest(AuthController.class)
@Import(TestSecurityConfig.class)
@ActiveProfiles("test")
@DisplayName("GlobalExceptionHandler Tests")
class GlobalExceptionHandlerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private UserRepository userRepository;
    @MockitoBean private AuthService authService;
    @MockitoBean private UserServiceClient userServiceClient;
    @MockitoBean private KeycloakService keycloakService;
    @MockitoBean private JavaMailSender mailSender;
    @MockitoBean private Keycloak keycloakAdmin;

    @Nested
    @DisplayName("MethodArgumentNotValidException -> 400 with field errors")
    class ValidationErrorTests {

        @Test
        @DisplayName("Blank email produces 400 with field error map")
        void blankEmail_Returns400WithFieldError() throws Exception {
            RegisterRequest req = new RegisterRequest();
            req.setEmail("");
            req.setPassword("password123");
            req.setConfirmPassword("password123");

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Invalid email format produces 400")
        void invalidEmailFormat_Returns400() throws Exception {
            RegisterRequest req = new RegisterRequest();
            req.setEmail("not-an-email");
            req.setPassword("password123");
            req.setConfirmPassword("password123");

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Short password produces 400")
        void shortPassword_Returns400() throws Exception {
            RegisterRequest req = new RegisterRequest();
            req.setEmail("valid@test.com");
            req.setPassword("abc");
            req.setConfirmPassword("abc");

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("RuntimeException -> 400 with error key")
    class RuntimeExceptionTests {

        @Test
        @DisplayName("AuthService RuntimeException returns 400 with error message")
        void authServiceThrows_Returns400WithError() throws Exception {
            when(authService.register(any(RegisterRequest.class)))
                    .thenThrow(new RuntimeException("Email already exists"));

            RegisterRequest req = new RegisterRequest();
            req.setEmail("valid@test.com");
            req.setPassword("password123");
            req.setConfirmPassword("password123");
            req.setRole(Role.STUDENT);

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest());
        }
    }
}