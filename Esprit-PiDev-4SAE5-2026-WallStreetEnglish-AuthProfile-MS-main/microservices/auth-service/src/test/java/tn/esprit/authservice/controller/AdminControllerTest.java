package tn.esprit.authservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
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
import tn.esprit.authservice.dto.AuthResponse;
import tn.esprit.authservice.dto.RegisterRequest;
import tn.esprit.authservice.dto.RoleUpdateRequest;
import tn.esprit.authservice.entity.Role;
import tn.esprit.authservice.entity.User;
import tn.esprit.authservice.repository.UserRepository;
import tn.esprit.authservice.service.AuthService;
import tn.esprit.authservice.service.KeycloakService;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminController.class)
@Import(TestSecurityConfig.class)
@ActiveProfiles("test")
@DisplayName("AdminController Tests")
class AdminControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private AuthService authService;
    @MockitoBean private UserRepository userRepository;
    @MockitoBean private KeycloakService keycloakService;

    private RegisterRequest registerRequest;
    private RoleUpdateRequest roleUpdateRequest;
    private AuthResponse authResponse;
    private User testUser;

    @BeforeEach
    void setUp() {
        registerRequest = new RegisterRequest();
        registerRequest.setEmail("newuser@test.com");
        registerRequest.setPassword("password123");
        registerRequest.setConfirmPassword("password123");
        registerRequest.setRole(Role.STUDENT);
        registerRequest.setFirstName("Jane");
        registerRequest.setLastName("Doe");

        authResponse = AuthResponse.builder()
                .token("mock-token")
                .email("newuser@test.com")
                .role(Role.STUDENT)
                .userId(2L)
                .message("Registration successful")
                .build();

        testUser = User.builder()
                .id(2L)
                .email("newuser@test.com")
                .role(Role.STUDENT)
                .keycloakId("kc-abc")
                .active(true)
                .emailVerified(true)
                .build();

        roleUpdateRequest = new RoleUpdateRequest();
        roleUpdateRequest.setEmail("newuser@test.com");
        roleUpdateRequest.setRole(Role.TUTOR);
    }

    @Nested
    @DisplayName("POST /api/auth/admin/create")
    class CreateUserTests {

        @Test
        @DisplayName("Should create user successfully and return 200 with AuthResponse")
        void createUser_ValidRequest_Returns200WithSuccessMap() throws Exception {
            // Controller calls registerByAdmin(), not register()
            when(authService.registerByAdmin(any(RegisterRequest.class))).thenReturn(authResponse);

            mockMvc.perform(post("/api/auth/admin/create")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(registerRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token").value("mock-token"))
                    .andExpect(jsonPath("$.email").value("newuser@test.com"))
                    .andExpect(jsonPath("$.userId").value(2));
        }

        @Test
        @DisplayName("Should return 400 when authService throws exception")
        void createUser_ServiceThrows_Returns400WithErrorMap() throws Exception {
            // Controller calls registerByAdmin(), not register()
            when(authService.registerByAdmin(any(RegisterRequest.class)))
                    .thenThrow(new RuntimeException("Email already exists"));

            mockMvc.perform(post("/api/auth/admin/create")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(registerRequest)))
                    .andExpect(status().isBadRequest())
                    // Global exception handler wraps the message in {"error": "..."}
                    .andExpect(jsonPath("$.error").value("Email already exists"));
        }

        @Test
        @DisplayName("Should return 400 when email is blank")
        void createUser_BlankEmail_Returns400() throws Exception {
            registerRequest.setEmail("");

            mockMvc.perform(post("/api/auth/admin/create")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(registerRequest)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should return 400 when password is too short")
        void createUser_ShortPassword_Returns400() throws Exception {
            registerRequest.setPassword("abc");

            mockMvc.perform(post("/api/auth/admin/create")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(registerRequest)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("GET /api/auth/admin/test")
    class TestEndpointTests {

        @Test
        @DisplayName("Should return 200 and admin confirmation message")
        void testAdminAccess_Returns200() throws Exception {
            mockMvc.perform(get("/api/auth/admin/test"))
                    .andExpect(status().isOk())
                    .andExpect(content().string("You are an admin! Access granted."));
        }
    }

    @Nested
    @DisplayName("PUT /api/auth/admin/role")
    class UpdateRoleTests {

        @Test
        @DisplayName("Should update role successfully and return 200 with result map")
        void updateRole_ValidRequest_Returns200WithSuccessMap() throws Exception {
            testUser.setRole(Role.STUDENT);
            when(userRepository.findByEmail("newuser@test.com"))
                    .thenReturn(Optional.of(testUser));
            doNothing().when(keycloakService).updateUserRole(anyString(), anyString());

            mockMvc.perform(put("/api/auth/admin/role")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(roleUpdateRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Role updated successfully"))
                    .andExpect(jsonPath("$.email").value("newuser@test.com"))
                    .andExpect(jsonPath("$.newRole").value("TUTOR"));
        }

        @Test
        @DisplayName("Should return 400 when user not found")
        void updateRole_UserNotFound_Returns400() throws Exception {
            when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());

            mockMvc.perform(put("/api/auth/admin/role")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(roleUpdateRequest)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("Should still return 200 even when Keycloak update fails")
        void updateRole_KeycloakFails_StillReturns200() throws Exception {
            when(userRepository.findByEmail("newuser@test.com"))
                    .thenReturn(Optional.of(testUser));
            doThrow(new RuntimeException("Keycloak unreachable"))
                    .when(keycloakService).updateUserRole(anyString(), anyString());

            mockMvc.perform(put("/api/auth/admin/role")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(roleUpdateRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("Should save updated role to repository")
        void updateRole_ValidRequest_SavesUserToRepository() throws Exception {
            when(userRepository.findByEmail("newuser@test.com"))
                    .thenReturn(Optional.of(testUser));
            doNothing().when(keycloakService).updateUserRole(anyString(), anyString());

            mockMvc.perform(put("/api/auth/admin/role")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(roleUpdateRequest)))
                    .andExpect(status().isOk());

            verify(userRepository, times(1)).save(any(User.class));
            verify(keycloakService, times(1)).updateUserRole("newuser@test.com", "TUTOR");
        }
    }
}