package tn.esprit.authservice.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;
import tn.esprit.authservice.dto.AuthResponse;
import tn.esprit.authservice.dto.LoginRequest;
import tn.esprit.authservice.dto.RegisterRequest;
import tn.esprit.authservice.entity.User;
import tn.esprit.authservice.repository.UserRepository;
import tn.esprit.authservice.service.AuthService;
import tn.esprit.authservice.client.UserServiceClient;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final UserRepository userRepository;
    private final AuthService authService;
    private final UserServiceClient userServiceClient;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        // Do login first and store the response
        AuthResponse response = authService.login(request);

        // Record login activity in User Service
        try {
            userServiceClient.recordUserLogin(request.getEmail());
            System.out.println("✅ Login recorded for: " + request.getEmail());
        } catch (Exception e) {
            // Log error but don't fail the login
            System.err.println("⚠️ Failed to record login activity: " + e.getMessage());
        }

        // Return the response
        return ResponseEntity.ok(response);
    }

    @GetMapping("/test")
    public ResponseEntity<String> test() {
        return ResponseEntity.ok("Auth service is working!");
    }

    @PostMapping("/check-password")
    public String checkPassword(@RequestBody LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found"));

        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        boolean match = encoder.matches(request.getPassword(), user.getPassword());

        return "Password matches: " + match +
                "\nUser active: " + user.isActive() +
                "\nEmail verified: " + user.isEmailVerified() +
                "\nHash: " + user.getPassword();
    }
}