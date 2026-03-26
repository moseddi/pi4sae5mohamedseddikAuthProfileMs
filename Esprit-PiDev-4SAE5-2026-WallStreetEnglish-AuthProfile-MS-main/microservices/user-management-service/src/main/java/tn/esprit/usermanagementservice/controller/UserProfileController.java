package tn.esprit.usermanagementservice.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.esprit.usermanagementservice.dto.CreateUserRequest;
import tn.esprit.usermanagementservice.dto.UpdateUserRequest;
import tn.esprit.usermanagementservice.dto.UserProfileDTO;
import tn.esprit.usermanagementservice.entity.Role;
import tn.esprit.usermanagementservice.service.KeycloakAdminService;
import tn.esprit.usermanagementservice.service.UserProfileService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserProfileController {

    private final UserProfileService userProfileService;
    private final KeycloakAdminService keycloakAdminService;



    /**
     * Create a new user profile (admin use or complete registration)
     */
    @PostMapping
    public ResponseEntity<UserProfileDTO> createUser(
            @Valid @RequestBody CreateUserRequest request,
            @RequestHeader(value = "Authorization", required = false) String token
    ) {
        System.out.println("=== CREATE USER ===");
        System.out.println("Email: " + request.getEmail());
        return ResponseEntity.ok(userProfileService.createUser(request, token));
    }

    /**
     * Complete or update user profile (for students filling missing info)
     */
    @PutMapping("/profile/{email}")
    public ResponseEntity<UserProfileDTO> completeProfile(
            @PathVariable String email,
            @Valid @RequestBody UpdateUserRequest request) {
        System.out.println("=== COMPLETE PROFILE ===");
        System.out.println("Email: " + email);
        return ResponseEntity.ok(userProfileService.updateUserProfile(email, request));
    }

    /**
     * Get user by ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<UserProfileDTO> getUserById(@PathVariable Long id) {
        return ResponseEntity.ok(userProfileService.getUserById(id));
    }

    /**
     * Get user by email
     */
    @GetMapping("/email/{email}")
    public ResponseEntity<UserProfileDTO> getUserByEmail(@PathVariable String email) {
        return ResponseEntity.ok(userProfileService.getUserByEmail(email));
    }

    /**
     * Get all users
     */
    @GetMapping
    public ResponseEntity<List<UserProfileDTO>> getAllUsers() {
        return ResponseEntity.ok(userProfileService.getAllUsers());
    }

    /**
     * Get users by role
     */
    @GetMapping("/role/{role}")
    public ResponseEntity<List<UserProfileDTO>> getUsersByRole(@PathVariable Role role) {
        return ResponseEntity.ok(userProfileService.getUsersByRole(role));
    }

    /**
     * Update user by ID
     */
    @PutMapping("/{id}")
    public ResponseEntity<UserProfileDTO> updateUser(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserRequest request) {
        return ResponseEntity.ok(userProfileService.updateUser(id, request));
    }

    /**
     * Delete user by ID
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        userProfileService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Test endpoint
     */
    @GetMapping("/test")
    public ResponseEntity<String> test() {
        return ResponseEntity.ok("User Management Service is working!");
    }

    /**
     * Record user login
     */
    @PostMapping("/record-login")
    public ResponseEntity<Void> recordUserLogin(@RequestParam String email) {
        userProfileService.recordUserLogin(email);
        return ResponseEntity.ok().build();
    }
    @PostMapping("/force-logout/{email}")
    public ResponseEntity<String> forceLogout(@PathVariable String email) {
        keycloakAdminService.logoutUserSessions(email);
        return ResponseEntity.ok("User logged out");
    }
    /**
     * Sync user from Auth Service when they don't exist in User DB
     */
    @PostMapping("/sync-from-auth")
    public ResponseEntity<UserProfileDTO> syncFromAuth(@RequestBody Map<String, Object> userData) {
        System.out.println("=== SYNC USER FROM AUTH ===");
        System.out.println("Data received: " + userData);

        String email = (String) userData.get("email");
        String role = (String) userData.get("role");

        UserProfileDTO syncedUser = userProfileService.syncUserFromAuth(email, role);
        return ResponseEntity.ok(syncedUser);
    }
}