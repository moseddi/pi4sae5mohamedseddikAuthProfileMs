package tn.esprit.usermanagementservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
import tn.esprit.usermanagementservice.dto.AuthRegisterRequest;
import tn.esprit.usermanagementservice.dto.AuthResponse;
import tn.esprit.usermanagementservice.dto.RoleUpdateRequest;

@FeignClient(name = "AUTH-SERVICE", url = "http://localhost:8081")
public interface AuthServiceClient {

    @PostMapping("/api/auth/register")
    AuthResponse registerUser(@RequestBody AuthRegisterRequest request);

    @PostMapping("/api/auth/admin/create")
    AuthResponse createUserByAdmin(
            @RequestBody AuthRegisterRequest request,
            @RequestHeader("Authorization") String authorizationHeader
    );

    // 🔥 NEW: Update user role in auth service
    @PutMapping("/api/auth/admin/role")
    AuthResponse updateUserRole(
            @RequestBody RoleUpdateRequest request,
            @RequestHeader("Authorization") String authorizationHeader
    );
}