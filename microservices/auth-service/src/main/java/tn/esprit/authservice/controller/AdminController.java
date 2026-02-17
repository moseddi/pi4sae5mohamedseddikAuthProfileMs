package tn.esprit.authservice.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import tn.esprit.authservice.dto.AuthResponse;
import tn.esprit.authservice.dto.RegisterRequest;
import tn.esprit.authservice.service.AuthService;

@RestController
@RequestMapping("/api/auth/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AuthService authService;

    @PostMapping("/create")
    @PreAuthorize("hasRole('ADMIN')") // Only admins can access this!
    public ResponseEntity<AuthResponse> createUserByAdmin(@Valid @RequestBody RegisterRequest request) {
        // Admin can create ANY role (including ADMIN)
        AuthResponse response = authService.register(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/test")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> testAdminAccess() {
        return ResponseEntity.ok("You are an admin! Access granted.");
    }
}