package tn.esprit.authservice.service;

import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;
import tn.esprit.authservice.client.UserServiceClient;
import tn.esprit.authservice.dto.AuthResponse;
import tn.esprit.authservice.dto.LoginRequest;
import tn.esprit.authservice.dto.RegisterRequest;
import tn.esprit.authservice.entity.Role;
import tn.esprit.authservice.entity.User;
import tn.esprit.authservice.repository.UserRepository;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final UserServiceClient userServiceClient;
    private final Keycloak keycloakAdmin;
    private final WebClient webClient;

    @Value("${keycloak.realm}")
    private String realm;

    @Value("${keycloak.client-id}")
    private String clientId;

    @Value("${keycloak.server-url}")
    private String serverUrl;

    public AuthResponse register(RegisterRequest request) {
        log.info("========== REGISTRATION STARTED ==========");
        log.info("Email: {}", request.getEmail());
        log.info("Requested Role: {}", request.getRole());
        log.info("First Name: {}", request.getFirstName());
        log.info("Last Name: {}", request.getLastName());

        // Check if user already exists
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            log.error("❌ Email already exists in database: {}", request.getEmail());
            throw new RuntimeException("Email already exists");
        }

        try {
            log.info("Step 1: Creating user in Keycloak: {}", request.getEmail());

            // ===== 1. Create user in Keycloak =====
            UserRepresentation keycloakUser = new UserRepresentation();
            keycloakUser.setUsername(request.getEmail());
            keycloakUser.setEmail(request.getEmail());
            keycloakUser.setEnabled(true);
            keycloakUser.setEmailVerified(true);

            if (request.getFirstName() != null) {
                keycloakUser.setFirstName(request.getFirstName());
            }
            if (request.getLastName() != null) {
                keycloakUser.setLastName(request.getLastName());
            }

            CredentialRepresentation credential = new CredentialRepresentation();
            credential.setType(CredentialRepresentation.PASSWORD);
            credential.setValue(request.getPassword());
            credential.setTemporary(false);
            keycloakUser.setCredentials(Collections.singletonList(credential));

            log.info("Calling Keycloak to create user with realm: {}", realm);
            Response response = keycloakAdmin.realm(realm).users().create(keycloakUser);
            log.info("Keycloak response status: {}", response.getStatus());

            if (response.getStatus() != 201 && response.getStatus() != 200) {
                String errorMsg = "Failed to create user in Keycloak. Status: " + response.getStatus();
                log.error("❌ {}", errorMsg);
                throw new RuntimeException(errorMsg);
            }

            String keycloakId = response.getLocation().getPath().replaceAll(".*/([^/]+)$", "$1");
            log.info("✅ User created in Keycloak with ID: {}", keycloakId);

            // ===== 2. Determine role =====
            Role userRole = request.getRole() != null ? request.getRole() : Role.STUDENT;
            log.info("Step 2: Role determined: {}", userRole);

            // ===== 3. Assign REALM role in Keycloak =====
            log.info("Step 3: Attempting to assign role '{}' in Keycloak", userRole);
            try {
                log.info("Fetching role '{}' from Keycloak realm '{}'", userRole.name(), realm);
                RoleRepresentation role = keycloakAdmin.realm(realm).roles()
                        .get(userRole.name()).toRepresentation();

                log.info("✅ Role found in Keycloak: {}", role.getName());
                log.info("Assigning role to user with ID: {}", keycloakId);

                keycloakAdmin.realm(realm).users().get(keycloakId)
                        .roles().realmLevel().add(Collections.singletonList(role));

                log.info("✅ Successfully assigned realm role {} to user", userRole);
            } catch (Exception e) {
                log.error("❌ FAILED TO ASSIGN ROLE: {}", e.getMessage());
                log.error("Exception type: {}", e.getClass().getName());
                log.error("Stack trace:", e);

                // Try to list available roles to see what exists
                try {
                    log.info("Listing all available roles in realm '{}':", realm);
                    List<RoleRepresentation> allRoles = keycloakAdmin.realm(realm).roles().list();
                    for (RoleRepresentation r : allRoles) {
                        log.info("Available role: {} (ID: {})", r.getName(), r.getId());
                    }
                } catch (Exception listError) {
                    log.error("Could not list roles: {}", listError.getMessage());
                }

                log.error("Make sure role '{}' exists in Keycloak realm '{}'", userRole.name(), realm);
                throw new RuntimeException("Failed to assign role in Keycloak: " + e.getMessage());
            }

            // ===== 4. Save in local database =====
            log.info("Step 4: Saving user in local database");
            User localUser = User.builder()
                    .email(request.getEmail())
                    .password("")  // Password not stored locally
                    .role(userRole)
                    .keycloakId(keycloakId)
                    .active(true)
                    .emailVerified(true)
                    .build();

            User savedUser = userRepository.save(localUser);
            log.info("✅ User saved in local DB with ID: {}", savedUser.getId());

            // ===== 5. Get token from Keycloak =====
            log.info("Step 5: Getting token from Keycloak for user: {}", request.getEmail());
            Map<String, Object> tokenResponse = getTokenFromKeycloak(
                    request.getEmail(),
                    request.getPassword()
            );

            String token = null;
            String refreshToken = null;

            if (tokenResponse != null && tokenResponse.containsKey("access_token")) {
                token = (String) tokenResponse.get("access_token");
                refreshToken = (String) tokenResponse.get("refresh_token");
                log.info("✅ Token obtained successfully");
            } else {
                log.warn("⚠️ Could not obtain token, but user was created");
            }

            // ===== 6. Create profile in User Service =====
            log.info("Step 6: Creating profile in User Service");
            try {
                userServiceClient.createProfile(
                        localUser.getEmail(),
                        localUser.getRole().name(),
                        request.getFirstName(),
                        request.getLastName()
                );
                log.info("✅ Profile created in User Service");
            } catch (Exception e) {
                log.warn("⚠️ Profile creation failed: {}", e.getMessage());
                log.warn("This is not critical - profile can be created later");
            }

            log.info("========== REGISTRATION COMPLETED SUCCESSFULLY ==========");

            return AuthResponse.builder()
                    .token(token)
                    .refreshToken(refreshToken)
                    .email(localUser.getEmail())
                    .role(localUser.getRole())
                    .userId(localUser.getId())
                    .message("Registration successful")
                    .build();

        } catch (Exception e) {
            log.error("========== REGISTRATION FAILED ==========");
            log.error("Error: {}", e.getMessage());
            log.error("Stack trace:", e);
            throw new RuntimeException("Registration failed: " + e.getMessage());
        }
    }

    public AuthResponse login(LoginRequest request) {
        try {
            log.info("Logging in user: {}", request.getEmail());

            Map<String, Object> tokenResponse = getTokenFromKeycloak(
                    request.getEmail(),
                    request.getPassword()
            );

            if (tokenResponse == null || !tokenResponse.containsKey("access_token")) {
                throw new RuntimeException("Failed to get token from Keycloak");
            }

            User user = userRepository.findByEmail(request.getEmail())
                    .orElseThrow(() -> new RuntimeException("User not found in database"));

            log.info("User found in database with role: {}", user.getRole());

            try {
                userServiceClient.recordUserLogin(request.getEmail());
                log.info("Login recorded for: {}", request.getEmail());
            } catch (Exception e) {
                log.error("Failed to record login: {}", e.getMessage());
            }

            return AuthResponse.builder()
                    .token((String) tokenResponse.get("access_token"))
                    .refreshToken((String) tokenResponse.get("refresh_token"))
                    .email(user.getEmail())
                    .role(user.getRole())
                    .userId(user.getId())
                    .message("Login successful")
                    .build();

        } catch (Exception e) {
            log.error("Login failed: {}", e.getMessage());
            throw new RuntimeException("Invalid credentials");
        }
    }

    private Map<String, Object> getTokenFromKeycloak(String username, String password) {
        String tokenUrl = serverUrl + "/realms/" + realm + "/protocol/openid-connect/token";
        log.debug("Getting token from URL: {}", tokenUrl);

        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("client_id", clientId);
        formData.add("grant_type", "password");
        formData.add("username", username);
        formData.add("password", password);

        try {
            Map<String, Object> response = webClient.post()
                    .uri(tokenUrl)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .bodyValue(formData)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            log.debug("Token response received");
            return response;
        } catch (Exception e) {
            log.error("Error getting token from Keycloak: {}", e.getMessage());
            return null;
        }
    }
}