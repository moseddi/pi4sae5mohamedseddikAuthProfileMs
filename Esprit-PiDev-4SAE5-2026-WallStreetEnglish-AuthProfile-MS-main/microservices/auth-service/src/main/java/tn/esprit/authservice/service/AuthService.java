package tn.esprit.authservice.service;

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
import tn.esprit.authservice.config.RabbitMQConfig;
import tn.esprit.authservice.dto.AuthResponse;
import tn.esprit.authservice.dto.LoginEventMessage;
import tn.esprit.authservice.dto.LoginRequest;
import tn.esprit.authservice.dto.RegisterRequest;
import tn.esprit.authservice.entity.LogoutType;
import tn.esprit.authservice.entity.Role;
import tn.esprit.authservice.entity.User;
import tn.esprit.authservice.repository.UserRepository;
import tn.esprit.authservice.client.UserServiceClient;

import jakarta.ws.rs.core.Response;
import jakarta.servlet.http.HttpServletRequest;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final UserServiceClient userServiceClient;
    private final Keycloak keycloakAdmin;
    private final WebClient webClient;
    private final org.springframework.amqp.rabbit.core.RabbitTemplate rabbitTemplate;
    private final HttpServletRequest httpServletRequest;

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

        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            log.error("❌ Email already exists in database: {}", request.getEmail());
            throw new RuntimeException("Email already exists");
        }

        try {
            log.info("Step 1: Creating user in Keycloak: {}", request.getEmail());

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

            Role userRole = request.getRole() != null ? request.getRole() : Role.STUDENT;
            log.info("Step 2: Role determined: {}", userRole);

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
                throw new RuntimeException("Failed to assign role in Keycloak: " + e.getMessage());
            }

            log.info("Step 4: Saving user in local database");
            User localUser = User.builder()
                    .email(request.getEmail())
                    .password("")
                    .role(userRole)
                    .keycloakId(keycloakId)
                    .active(true)
                    .emailVerified(true)
                    .build();

            User savedUser = userRepository.save(localUser);
            log.info("✅ User saved in local DB with ID: {}", savedUser.getId());

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
            throw new RuntimeException("Registration failed: " + e.getMessage());
        }
    }

    // ✅ ADD THIS METHOD
    private boolean isUserBlocked(String email) {
        try {
            String url = "http://localhost:8082/api/users/check-blocked/" + email;
            Map<String, Object> response = webClient.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
            return (Boolean) response.get("blocked");
        } catch (Exception e) {
            log.error("Failed to check if user is blocked: {}", e.getMessage());
            return false;
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

            // ✅ CHECK IF USER IS BLOCKED
            if (isUserBlocked(request.getEmail())) {
                throw new RuntimeException("Your account is blocked. Please check your email for reactivation instructions.");
            }

            log.info("User found in database with role: {}", user.getRole());

            try {
                userServiceClient.recordUserLogin(request.getEmail());
                log.info("Login recorded for: {}", request.getEmail());
            } catch (Exception e) {
                log.error("Failed to record login in User Service: {}", e.getMessage());
            }

            try {
                LoginEventMessage event = buildLoginEventMessage(user);
                rabbitTemplate.convertAndSend(RabbitMQConfig.LOGIN_QUEUE, event);
                log.info("✅ Sent LoginEventMessage to RabbitMQ: {} for {}", event.getType(), event.getEmail());
            } catch (Exception e) {
                log.error("❌ RabbitMQ error: {}", e.getMessage());
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

    public AuthResponse logout(String email, String logoutType) {
        try {
            log.info("Logging out user: {}", email);

            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("User not found in database"));

            LogoutType type = logoutType != null ? LogoutType.valueOf(logoutType) : LogoutType.VOLUNTARY;

            try {
                LoginEventMessage event = buildLogoutEventMessage(user, type);
                rabbitTemplate.convertAndSend(RabbitMQConfig.LOGIN_QUEUE, event);
                log.info("✅ Sent LogoutEventMessage to RabbitMQ: {} for {}", event.getType(), event.getEmail());
            } catch (Exception e) {
                log.error("❌ RabbitMQ error during logout: {}", e.getMessage());
            }

            return AuthResponse.builder()
                    .email(user.getEmail())
                    .role(user.getRole())
                    .message("Logout successful")
                    .build();

        } catch (Exception e) {
            log.error("Logout failed: {}", e.getMessage());
            throw new RuntimeException("Logout failed: " + e.getMessage());
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

    private LoginEventMessage buildLoginEventMessage(User user) {
        LoginEventMessage event = new LoginEventMessage();
        event.setEmail(user.getEmail());
        event.setRole(user.getRole().name());
        event.setType(LoginEventMessage.EventType.LOGIN);
        event.setTimestamp(LocalDateTime.now());
        event.setSessionId(UUID.randomUUID().toString());

        String userAgent = httpServletRequest.getHeader("User-Agent");
        String ipAddress = getClientIpAddress();

        event.setIpAddress(ipAddress);

        if (userAgent != null) {
            event.setBrowser(parseBrowser(userAgent));
            event.setOs(parseOperatingSystem(userAgent));
            event.setDeviceType(parseDeviceType(userAgent));
        } else {
            event.setBrowser("Unknown");
            event.setOs("Unknown");
            event.setDeviceType("Desktop");
        }

        return event;
    }

    private LoginEventMessage buildLogoutEventMessage(User user, LogoutType logoutType) {
        LoginEventMessage event = new LoginEventMessage();
        event.setEmail(user.getEmail());
        event.setRole(user.getRole().name());
        event.setType(LoginEventMessage.EventType.LOGOUT);
        event.setLogoutType(convertLogoutType(logoutType));
        event.setTimestamp(LocalDateTime.now());

        String userAgent = httpServletRequest.getHeader("User-Agent");
        String ipAddress = getClientIpAddress();

        event.setIpAddress(ipAddress);

        if (userAgent != null) {
            event.setBrowser(parseBrowser(userAgent));
            event.setOs(parseOperatingSystem(userAgent));
            event.setDeviceType(parseDeviceType(userAgent));
        } else {
            event.setBrowser("Unknown");
            event.setOs("Unknown");
            event.setDeviceType("Desktop");
        }

        return event;
    }

    private String getClientIpAddress() {
        String xForwardedFor = httpServletRequest.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            String ip = xForwardedFor.split(",")[0].trim();
            if ("0:0:0:0:0:0:0:1".equals(ip) || "0:0:0:0:0:0:0:1%0".equals(ip)) {
                return "127.0.0.1";
            }
            return ip;
        }

        String xRealIp = httpServletRequest.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }

        String remoteAddr = httpServletRequest.getRemoteAddr();
        if ("0:0:0:0:0:0:0:1".equals(remoteAddr) || "0:0:0:0:0:0:0:1%0".equals(remoteAddr)) {
            return "127.0.0.1";
        }
        return remoteAddr;
    }

    private String parseBrowser(String userAgent) {
        if (userAgent.contains("Chrome")) return "Chrome";
        if (userAgent.contains("Firefox")) return "Firefox";
        if (userAgent.contains("Safari") && !userAgent.contains("Chrome")) return "Safari";
        if (userAgent.contains("Edge")) return "Edge";
        if (userAgent.contains("Opera") || userAgent.contains("OPR")) return "Opera";
        if (userAgent.contains("MSIE") || userAgent.contains("Trident")) return "Internet Explorer";
        return "Unknown";
    }

    private String parseOperatingSystem(String userAgent) {
        if (userAgent.contains("Windows")) return "Windows";
        if (userAgent.contains("Mac")) return "MacOS";
        if (userAgent.contains("Linux")) return "Linux";
        if (userAgent.contains("Android")) return "Android";
        if (userAgent.contains("iOS") || userAgent.contains("iPhone") || userAgent.contains("iPad")) return "iOS";
        return "Unknown";
    }

    private String parseDeviceType(String userAgent) {
        if (userAgent.contains("Mobile") || userAgent.contains("Android") && !userAgent.contains("Tablet")) {
            return "Mobile";
        }
        if (userAgent.contains("Tablet") || userAgent.contains("iPad")) {
            return "Tablet";
        }
        return "Desktop";
    }

    private LoginEventMessage.LogoutType convertLogoutType(LogoutType logoutType) {
        switch (logoutType) {
            case VOLUNTARY: return LoginEventMessage.LogoutType.VOLUNTARY;
            case TIMEOUT: return LoginEventMessage.LogoutType.TIMEOUT;
            case FORCED: return LoginEventMessage.LogoutType.FORCED;
            default: return LoginEventMessage.LogoutType.VOLUNTARY;
        }
    }
}