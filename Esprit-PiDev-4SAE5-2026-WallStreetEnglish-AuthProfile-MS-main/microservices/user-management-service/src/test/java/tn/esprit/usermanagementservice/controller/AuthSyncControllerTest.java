package tn.esprit.usermanagementservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tn.esprit.usermanagementservice.entity.Role;
import tn.esprit.usermanagementservice.service.UserProfileService;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthSyncController.class)
class AuthSyncControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockitoBean UserProfileService userProfileService;

    // ══════════════════════════════════════════════════════════════════════
    //  POST /api/users/from-auth — createProfileFromAuth
    // ══════════════════════════════════════════════════════════════════════
    @Nested
    class CreateProfileFromAuthTests {

        @Test
        void createProfile_student_returns200() throws Exception {
            Map<String, String> body = new HashMap<>();
            body.put("email", "student@test.com");
            body.put("role", "STUDENT");

            doNothing().when(userProfileService).createMinimalProfile("student@test.com", Role.STUDENT);

            mockMvc.perform(post("/api/users/from-auth")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isOk())
                    .andExpect(content().string("Profile created successfully"));

            verify(userProfileService).createMinimalProfile("student@test.com", Role.STUDENT);
        }

        @Test
        void createProfile_tutor_returns200() throws Exception {
            Map<String, String> body = new HashMap<>();
            body.put("email", "tutor@test.com");
            body.put("role", "TUTOR");

            doNothing().when(userProfileService).createMinimalProfile("tutor@test.com", Role.TUTOR);

            mockMvc.perform(post("/api/users/from-auth")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isOk())
                    .andExpect(content().string("Profile created successfully"));

            verify(userProfileService).createMinimalProfile("tutor@test.com", Role.TUTOR);
        }

        @Test
        void createProfile_admin_returns200() throws Exception {
            Map<String, String> body = new HashMap<>();
            body.put("email", "admin@test.com");
            body.put("role", "ADMIN");

            doNothing().when(userProfileService).createMinimalProfile("admin@test.com", Role.ADMIN);

            mockMvc.perform(post("/api/users/from-auth")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isOk())
                    .andExpect(content().string("Profile created successfully"));

            verify(userProfileService).createMinimalProfile("admin@test.com", Role.ADMIN);
        }

        @Test
        void createProfile_invalidRole_throwsException() {
            Map<String, String> body = new HashMap<>();
            body.put("email", "user@test.com");
            body.put("role", "INVALID_ROLE");

            // Role.valueOf("INVALID_ROLE") throws IllegalArgumentException inside the controller
            org.junit.jupiter.api.Assertions.assertThrows(
                    jakarta.servlet.ServletException.class,
                    () -> mockMvc.perform(post("/api/users/from-auth")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
            );
        }

        @Test
        void createProfile_serviceThrows_propagatesException() {
            Map<String, String> body = new HashMap<>();
            body.put("email", "student@test.com");
            body.put("role", "STUDENT");

            doThrow(new RuntimeException("DB error"))
                    .when(userProfileService).createMinimalProfile("student@test.com", Role.STUDENT);

            org.junit.jupiter.api.Assertions.assertThrows(
                    jakarta.servlet.ServletException.class,
                    () -> mockMvc.perform(post("/api/users/from-auth")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
            );
        }
    }
}