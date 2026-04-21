package tn.esprit.usermanagementservice.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.esprit.usermanagementservice.dto.CreateUserRequest;
import tn.esprit.usermanagementservice.dto.UpdateUserRequest;
import tn.esprit.usermanagementservice.dto.UserProfileDTO;
import tn.esprit.usermanagementservice.entity.LoginHistory;
import tn.esprit.usermanagementservice.entity.Role;
import tn.esprit.usermanagementservice.entity.UserProfile;
import tn.esprit.usermanagementservice.repository.LoginHistoryRepository;
import tn.esprit.usermanagementservice.repository.UserProfileRepository;
import tn.esprit.usermanagementservice.service.KeycloakAdminService;
import tn.esprit.usermanagementservice.service.UserProfileService;

import java.util.HashMap;
import java.util.stream.Collectors;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Slf4j
public class UserProfileController {

    private final UserProfileService userProfileService;
    private final KeycloakAdminService keycloakAdminService;
    private final LoginHistoryRepository loginHistoryRepository;
    private final UserProfileRepository userProfileRepository;


    // ── User CRUD ────────────────────────────────────────────────────────────

    @PostMapping
    public ResponseEntity<UserProfileDTO> createUser(
            @Valid @RequestBody CreateUserRequest request,
            @RequestHeader(value = "Authorization", required = false) String token) {
        log.info("=== CREATE USER: {}", request.getEmail());
        return ResponseEntity.ok(userProfileService.createUser(request, token));
    }

    @PutMapping("/profile/{email}")
    public ResponseEntity<UserProfileDTO> completeProfile(
            @PathVariable String email,
            @Valid @RequestBody UpdateUserRequest request) {
        log.info("=== COMPLETE PROFILE: {}", email);
        return ResponseEntity.ok(userProfileService.updateUserProfile(email, request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserProfileDTO> getUserById(@PathVariable Long id) {
        return ResponseEntity.ok(userProfileService.getUserById(id));
    }

    @GetMapping("/email/{email}")
    public ResponseEntity<UserProfileDTO> getUserByEmail(@PathVariable String email) {
        return ResponseEntity.ok(userProfileService.getUserByEmail(email));
    }

    @GetMapping
    public ResponseEntity<List<UserProfileDTO>> getAllUsers() {
        return ResponseEntity.ok(userProfileService.getAllUsers());
    }

    @GetMapping("/role/{role}")
    public ResponseEntity<List<UserProfileDTO>> getUsersByRole(@PathVariable Role role) {
        return ResponseEntity.ok(userProfileService.getUsersByRole(role));
    }

    @PutMapping("/{id}")
    public ResponseEntity<UserProfileDTO> updateUser(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserRequest request) {
        return ResponseEntity.ok(userProfileService.updateUser(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        userProfileService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/test")
    public ResponseEntity<String> test() {
        return ResponseEntity.ok("User Management Service is working!");
    }

    // ── Login / Logout recording ─────────────────────────────────────────────

    @PostMapping("/record-login")
    public ResponseEntity<Void> recordUserLogin(@RequestParam String email) {
        userProfileService.recordUserLogin(email);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/record-logout")
    public ResponseEntity<Void> recordUserLogout(
            @RequestParam String email,
            @RequestParam(defaultValue = "VOLUNTARY") LoginHistory.LogoutType logoutType) {
        userProfileService.recordUserLogout(email, logoutType);
        return ResponseEntity.ok().build();
    }

    // ── Force logout (admin action) ───────────────────────────────────────────

    @PostMapping("/force-logout/{email}")
    public ResponseEntity<String> forceLogout(@PathVariable String email) {
        keycloakAdminService.logoutUserSessions(email);
        userProfileService.recordUserLogout(email, LoginHistory.LogoutType.FORCED);
        log.info("🔒 Force logout executed for: {}", email);
        return ResponseEntity.ok("User " + email + " logged out");
    }

    // ── Security monitor endpoints ────────────────────────────────────────────

    @GetMapping("/recent-logins")
    public ResponseEntity<List<LoginHistory>> getRecentLogins() {
        return ResponseEntity.ok(loginHistoryRepository.findTop20ByOrderByLoginTimeDesc());
    }

    @GetMapping("/logins/today")
    public ResponseEntity<List<LoginHistory>> getTodayLogins() {
        LocalDateTime since = LocalDateTime.now().minusHours(24);
        return ResponseEntity.ok(loginHistoryRepository.findByLoginTimeAfterOrderByLoginTimeDesc(since));
    }

    @GetMapping("/sessions/active-count")
    public ResponseEntity<Long> getActiveSessionCount() {
        return ResponseEntity.ok(loginHistoryRepository.countByActive(true));
    }

    @GetMapping("/logins/suspicious-count")
    public ResponseEntity<Long> getSuspiciousCount() {
        LocalDateTime since = LocalDateTime.now().minusHours(24);
        return ResponseEntity.ok(loginHistoryRepository.countBySuspiciousAndLoginTimeAfter(true, since));
    }

    @PostMapping("/sync-from-auth")
    public ResponseEntity<UserProfileDTO> syncFromAuth(@RequestBody Map<String, Object> userData) {
        log.info("=== SYNC USER FROM AUTH: {}", userData);
        String email = (String) userData.get("email");
        String role = (String) userData.get("role");
        return ResponseEntity.ok(userProfileService.syncUserFromAuth(email, role));
    }

    @GetMapping("/active-sessions")
    public ResponseEntity<List<LoginHistory>> getActiveSessions() {
        return ResponseEntity.ok(loginHistoryRepository.findByActiveTrueOrderByLoginTimeDesc());
    }

    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Object>> getStatistics() {
        LocalDateTime twentyFourHoursAgo = LocalDateTime.now().minusHours(24);
        List<LoginHistory> events = loginHistoryRepository.findAllEventsLast24h(twentyFourHoursAgo);

        long logins = events.stream().filter(e -> e.getType() == LoginHistory.EventType.LOGIN).count();
        long logouts = events.stream().filter(e -> e.getType() == LoginHistory.EventType.LOGOUT).count();
        long active = loginHistoryRepository.countByActive(true);
        long suspicious = loginHistoryRepository.countBySuspiciousAndLoginTimeAfter(true, twentyFourHoursAgo);

        Map<String, Object> stats = new HashMap<>();
        stats.put("logins", logins);
        stats.put("logouts", logouts);
        stats.put("activeSessions", active);
        stats.put("suspicious", suspicious);
        stats.put("period", "Last 24 Hours");

        return ResponseEntity.ok(stats);
    }

    @GetMapping("/recent-logins-formatted")
    public ResponseEntity<List<String>> getRecentLoginsFormatted() {
        LocalDateTime twentyFourHoursAgo = LocalDateTime.now().minusHours(24);
        List<LoginHistory> events = loginHistoryRepository.findAllEventsLast24h(twentyFourHoursAgo);

        List<String> messages = events.stream()
                .map(LoginHistory::getMessage)
                .limit(50)
                .collect(Collectors.toList());

        log.info("📋 Returning {} events from last 24 hours", messages.size());
        return ResponseEntity.ok(messages);
    }

    // ── Block/Unblock endpoints ─────────────────────────────────────────────

    @PostMapping("/block/{email}")
    public ResponseEntity<String> blockUser(@PathVariable String email, @RequestParam String reason) {
        log.info("🔒 Blocking user: {} reason: {}", email, reason);
        userProfileService.blockUser(email, reason);
        return ResponseEntity.ok("User " + email + " has been blocked");
    }

    @PostMapping("/unblock/{email}")
    public ResponseEntity<String> unblockUser(@PathVariable String email) {
        log.info("🔓 Unblocking user: {}", email);
        userProfileService.unblockUser(email);
        return ResponseEntity.ok("User " + email + " has been unblocked");
    }

    // ── Reactivation endpoints ─────────────────────────────────────────────

    @GetMapping("/reactivate/{token}")
    public ResponseEntity<Map<String, Object>> validateReactivationToken(@PathVariable String token) {
        boolean isValid = userProfileService.validateReactivationToken(token);
        Map<String, Object> response = new HashMap<>();
        response.put("valid", isValid);

        // ✅ ADD EMAIL TO RESPONSE
        if (isValid) {
            Optional<UserProfile> user = userProfileRepository.findAll().stream()
                    .filter(u -> token.equals(u.getReactivationToken()))
                    .findFirst();
            user.ifPresent(u -> response.put("email", u.getEmail()));
        }

        return ResponseEntity.ok(response);
    }

    // ✅ FIXED: Return Map instead of String
    @PostMapping("/reactivate-request")
    public ResponseEntity<Map<String, String>> requestReactivation(@RequestBody Map<String, String> request) {
        log.info("📝 Reactivation request from: {}", request.get("email"));
        userProfileService.processReactivationRequest(request);
        Map<String, String> response = new HashMap<>();
        response.put("message", "Reactivation request submitted successfully");
        response.put("status", "success");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/send-reactivation-email/{email}")
    public ResponseEntity<String> sendReactivationEmail(@PathVariable String email) {
        log.info("📧 Sending reactivation email to: {}", email);
        userProfileService.sendReactivationEmail(email);
        return ResponseEntity.ok("Reactivation email sent to " + email);
    }

    @GetMapping("/unblock/{email}")
    public ResponseEntity<String> unblockUserGet(@PathVariable String email) {
        log.info("🔓 Unblocking user via GET: {}", email);
        userProfileService.unblockUser(email);
        return ResponseEntity.ok("User " + email + " has been unblocked. You can now login.");
    }

    @GetMapping("/check-blocked/{email}")
    public ResponseEntity<Map<String, Object>> checkBlocked(@PathVariable String email) {
        UserProfileDTO user = userProfileService.getUserByEmail(email);
        Map<String, Object> response = new HashMap<>();
        response.put("blocked", user.isBlocked());
        response.put("blockedReason", user.getBlockedReason());
        return ResponseEntity.ok(response);
    }
}