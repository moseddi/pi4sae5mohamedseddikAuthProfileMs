package tn.esprit.authservice;

import tn.esprit.authservice.dto.RegisterRequest;
import tn.esprit.authservice.entity.Role;
import tn.esprit.authservice.entity.User;

import java.util.Map;

public class TestDataFactory {

    public static RegisterRequest createValidRegisterRequest() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("student@test.com");
        request.setPassword("password123");
        request.setConfirmPassword("password123");
        request.setRole(Role.STUDENT);
        request.setFirstName("John");
        request.setLastName("Doe");
        return request;
    }

    public static User createTestUser() {
        return User.builder()
                .id(1L)
                .email("student@test.com")
                .role(Role.STUDENT)
                .keycloakId("keycloak-123")
                .active(true)
                .emailVerified(true)
                .build();
    }

    public static Map<String, Object> createValidTokenResponse() {
        return Map.of(
                "access_token", "mock-access-token-123",
                "refresh_token", "mock-refresh-token-456"
        );
    }

    public static Map<String, Object> createBlockedUserResponse() {
        return Map.of("blocked", true);
    }
}