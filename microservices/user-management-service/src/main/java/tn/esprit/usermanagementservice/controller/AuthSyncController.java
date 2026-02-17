package tn.esprit.usermanagementservice.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.esprit.usermanagementservice.entity.Role;
import tn.esprit.usermanagementservice.service.UserProfileService;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class AuthSyncController {

    private final UserProfileService userProfileService;

    @PostMapping("/from-auth")
    public ResponseEntity<Void> createProfileFromAuth(
            @RequestParam String email,
            @RequestParam Role role) {

        userProfileService.createEmptyProfileFromAuth(email, role);
        return ResponseEntity.ok().build();
    }
}