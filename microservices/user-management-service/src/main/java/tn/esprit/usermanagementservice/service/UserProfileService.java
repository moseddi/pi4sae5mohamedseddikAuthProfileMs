package tn.esprit.usermanagementservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.esprit.usermanagementservice.client.AuthServiceClient;
import tn.esprit.usermanagementservice.dto.*;
import tn.esprit.usermanagementservice.entity.Role;
import tn.esprit.usermanagementservice.entity.UserProfile;
import tn.esprit.usermanagementservice.repository.UserProfileRepository;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserProfileService {

    private final UserProfileRepository userProfileRepository;
    private final AuthServiceClient authServiceClient;

    // ThreadLocal to track emails being created by admin (prevents duplicate creation)
    private static ThreadLocal<String> creatingUsers = new ThreadLocal<>();

    @Transactional
    public UserProfileDTO createUser(CreateUserRequest request, String adminToken) {
        // Set marker that we're creating this user
        creatingUsers.set(request.getEmail());

        try {
            // Check if email exists in User Service
            if (userProfileRepository.existsByEmail(request.getEmail())) {
                throw new RuntimeException("Email already exists in user management service");
            }

            // 1. First create the profile in User Service (BEFORE calling Auth)
            UserProfile userProfile = UserProfile.builder()
                    .email(request.getEmail())
                    .firstName(request.getFirstName())
                    .lastName(request.getLastName())
                    .phoneNumber(request.getPhoneNumber())
                    .dateOfBirth(request.getDateOfBirth())
                    .address(request.getAddress())
                    .city(request.getCity())
                    .country(request.getCountry())
                    .role(request.getRole())
                    .createdBy(request.getCreatedBy())
                    .active(true)
                    .build();

            // Save the profile FIRST
            UserProfile savedProfile = userProfileRepository.save(userProfile);
            System.out.println("=== USER PROFILE CREATED ===");
            System.out.println("Profile ID: " + savedProfile.getId());

            // 2. Now create in Auth Service
            try {
                AuthRegisterRequest authRequest = new AuthRegisterRequest();
                authRequest.setEmail(request.getEmail());
                authRequest.setPassword(request.getPassword());
                authRequest.setConfirmPassword(request.getPassword());
                authRequest.setRole(request.getRole().name());

                System.out.println("=== CREATING USER IN AUTH SERVICE ===");
                System.out.println("Email: " + request.getEmail());
                System.out.println("Role: " + request.getRole());
                System.out.println("Token received: " + (adminToken != null ? adminToken.substring(0, Math.min(adminToken.length(), 50)) + "..." : "null"));

                // Clean token handling
                String cleanToken = adminToken;
                if (adminToken != null && !adminToken.startsWith("Bearer ")) {
                    cleanToken = "Bearer " + adminToken;
                }

                AuthResponse response = authServiceClient.createUserByAdmin(authRequest, cleanToken);
                System.out.println("Auth Service Response: " + response);

                // If auth service also calls back to /from-auth, our check in createEmptyProfileFromAuth
                // will prevent duplicate creation

            } catch (Exception e) {
                System.err.println("!!! FAILED TO CREATE USER IN AUTH SERVICE !!!");
                e.printStackTrace();

                // Rollback: Delete the profile we just created since auth failed
                userProfileRepository.delete(savedProfile);

                throw new RuntimeException("Failed to create user in auth service: " + e.getMessage());
            }

            return convertToDTO(savedProfile);

        } finally {
            // Clear the marker
            creatingUsers.remove();
        }
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

        if (request.getFirstName() != null) userProfile.setFirstName(request.getFirstName());
        if (request.getLastName() != null) userProfile.setLastName(request.getLastName());
        if (request.getPhoneNumber() != null) userProfile.setPhoneNumber(request.getPhoneNumber());
        if (request.getAddress() != null) userProfile.setAddress(request.getAddress());
        if (request.getCity() != null) userProfile.setCity(request.getCity());
        if (request.getCountry() != null) userProfile.setCountry(request.getCountry());
        if (request.getRole() != null) userProfile.setRole(request.getRole());
        if (request.getActive() != null) userProfile.setActive(request.getActive());
        if (request.getDateOfBirth() != null) userProfile.setDateOfBirth(request.getDateOfBirth());

        userProfile.recordActivity();
        UserProfile updatedProfile = userProfileRepository.save(userProfile);
        return convertToDTO(updatedProfile);
    }

    @Transactional
    public void deleteUser(Long id) {
        if (!userProfileRepository.existsById(id)) {
            throw new RuntimeException("User not found with id: " + id);
        }
        userProfileRepository.deleteById(id);
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

    public void createEmptyProfileFromAuth(String email, Role role) {
        // Check if we're currently creating this user via admin
        if (email.equals(creatingUsers.get())) {
            System.out.println("⚠️ Skipping duplicate creation for: " + email + " (already being created by admin)");
            return;
        }

        // Check if profile already exists (including fully created ones)
        if (userProfileRepository.existsByEmail(email)) {
            System.out.println("⚠️ Profile already exists for: " + email + " - skipping creation");
            return; // Already exists, don't create another
        }

        UserProfile userProfile = UserProfile.builder()
                .email(email)
                .firstName("")  // Empty, will be filled later
                .lastName("")   // Empty, will be filled later
                .role(role)
                .createdBy("auth-service")
                .active(true)
                .build();

        userProfileRepository.save(userProfile);
        System.out.println("✅ Empty profile created for: " + email);
    }

    @Transactional
    public void recordUserLogin(String email) {
        UserProfile userProfile = userProfileRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found with email: " + email));

        userProfile.recordLogin(); // This updates lastLoginAt and loginCount
        userProfileRepository.save(userProfile);
        System.out.println("✅ Login recorded for: " + email);
    }
}