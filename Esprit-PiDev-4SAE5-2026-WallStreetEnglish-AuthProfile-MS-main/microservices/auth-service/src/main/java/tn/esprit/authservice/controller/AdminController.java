package tn.esprit.authservice.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import tn.esprit.authservice.dto.AuthResponse;
import tn.esprit.authservice.dto.RegisterRequest;
import tn.esprit.authservice.dto.RoleUpdateRequest;
import tn.esprit.authservice.entity.Role;
import tn.esprit.authservice.entity.User;
import tn.esprit.authservice.repository.UserRepository;
import tn.esprit.authservice.service.AuthService;
import tn.esprit.authservice.service.KeycloakService;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/auth/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AuthService authService;
    private final UserRepository userRepository;
    private final KeycloakService keycloakService;

    @PostMapping("/create")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> createUserByAdmin(@Valid @RequestBody RegisterRequest request) {
        log.info("========== ADMIN CREATE USER ==========");
        log.info("Email: {}", request.getEmail());
        log.info("Role: {}", request.getRole());
        log.info("First Name: {}", request.getFirstName());
        log.info("Last Name: {}", request.getLastName());
        log.info("=======================================");

        try {
            AuthResponse response = authService.register(request);
            log.info("✅ User created successfully with ID: {}", response.getUserId());

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "User created successfully");
            result.put("userId", response.getUserId());
            result.put("email", response.getEmail());
            result.put("role", response.getRole());

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("❌ Failed to create user: {}", e.getMessage(), e);

            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @GetMapping("/test")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> testAdminAccess() {
        return ResponseEntity.ok("You are an admin! Access granted.");
    }

    @PutMapping("/role")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> updateUserRole(@RequestBody RoleUpdateRequest request) {
        log.info("========== UPDATE ROLE ==========");
        log.info("Email: {}", request.getEmail());
        log.info("New Role: {}", request.getRole());
        log.info("================================");

        try {
            // 1. Update in auth database
            User user = userRepository.findByEmail(request.getEmail())
                    .orElseThrow(() -> new RuntimeException("User not found in auth database"));

            Role oldRole = user.getRole();
            user.setRole(request.getRole());
            userRepository.save(user);
            log.info("✅ Auth DB updated from {} to {}", oldRole, request.getRole());

            // 2. Update in Keycloak
            try {
                keycloakService.updateUserRole(request.getEmail(), request.getRole().name());
                log.info("✅ Keycloak updated");
            } catch (Exception e) {
                log.error("❌ Keycloak update failed: {}", e.getMessage());
            }

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "Role updated successfully");
            result.put("email", user.getEmail());
            result.put("oldRole", oldRole);
            result.put("newRole", user.getRole());

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("❌ Failed: {}", e.getMessage(), e);

            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }
}