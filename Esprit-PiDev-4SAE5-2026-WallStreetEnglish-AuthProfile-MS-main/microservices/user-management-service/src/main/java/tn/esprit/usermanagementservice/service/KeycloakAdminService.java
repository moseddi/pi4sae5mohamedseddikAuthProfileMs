package tn.esprit.usermanagementservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;

@Slf4j
@Service
@RequiredArgsConstructor
public class KeycloakAdminService {

    private final RestTemplate restTemplate;
    private static final String KEYCLOAK_URL = "http://localhost:6083";
    private static final String REALM = "myapp2";

    public void logoutUserSessions(String email) {
        try {
            String token = getAdminToken();
            if (token == null) {
                log.error("Cannot logout: Failed to get admin token");
                return;
            }

            String userId = getUserId(email, token);
            if (userId == null) {
                log.error("Cannot logout: User not found in Keycloak");
                return;
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);
            HttpEntity<?> request = new HttpEntity<>(headers);

            String url = KEYCLOAK_URL + "/admin/realms/" + REALM + "/users/" + userId + "/logout";
            restTemplate.exchange(url, HttpMethod.POST, request, String.class);

            log.info("✅ Logged out user: {}", email);
        } catch (Exception e) {
            log.error("❌ Logout failed: {}", e.getMessage());
        }
    }

    public void updateUserRole(String email, String newRole) {
        try {
            log.info("========== UPDATE USER ROLE ==========");
            log.info("Email: {}", email);
            log.info("New Role: {}", newRole);

            String token = getAdminToken();
            if (token == null) {
                log.error("Failed to get admin token - cannot update role");
                return;
            }

            String userId = getUserId(email, token);
            if (userId == null) {
                log.error("User not found in Keycloak: {}", email);
                return;
            }

            log.info("User ID: {}", userId);

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);
            headers.setContentType(MediaType.APPLICATION_JSON);

            // Get all realm roles
            String realmRolesUrl = KEYCLOAK_URL + "/admin/realms/" + REALM + "/roles";
            log.info("Getting roles from: {}", realmRolesUrl);

            HttpEntity<?> getRolesRequest = new HttpEntity<>(headers);
            ResponseEntity<KeycloakRole[]> rolesResponse = restTemplate.exchange(
                    realmRolesUrl,
                    HttpMethod.GET,
                    getRolesRequest,
                    KeycloakRole[].class
            );

            if (rolesResponse.getBody() == null) {
                log.error("No roles found in Keycloak");
                return;
            }

            log.info("Found {} roles in Keycloak", rolesResponse.getBody().length);

            // Find the role we want to assign
            KeycloakRole targetRole = Arrays.stream(rolesResponse.getBody())
                    .filter(role -> role.getName().equals(newRole))
                    .findFirst()
                    .orElse(null);

            if (targetRole == null) {
                log.error("Role {} not found in Keycloak", newRole);
                return;
            }

            log.info("Found target role: {} with ID: {}", targetRole.getName(), targetRole.getId());

            // Get user's current realm roles
            String userRolesUrl = KEYCLOAK_URL + "/admin/realms/" + REALM + "/users/" + userId + "/role-mappings/realm";
            log.info("Getting user roles from: {}", userRolesUrl);

            HttpEntity<?> getUserRolesRequest = new HttpEntity<>(headers);
            ResponseEntity<KeycloakRole[]> userRolesResponse = restTemplate.exchange(
                    userRolesUrl,
                    HttpMethod.GET,
                    getUserRolesRequest,
                    KeycloakRole[].class
            );

            if (userRolesResponse.getBody() != null && userRolesResponse.getBody().length > 0) {
                log.info("User currently has {} roles:", userRolesResponse.getBody().length);

                // Filter out default roles
                KeycloakRole[] rolesToRemove = Arrays.stream(userRolesResponse.getBody())
                        .filter(role -> !role.getName().equals("default-roles-myapp2")
                                && !role.getName().equals("offline_access")
                                && !role.getName().equals("uma_authorization"))
                        .toArray(KeycloakRole[]::new);

                if (rolesToRemove.length > 0) {
                    HttpEntity<KeycloakRole[]> deleteRequest = new HttpEntity<>(rolesToRemove, headers);
                    log.info("Removing {} custom roles...", rolesToRemove.length);
                    restTemplate.exchange(userRolesUrl, HttpMethod.DELETE, deleteRequest, String.class);
                    log.info("✅ Roles removed successfully");
                }
            }

            // Add the new role
            KeycloakRole[] rolesToAdd = new KeycloakRole[]{targetRole};
            HttpEntity<KeycloakRole[]> addRequest = new HttpEntity<>(rolesToAdd, headers);
            log.info("Adding role: {} ({})", targetRole.getName(), targetRole.getId());

            restTemplate.exchange(userRolesUrl, HttpMethod.POST, addRequest, String.class);
            log.info("✅ Role added successfully");

            log.info("✅ Successfully updated role to {} for user: {} in Keycloak", newRole, email);

            // Force logout to apply new role
            logoutUserSessions(email);

        } catch (Exception e) {
            log.error("❌ Failed to update role in Keycloak: {}", e.getMessage());
            e.printStackTrace();
        }
    }

    private String getAdminToken() {
        String url = KEYCLOAK_URL + "/realms/master/protocol/openid-connect/token";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        String body = "client_id=admin-cli&username=admin&password=admin&grant_type=password";
        HttpEntity<String> request = new HttpEntity<>(body, headers);

        try {
            log.info("Requesting Keycloak admin token from: {}", url);

            ResponseEntity<KeycloakTokenResponse> response = restTemplate.postForEntity(url, request, KeycloakTokenResponse.class);

            if (response.getBody() != null && response.getBody().getAccessToken() != null) {
                String token = response.getBody().getAccessToken();
                log.info("✅ Successfully got Keycloak admin token");
                return token;
            } else {
                log.error("❌ Response body or token is null - Status Code: {}", response.getStatusCode());
                log.error("Response Body: {}", response.getBody());
                return null;
            }
        } catch (Exception e) {
            log.error("❌ Failed to get admin token: {}", e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    private String getUserId(String email, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        HttpEntity<?> request = new HttpEntity<>(headers);

        String url = KEYCLOAK_URL + "/admin/realms/" + REALM + "/users?email=" + email;
        log.info("Getting user ID from: {}", url);

        try {
            ResponseEntity<KeycloakUser[]> response = restTemplate.exchange(url, HttpMethod.GET, request, KeycloakUser[].class);

            if (response.getBody() != null && response.getBody().length > 0) {
                String userId = response.getBody()[0].getId();
                log.info("✅ Found user in Keycloak with ID: {}", userId);
                return userId;
            }
        } catch (Exception e) {
            log.error("❌ Failed to get user ID: {}", e.getMessage());
        }

        log.error("User not found in Keycloak with email: {}", email);
        return null;
    }

    // Helper classes
    private static class KeycloakTokenResponse {
        private String access_token;
        private String refresh_token;
        private int expires_in;
        private int refresh_expires_in;
        private String token_type;

        public String getAccessToken() { return access_token; }
        public void setAccess_token(String access_token) { this.access_token = access_token; }
    }

    private static class KeycloakUser {
        private String id;
        private String username;
        private String email;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
    }

    private static class KeycloakRole {
        private String id;
        private String name;
        private boolean composite;
        private boolean clientRole;
        private String containerId;

        public String getId() { return id; }
        public String getName() { return name; }
        public boolean isComposite() { return composite; }
        public boolean isClientRole() { return clientRole; }
        public String getContainerId() { return containerId; }
    }
}