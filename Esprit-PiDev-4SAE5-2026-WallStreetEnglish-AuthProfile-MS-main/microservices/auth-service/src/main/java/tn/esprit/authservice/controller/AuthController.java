package tn.esprit.authservice.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;
import tn.esprit.authservice.client.UserServiceClient;
import tn.esprit.authservice.dto.AuthResponse;
import tn.esprit.authservice.dto.ForgotPasswordRequest;
import tn.esprit.authservice.dto.LoginRequest;
import tn.esprit.authservice.dto.RegisterRequest;
import tn.esprit.authservice.entity.User;
import tn.esprit.authservice.repository.UserRepository;
import tn.esprit.authservice.service.AuthService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository userRepository;
    private final AuthService authService;
    private final UserServiceClient userServiceClient;
    private final Keycloak keycloakAdmin;
    private final JavaMailSender mailSender;

    @Value("${keycloak.realm}")
    private String realm;

    // ★★★ SOLUTION: rendre cette Map STATIC pour qu'elle soit partagée entre toutes les requêtes ★★★
    private static Map<String, String> resetTokens = new HashMap<>();

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);

        try {
            userServiceClient.recordUserLogin(request.getEmail());
            log.info("✅ Login recorded for: {}", request.getEmail());
        } catch (Exception e) {
            log.error("⚠️ Failed to record login activity: {}", e.getMessage());
        }

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

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        log.info("========== FORGOT PASSWORD REQUEST ==========");
        log.info("Email: {}", request.getEmail());

        try {
            // Récupère TOUS les utilisateurs
            List<UserRepresentation> allUsers = keycloakAdmin.realm(realm).users().list();
            log.info("📊 TOTAL utilisateurs dans Keycloak: {}", allUsers.size());

            // Cherche l'utilisateur manuellement
            UserRepresentation foundUser = null;
            String searchEmail = request.getEmail().toLowerCase().trim();

            for (UserRepresentation user : allUsers) {
                if (user.getEmail() != null && user.getEmail().toLowerCase().trim().equals(searchEmail)) {
                    foundUser = user;
                    log.info("✅ Trouvé par EMAIL: {}", user.getEmail());
                    break;
                }
                if (user.getUsername() != null && user.getUsername().toLowerCase().trim().equals(searchEmail)) {
                    foundUser = user;
                    log.info("✅ Trouvé par USERNAME: {}", user.getUsername());
                    break;
                }
            }

            if (foundUser != null) {
                log.info("✅ Utilisateur trouvé: ID={}", foundUser.getId());

                // Générer token
                String token = UUID.randomUUID().toString();

                // ★★★ SAUVEGARDE DANS LA MAP STATIC ★★★
                resetTokens.put(token, foundUser.getId());
                log.info("📝 Token sauvegardé: {} pour l'utilisateur {}", token, foundUser.getId());
                log.info("📝 Tokens disponibles: {}", resetTokens.keySet());

                // Lien vers TA page Angular
                String resetLink = "http://localhost:4200/reset-password?token=" + token;

                // Envoyer email
                sendResetEmail(request.getEmail(), resetLink);

                log.info("✅ Email envoyé à {} avec lien: {}", request.getEmail(), resetLink);
            } else {
                log.warn("❌ Utilisateur NON trouvé: {}", request.getEmail());
                // Affiche les 5 premiers utilisateurs pour debug
                for (int i = 0; i < Math.min(5, allUsers.size()); i++) {
                    UserRepresentation u = allUsers.get(i);
                    log.info("User {}: Email='{}', Username='{}'", i+1, u.getEmail(), u.getUsername());
                }
            }

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "If your email exists, you will receive a password reset link.");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("❌ Error: {}", e.getMessage(), e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "If your email exists, you will receive a password reset link.");
            return ResponseEntity.ok(response);
        }
    }

    private void sendResetEmail(String to, String resetLink) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom("seddik202209@gmail.com");
            message.setTo(to);
            message.setSubject("🔐 Reset your Wall Street English password");
            message.setText(
                    "Hello,\n\n" +
                            "You requested to reset your password.\n\n" +
                            "Click this link to reset your password: " + resetLink + "\n\n" +
                            "This link will expire in 24 hours.\n\n" +
                            "If you didn't request this, ignore this email.\n\n" +
                            "Wall Street English Team"
            );

            mailSender.send(message);
            log.info("✅ Email sent successfully to: {}", to);

        } catch (Exception e) {
            log.error("❌ Failed to send email to {}: {}", to, e.getMessage());
        }
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody Map<String, String> request) {
        log.info("========== RESET PASSWORD REQUEST ==========");

        String token = request.get("token");
        String newPassword = request.get("newPassword");
        String confirmPassword = request.get("confirmPassword");

        log.info("Token reçu: {}", token);
        log.info("Tokens disponibles: {}", resetTokens.keySet());

        if (token == null || newPassword == null || confirmPassword == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Missing required fields"));
        }

        if (!newPassword.equals(confirmPassword)) {
            return ResponseEntity.badRequest().body(Map.of("message", "Passwords do not match"));
        }

        if (newPassword.length() < 6) {
            return ResponseEntity.badRequest().body(Map.of("message", "Password must be at least 6 characters"));
        }

        if (!resetTokens.containsKey(token)) {
            log.error("❌ Token non trouvé: {}", token);
            return ResponseEntity.badRequest().body(Map.of("message", "Invalid or expired token"));
        }

        String userId = resetTokens.get(token);
        log.info("✅ Token valide! UserId associé: {}", userId);

        try {
            CredentialRepresentation credential = new CredentialRepresentation();
            credential.setType(CredentialRepresentation.PASSWORD);
            credential.setValue(newPassword);
            credential.setTemporary(false);

            keycloakAdmin.realm(realm).users().get(userId).resetPassword(credential);

            // ★★★ SUPPRIME LE TOKEN APRÈS UTILISATION ★★★
            resetTokens.remove(token);
            log.info("📝 Token supprimé, tokens restants: {}", resetTokens.keySet());

            log.info("✅ Password reset successful for user: {}", userId);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Password reset successfully"
            ));

        } catch (Exception e) {
            log.error("❌ Error resetting password: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of(
                    "message", "Error resetting password: " + e.getMessage()
            ));
        }
    }

    @GetMapping("/validate-reset-token")
    public ResponseEntity<?> validateResetToken(@RequestParam String token) {
        log.info("Validating reset token: {}", token);

        if (resetTokens.containsKey(token)) {
            return ResponseEntity.ok(Map.of("valid", true));
        } else {
            return ResponseEntity.badRequest().body(Map.of(
                    "valid", false,
                    "message", "Invalid or expired token"
            ));
        }
    }

    @GetMapping("/test-email")
    public String testEmail() {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom("seddik202209@gmail.com");
            message.setTo("seddik202209@gmail.com");
            message.setSubject("TEST EMAIL");
            message.setText("Si tu reçois ça, l'email fonctionne !");

            mailSender.send(message);
            return "✅ Email test envoyé ! Vérifie ta boîte mail.";
        } catch (Exception e) {
            return "❌ Erreur: " + e.getMessage();
        }
    }
}