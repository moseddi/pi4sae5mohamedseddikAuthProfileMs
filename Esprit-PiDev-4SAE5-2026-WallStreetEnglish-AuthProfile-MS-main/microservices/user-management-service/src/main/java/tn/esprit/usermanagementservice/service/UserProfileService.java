package tn.esprit.usermanagementservice.service;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tn.esprit.usermanagementservice.client.AuthServiceClient;
import tn.esprit.usermanagementservice.dto.*;
import tn.esprit.usermanagementservice.entity.Role;
import tn.esprit.usermanagementservice.entity.UserProfile;
import tn.esprit.usermanagementservice.repository.UserProfileRepository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserProfileService {

    private final UserProfileRepository userProfileRepository;
    private final AuthServiceClient authServiceClient;
    private final KeycloakAdminService keycloakAdminService;

    private static ThreadLocal<String> creatingUsers = new ThreadLocal<>();

    @Transactional
    public void createMinimalProfile(String email, Role role) {
        creatingUsers.set(email);
        try {
            if (userProfileRepository.existsByEmail(email)) {
                System.out.println("⚠️ Profile already exists for: " + email);
                return;
            }

            UserProfile userProfile = UserProfile.builder()
                    .email(email)
                    .firstName("")
                    .lastName("")
                    .role(role)
                    .active(true)
                    .createdBy("auth-service")
                    .build();

            userProfileRepository.save(userProfile);
            System.out.println("✅ Minimal profile created for: " + email);
        } finally {
            creatingUsers.remove();
        }
    }

    @Transactional
    public UserProfileDTO createUser(CreateUserRequest request, String adminToken) {
        creatingUsers.set(request.getEmail());
        try {
            if (userProfileRepository.existsByEmail(request.getEmail())) {
                throw new RuntimeException("Email already exists in user management service");
            }

            UserProfile userProfile = UserProfile.builder()
                    .email(request.getEmail())
                    .firstName(request.getFirstName() != null ? request.getFirstName() : "")
                    .lastName(request.getLastName() != null ? request.getLastName() : "")
                    .phoneNumber(request.getPhoneNumber())
                    .dateOfBirth(request.getDateOfBirth())
                    .address(request.getAddress())
                    .city(request.getCity())
                    .country(request.getCountry())
                    .role(request.getRole())
                    .createdBy(request.getCreatedBy() != null ? request.getCreatedBy() : "admin")
                    .active(true)
                    .build();

            UserProfile savedProfile = userProfileRepository.save(userProfile);
            System.out.println("✅ User profile created with ID: " + savedProfile.getId());

            if (request.getPassword() != null && !request.getPassword().isEmpty()) {
                createUserInAuthService(request, adminToken, savedProfile);
            }

            return convertToDTO(savedProfile);
        } finally {
            creatingUsers.remove();
        }
    }

    private void createUserInAuthService(CreateUserRequest request, String adminToken, UserProfile savedProfile) {
        try {
            AuthRegisterRequest authRequest = new AuthRegisterRequest();
            authRequest.setEmail(request.getEmail());
            authRequest.setPassword(request.getPassword());
            authRequest.setConfirmPassword(request.getPassword());
            authRequest.setRole(request.getRole().name());

            System.out.println("=== CREATING USER IN AUTH SERVICE ===");

            String cleanToken = adminToken;
            if (adminToken != null && !adminToken.startsWith("Bearer ")) {
                cleanToken = "Bearer " + adminToken;
            }

            AuthResponse response = authServiceClient.createUserByAdmin(authRequest, cleanToken);
            System.out.println("Auth Service Response: " + response);

        } catch (Exception e) {
            System.err.println("!!! FAILED TO CREATE USER IN AUTH SERVICE !!!");
            e.printStackTrace();
            userProfileRepository.delete(savedProfile);
            throw new RuntimeException("Failed to create user in auth service: " + e.getMessage());
        }
    }

    @Transactional
    public UserProfileDTO updateUserProfile(String email, UpdateUserRequest request) {
        UserProfile userProfile = userProfileRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found with email: " + email));

        System.out.println("=== UPDATING PROFILE FOR: " + email + " ===");

        Role oldRole = userProfile.getRole();

        if (request.getFirstName() != null && !request.getFirstName().isEmpty()) {
            userProfile.setFirstName(request.getFirstName());
        }

        if (request.getLastName() != null && !request.getLastName().isEmpty()) {
            userProfile.setLastName(request.getLastName());
        }

        if (request.getPhoneNumber() != null) {
            userProfile.setPhoneNumber(request.getPhoneNumber());
        }

        if (request.getAddress() != null) {
            userProfile.setAddress(request.getAddress());
        }

        if (request.getCity() != null) {
            userProfile.setCity(request.getCity());
        }

        if (request.getCountry() != null) {
            userProfile.setCountry(request.getCountry());
        }

        if (request.getDateOfBirth() != null) {
            userProfile.setDateOfBirth(request.getDateOfBirth());
        }

        if (request.getRole() != null && request.getRole() != oldRole) {
            userProfile.setRole(request.getRole());
        }

        if (request.getActive() != null) {
            userProfile.setActive(request.getActive());
        }

        userProfile.recordActivity();
        UserProfile updatedProfile = userProfileRepository.save(userProfile);

        System.out.println("✅ Profile updated successfully for: " + email);
        return convertToDTO(updatedProfile);
    }

    public UserProfileDTO getUserById(Long id) {
        UserProfile userProfile = userProfileRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + id));
        return convertToDTO(userProfile);
    }

    public UserProfileDTO getUserByEmail(String email) {
        UserProfile userProfile = userProfileRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found with email: " + email));
        return convertToDTO(userProfile);
    }

    public List<UserProfileDTO> getAllUsers() {
        return userProfileRepository.findAll().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public List<UserProfileDTO> getUsersByRole(Role role) {
        return userProfileRepository.findByRole(role).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public UserProfileDTO updateUser(Long id, UpdateUserRequest request) {
        UserProfile userProfile = userProfileRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + id));

        Role oldRole = userProfile.getRole();
        boolean roleChanged = false;

        if (request.getFirstName() != null) userProfile.setFirstName(request.getFirstName());
        if (request.getLastName() != null) userProfile.setLastName(request.getLastName());
        if (request.getPhoneNumber() != null) userProfile.setPhoneNumber(request.getPhoneNumber());
        if (request.getAddress() != null) userProfile.setAddress(request.getAddress());
        if (request.getCity() != null) userProfile.setCity(request.getCity());
        if (request.getCountry() != null) userProfile.setCountry(request.getCountry());

        // 🔥 FIX: Update role in Keycloak AND Auth Service when changed
        if (request.getRole() != null && request.getRole() != oldRole) {
            log.info("🔄 Role changing from {} to {}", oldRole, request.getRole());
            userProfile.setRole(request.getRole());
            roleChanged = true;

            // 1. Update in Keycloak
            try {
                keycloakAdminService.updateUserRole(userProfile.getEmail(), request.getRole().name());
                log.info("✅ Role updated in Keycloak for: {}", userProfile.getEmail());
            } catch (Exception e) {
                log.error("❌ Failed to update role in Keycloak: {}", e.getMessage());
            }

            // 2. 🔥 FIX: Update in Auth Service database with admin token
            try {
                // Get token from request header
                ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
                String adminToken = null;

                if (attributes != null) {
                    HttpServletRequest httpRequest = attributes.getRequest();
                    String authHeader = httpRequest.getHeader("Authorization");
                    if (authHeader != null && authHeader.startsWith("Bearer ")) {
                        adminToken = authHeader.substring(7);
                        log.info("✅ Extracted admin token from request");
                    }
                }

                // If we couldn't get token from request, try security context
                if (adminToken == null) {
                    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                    if (auth != null && auth.getCredentials() != null) {
                        adminToken = auth.getCredentials().toString();
                        log.info("✅ Extracted admin token from security context");
                    }
                }

                if (adminToken == null) {
                    log.warn("⚠️ No admin token found, skipping auth service update");
                } else {
                    // Create role update request
                    RoleUpdateRequest roleUpdate = new RoleUpdateRequest();
                    roleUpdate.setEmail(userProfile.getEmail());
                    roleUpdate.setRole(request.getRole());

                    // Call auth service with token
                    String authHeader = "Bearer " + adminToken;
                    log.info("Calling auth service with token: {}...", adminToken.substring(0, Math.min(20, adminToken.length())));

                    AuthResponse response = authServiceClient.updateUserRole(roleUpdate, authHeader);
                    log.info("✅ Role updated in Auth Service for: {}, response: {}", userProfile.getEmail(), response);
                }
            } catch (Exception e) {
                log.error("❌ Failed to update role in Auth Service: {}", e.getMessage());
                e.printStackTrace();
                // Don't throw - we still want to update local DB and Keycloak
            }
        }

        if (request.getActive() != null) userProfile.setActive(request.getActive());
        if (request.getDateOfBirth() != null) userProfile.setDateOfBirth(request.getDateOfBirth());

        userProfile.recordActivity();
        UserProfile updatedProfile = userProfileRepository.save(userProfile);

        // Force logout if role changed
        if (roleChanged) {
            try {
                keycloakAdminService.logoutUserSessions(userProfile.getEmail());
                log.info("✅ User logged out to apply new role");
            } catch (Exception e) {
                log.error("⚠️ Could not logout user: {}", e.getMessage());
            }
        }

        return convertToDTO(updatedProfile);
    }

    @Transactional
    public void deleteUser(Long id) {
        if (!userProfileRepository.existsById(id)) {
            throw new RuntimeException("User not found with id: " + id);
        }
        userProfileRepository.deleteById(id);
    }

    @Transactional
    public void recordUserLogin(String email) {
        UserProfile userProfile = userProfileRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found with email: " + email));

        userProfile.recordLogin();
        userProfileRepository.save(userProfile);
        System.out.println("✅ Login recorded for: " + email);
    }

    private UserProfileDTO convertToDTO(UserProfile userProfile) {
        UserProfileDTO dto = new UserProfileDTO();
        dto.setId(userProfile.getId());
        dto.setEmail(userProfile.getEmail());
        dto.setFirstName(userProfile.getFirstName());
        dto.setLastName(userProfile.getLastName());
        dto.setPhoneNumber(userProfile.getPhoneNumber());
        dto.setRole(userProfile.getRole());
        dto.setCreatedAt(userProfile.getCreatedAt());
        dto.setUpdatedAt(userProfile.getUpdatedAt());
        dto.setLastLoginAt(userProfile.getLastLoginAt());
        dto.setAccountCreatedAt(userProfile.getAccountCreatedAt());
        dto.setLastActivityAt(userProfile.getLastActivityAt());
        dto.setActive(userProfile.isActive());
        dto.setLoginCount(userProfile.getLoginCount());
        dto.setCreatedBy(userProfile.getCreatedBy());
        dto.setDateOfBirth(userProfile.getDateOfBirth());
        dto.setAddress(userProfile.getAddress());
        dto.setCity(userProfile.getCity());
        dto.setCountry(userProfile.getCountry());
        return dto;
    }

    public void forceUserLogout(String email) {
        keycloakAdminService.logoutUserSessions(email);
    }

    @Transactional
    public UserProfileDTO syncUserFromAuth(String email, String role) {
        Optional<UserProfile> existingProfile = userProfileRepository.findByEmail(email);

        if (existingProfile.isPresent()) {
            System.out.println("✅ User already exists, returning existing profile: " + email);
            return convertToDTO(existingProfile.get());
        }

        UserProfile newProfile = UserProfile.builder()
                .email(email)
                .firstName("")
                .lastName("")
                .role(Role.valueOf(role))
                .active(true)
                .createdBy("auth-sync")
                .build();

        UserProfile savedProfile = userProfileRepository.save(newProfile);
        System.out.println("✅ User synced from Auth and created in User DB: " + email);

        return convertToDTO(savedProfile);
    }
}