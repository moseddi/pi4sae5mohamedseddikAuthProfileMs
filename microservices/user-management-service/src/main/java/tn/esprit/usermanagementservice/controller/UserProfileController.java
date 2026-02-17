package tn.esprit.usermanagementservice.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.esprit.usermanagementservice.dto.CreateUserRequest;
import tn.esprit.usermanagementservice.dto.UpdateUserRequest;
import tn.esprit.usermanagementservice.dto.UserProfileDTO;
import tn.esprit.usermanagementservice.entity.Role;
import tn.esprit.usermanagementservice.service.UserProfileService;
import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserProfileController {

    private final UserProfileService userProfileService;

    @PostMapping
    public ResponseEntity<UserProfileDTO> createUser(
            @Valid @RequestBody CreateUserRequest request,
            @RequestHeader(value = "Authorization", required = false) String token  // 👈 Make optional
    ) {
        System.out.println("Creating user: " + request.getEmail());
        // Token is optional for creation
        return ResponseEntity.ok(userProfileService.createUser(request, token));
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
    @PostMapping("/record-login")
    public ResponseEntity<Void> recordUserLogin(@RequestParam String email) {
        userProfileService.recordUserLogin(email);
        return ResponseEntity.ok().build();
    }
}