package tn.esprit.authservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
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
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private static final String UNKNOWN_DEVICE_INFO = "Unknown";
    private static final String DEVICE_TYPE_TABLET = "Tablet";
    private static final String OS_ANDROID = "Android";
    private static final String DEVICE_TYPE_MOBILE = "Mobile";
    private static final String DEVICE_TYPE_DESKTOP = "Desktop";

    private static final String BROWSER_CHROME = "Chrome";
    private static final String BROWSER_FIREFOX = "Firefox";
    private static final String BROWSER_SAFARI = "Safari";
    private static final String BROWSER_EDGE = "Edge";
    private static final String BROWSER_OPERA = "Opera";
    private static final String BROWSER_IE = "Internet Explorer";

    private static final String TOKEN_FIELD_ACCESS_TOKEN = "access_token";
    private static final String TOKEN_FIELD_REFRESH_TOKEN = "refresh_token";

    private static final String OS_WINDOWS = "Windows";
    private static final String OS_MACOS = "MacOS";
    private static final String OS_LINUX = "Linux";
    private static final String OS_IOS = "iOS";

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

    @Value("${user.service.url:http://localhost:8082}")
    private String userServiceUrl;

    public AuthResponse register(RegisterRequest request) {
        log.info("========== REGISTRATION STARTED ==========");
        log.info("Email: {}", request.getEmail());
        log.info("Requested Role: {}", request.getRole());
        log.info("First Name: {}", request.getFirstName());
        log.info("Last Name: {}", request.getLastName());

        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            log.error("❌ Email already exists in database: {}", request.getEmail());
            throw new RuntimeException("Email already exists: " + request.getEmail());
        }

        String keycloakId = null;
        try {
            log.info("Step 1: Creating user in Keycloak: {}", request.getEmail());
            keycloakId = createUserInKeycloak(request);
            log.info("✅ User created in Keycloak with ID: {}", keycloakId);

            Role userRole = request.getRole() != null ? request.getRole() : Role.STUDENT;
            log.info("Step 2: Role determined: {}", userRole);

            assignRoleToUser(keycloakId, userRole);

            log.info("Step 3: Saving user in local database");
            User savedUser = saveUserInLocalDatabase(request, keycloakId, userRole);
            log.info("✅ User saved in local DB with ID: {}", savedUser.getId());

            log.info("Step 4: Getting token from Keycloak for user: {}", request.getEmail());
            Map<String, Object> tokenResponse = getTokenFromKeycloak(
                    request.getEmail(),
                    request.getPassword()
            );

            String token = null;
            String refreshToken = null;

            if (tokenResponse != null && tokenResponse.containsKey(TOKEN_FIELD_ACCESS_TOKEN)) {
                token = (String) tokenResponse.get(TOKEN_FIELD_ACCESS_TOKEN);
                refreshToken = (String) tokenResponse.get(TOKEN_FIELD_REFRESH_TOKEN);
                log.info("✅ Token obtained successfully");
            } else {
                log.warn("⚠️ Could not obtain token, but user was created");
            }

            log.info("Step 5: Creating profile in User Service");
            createUserProfile(request, savedUser);

            log.info("========== REGISTRATION COMPLETED SUCCESSFULLY ==========");

            return AuthResponse.builder()
                    .token(token)
                    .refreshToken(refreshToken)
                    .email(savedUser.getEmail())
                    .role(savedUser.getRole())
                    .userId(savedUser.getId())
                    .message("Registration successful")
                    .build();

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("========== REGISTRATION FAILED ==========");
            log.error("Error: {}", e.getMessage());
            throw new RuntimeException("Registration failed: " + e.getMessage());
        }
    }

    public AuthResponse registerByAdmin(RegisterRequest request) {
        log.info("========== ADMIN REGISTRATION (no UMS callback) ==========");
        log.info("Email: {}", request.getEmail());
        log.info("Requested Role: {}", request.getRole());

        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            log.error("❌ Email already exists in database: {}", request.getEmail());
            throw new RuntimeException("Email already exists: " + request.getEmail());
        }

        try {
            String keycloakId = createUserInKeycloak(request);
            log.info("✅ User created in Keycloak with ID: {}", keycloakId);

            Role userRole = request.getRole() != null ? request.getRole() : Role.STUDENT;
            assignRoleToUser(keycloakId, userRole);

            User savedUser = saveUserInLocalDatabase(request, keycloakId, userRole);
            log.info("✅ User saved in auth DB with ID: {}", savedUser.getId());

            log.info("========== ADMIN REGISTRATION COMPLETED ==========");
            return AuthResponse.builder()
                    .email(savedUser.getEmail())
                    .role(savedUser.getRole())
                    .userId(savedUser.getId())
                    .message("User created successfully by admin")
                    .build();

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("========== ADMIN REGISTRATION FAILED: {} ==========", e.getMessage());
            throw new RuntimeException("Admin registration failed: " + e.getMessage());
        }
    }

    private String createUserInKeycloak(RegisterRequest request) {
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

        if (response.getStatus() != HttpStatus.CREATED.value() && response.getStatus() != HttpStatus.OK.value()) {
            String errorMsg = "Failed to create user in Keycloak. Status: " + response.getStatus();
            log.error("❌ {}", errorMsg);
            throw new RuntimeException(errorMsg);
        }

        return response.getLocation().getPath().replaceAll(".*/([^/]+)$", "$1");
    }

    private void assignRoleToUser(String keycloakId, Role userRole) {
        log.info("Attempting to assign role '{}' in Keycloak", userRole);
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
    }

    private User saveUserInLocalDatabase(RegisterRequest request, String keycloakId, Role userRole) {
        User localUser = User.builder()
                .email(request.getEmail())
                .password("")
                .role(userRole)
                .keycloakId(keycloakId)
                .active(true)
                .emailVerified(true)
                .build();

        return userRepository.save(localUser);
    }

    private void createUserProfile(RegisterRequest request, User savedUser) {
        try {
            userServiceClient.createProfile(
                    savedUser.getEmail(),
                    savedUser.getRole().name(),
                    request.getFirstName(),
                    request.getLastName()
            );
            log.info("✅ Profile created in User Service");
        } catch (Exception e) {
            log.warn("⚠️ Profile creation failed: {}", e.getMessage());
        }
    }

    private boolean isUserBlocked(String email) {
        try {
            String url = userServiceUrl + "/api/users/check-blocked/" + email;
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

            if (tokenResponse == null || !tokenResponse.containsKey(TOKEN_FIELD_ACCESS_TOKEN)) {
                throw new RuntimeException("Failed to get token from Keycloak");
            }

            User user = userRepository.findByEmail(request.getEmail())
                    .orElseThrow(() -> new RuntimeException("User not found in database: " + request.getEmail()));

            if (isUserBlocked(request.getEmail())) {
                throw new RuntimeException("Your account is blocked. Please check your email for reactivation instructions.");
            }

            String accessToken = (String) tokenResponse.get(TOKEN_FIELD_ACCESS_TOKEN);
            Role roleFromJwt = extractRoleFromJwt(accessToken);

            if (roleFromJwt != null) {
                if (roleFromJwt != user.getRole()) {
                    log.info("🔄 Role mismatch detected for {}: DB={} JWT={} — syncing local DB",
                            user.getEmail(), user.getRole(), roleFromJwt);
                    user.setRole(roleFromJwt);
                    userRepository.save(user);
                }
            } else {
                roleFromJwt = user.getRole();
                log.warn("⚠️ No business role found in JWT for {}, falling back to DB role: {}",
                        user.getEmail(), roleFromJwt);
            }

            log.info("✅ Effective role for login response: {}", roleFromJwt);

            try {
                userServiceClient.recordUserLogin(request.getEmail());
                log.info("Login recorded for: {}", request.getEmail());
            } catch (Exception e) {
                log.error("Failed to record login in User Service: {}", e.getMessage());
            }

            sendLoginEventToRabbitMQ(user);

            return AuthResponse.builder()
                    .token(accessToken)
                    .refreshToken((String) tokenResponse.get(TOKEN_FIELD_REFRESH_TOKEN))
                    .email(user.getEmail())
                    .role(roleFromJwt)
                    .userId(user.getId())
                    .message("Login successful")
                    .build();

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Login failed: {}", e.getMessage());
            throw new RuntimeException("Invalid credentials");
        }
    }

    private void sendLoginEventToRabbitMQ(User user) {
        try {
            LoginEventMessage event = buildLoginEventMessage(user);
            rabbitTemplate.convertAndSend(RabbitMQConfig.LOGIN_QUEUE, event);
            log.info("✅ Sent LoginEventMessage to RabbitMQ: {} for {}", event.getType(), event.getEmail());
        } catch (Exception e) {
            log.error("❌ RabbitMQ error: {}", e.getMessage());
        }
    }

    public AuthResponse logout(String email, String logoutType) {
        try {
            log.info("Logging out user: {}", email);

            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("User not found in database: " + email));

            LogoutType type = logoutType != null ? LogoutType.valueOf(logoutType) : LogoutType.VOLUNTARY;

            sendLogoutEventToRabbitMQ(user, type);

            return AuthResponse.builder()
                    .email(user.getEmail())
                    .role(user.getRole())
                    .message("Logout successful")
                    .build();

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Logout failed: {}", e.getMessage());
            throw new RuntimeException("Logout failed: " + e.getMessage());
        }
    }

    private void sendLogoutEventToRabbitMQ(User user, LogoutType type) {
        try {
            LoginEventMessage event = buildLogoutEventMessage(user, type);
            rabbitTemplate.convertAndSend(RabbitMQConfig.LOGIN_QUEUE, event);
            log.info("✅ Sent LogoutEventMessage to RabbitMQ: {} for {}", event.getType(), event.getEmail());
        } catch (Exception e) {
            log.error("❌ RabbitMQ error during logout: {}", e.getMessage());
        }
    }

    private Role extractRoleFromJwt(String accessToken) {
        try {
            String[] parts = accessToken.split("\\.");
            if (parts.length < 2) return null;

            String padded = parts[1];
            int mod = padded.length() % 4;
            if (mod != 0) padded = padded + "=".repeat(4 - mod);

            String payload = new String(java.util.Base64.getUrlDecoder().decode(padded));
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            @SuppressWarnings("unchecked")
            Map<String, Object> claims = mapper.readValue(payload, Map.class);

            @SuppressWarnings("unchecked")
            Map<String, Object> realmAccess = (Map<String, Object>) claims.get("realm_access");
            if (realmAccess != null) {
                @SuppressWarnings("unchecked")
                java.util.List<String> roles = (java.util.List<String>) realmAccess.get("roles");
                if (roles != null) {
                    for (String r : roles) {
                        try {
                            Role role = Role.valueOf(r);
                            log.info("✅ Role extracted from JWT: {}", role);
                            return role;
                        } catch (IllegalArgumentException ignored) {
                            // Not a business role, skip
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("⚠️ Could not extract role from JWT: {}", e.getMessage());
        }
        return null;
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
            event.setBrowser(UNKNOWN_DEVICE_INFO);
            event.setOs(UNKNOWN_DEVICE_INFO);
            event.setDeviceType(DEVICE_TYPE_DESKTOP);
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
            event.setBrowser(UNKNOWN_DEVICE_INFO);
            event.setOs(UNKNOWN_DEVICE_INFO);
            event.setDeviceType(DEVICE_TYPE_DESKTOP);
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
        if (userAgent.contains(BROWSER_CHROME)) return BROWSER_CHROME;
        if (userAgent.contains(BROWSER_FIREFOX)) return BROWSER_FIREFOX;
        if (userAgent.contains(BROWSER_SAFARI) && !userAgent.contains(BROWSER_CHROME)) return BROWSER_SAFARI;
        if (userAgent.contains(BROWSER_EDGE)) return BROWSER_EDGE;
        if (userAgent.contains(BROWSER_OPERA) || userAgent.contains("OPR")) return BROWSER_OPERA;
        if (userAgent.contains("MSIE") || userAgent.contains("Trident")) return BROWSER_IE;
        return UNKNOWN_DEVICE_INFO;
    }

    private String parseOperatingSystem(String userAgent) {
        if (userAgent.contains(OS_WINDOWS)) return OS_WINDOWS;
        if (userAgent.contains("Mac")) return OS_MACOS;
        if (userAgent.contains(OS_LINUX)) return OS_LINUX;
        if (userAgent.contains(OS_ANDROID)) return OS_ANDROID;
        if (userAgent.contains("iOS") || userAgent.contains("iPhone") || userAgent.contains("iPad")) return OS_IOS;
        return UNKNOWN_DEVICE_INFO;
    }

    private String parseDeviceType(String userAgent) {
        if (userAgent.contains(DEVICE_TYPE_MOBILE) || (userAgent.contains(OS_ANDROID) && !userAgent.contains(DEVICE_TYPE_TABLET))) {
            return DEVICE_TYPE_MOBILE;
        }
        if (userAgent.contains(DEVICE_TYPE_TABLET) || userAgent.contains("iPad")) {
            return DEVICE_TYPE_TABLET;
        }
        return DEVICE_TYPE_DESKTOP;
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