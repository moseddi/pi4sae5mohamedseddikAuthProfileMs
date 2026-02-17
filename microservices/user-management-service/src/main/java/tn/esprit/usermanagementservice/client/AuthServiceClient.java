package tn.esprit.usermanagementservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import tn.esprit.usermanagementservice.dto.AuthRegisterRequest;
import tn.esprit.usermanagementservice.dto.AuthResponse;

// ✅ FIX: Add URL to bypass service discovery issues
@FeignClient(name = "AUTH-SERVICE", url = "http://localhost:8081")
public interface AuthServiceClient {

    @PostMapping("/api/auth/register")
    AuthResponse registerUser(@RequestBody AuthRegisterRequest request);

    @PostMapping("/api/auth/admin/create")
    AuthResponse createUserByAdmin(
            @RequestBody AuthRegisterRequest request,
            @RequestHeader("Authorization") String authorizationHeader
    );
}