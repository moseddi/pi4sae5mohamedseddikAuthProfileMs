package tn.esprit.authservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import tn.esprit.authservice.dto.AuthResponse;
import tn.esprit.authservice.dto.LoginRequest;
import tn.esprit.authservice.dto.RegisterRequest;
import tn.esprit.authservice.entity.Role;
import tn.esprit.authservice.entity.User;
import tn.esprit.authservice.repository.UserRepository;
import tn.esprit.authservice.client.UserServiceClient;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final UserServiceClient userServiceClient;

    public AuthResponse login(LoginRequest request) {
        // Authenticate user
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );

        // Get user details
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found"));

        // DEBUG: Check user role
        System.out.println("===== DEBUG =====");
        System.out.println("User email: " + user.getEmail());
        System.out.println("User role: " + user.getRole());
        System.out.println("User role name: " + user.getRole().name());

        // Generate token WITH role
        Map<String, Object> extraClaims = new HashMap<>();
        extraClaims.put("role", user.getRole().name());
        extraClaims.put("userId", user.getId());

        // DEBUG: Check extraClaims
        System.out.println("Extra claims: " + extraClaims);

        String token = jwtService.generateToken(extraClaims, user);

        // DEBUG: Check token (first 50 chars)
        System.out.println("Token generated: " + token.substring(0, Math.min(token.length(), 50)) + "...");

        return AuthResponse.builder()
                .token(token)
                .email(user.getEmail())
                .role(user.getRole())
                .userId(user.getId())
                .message("Login successful")
                .build();
    }





    public AuthResponse register(RegisterRequest request) {
        // Check if user exists
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new RuntimeException("Email already exists");
        }

        // Create new user
        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(request.getRole() != null ? request.getRole() : Role.STUDENT)
                .active(true)
                .emailVerified(true)
                .build();

        userRepository.save(user);

        // ===== NEW: Create profile in User Service =====
        try {
            // You need to create this Feign client
            userServiceClient.createProfile(user.getEmail(), user.getRole());
            System.out.println("✅ Profile created in User Service for: " + user.getEmail());
        } catch (Exception e) {
            // Log but don't fail - profile can be created later
            System.out.println("⚠️ Profile not created in User Service: " + e.getMessage());
            // Student can still login, just needs to complete profile later
        }
        // ==============================================

        // Generate token WITH role
        Map<String, Object> extraClaims = new HashMap<>();
        extraClaims.put("role", user.getRole().name());
        extraClaims.put("userId", user.getId());

        String token = jwtService.generateToken(extraClaims, user);

        return AuthResponse.builder()
                .token(token)
                .email(user.getEmail())
                .role(user.getRole())
                .userId(user.getId())
                .message("Registration successful")
                .build();
    }
}