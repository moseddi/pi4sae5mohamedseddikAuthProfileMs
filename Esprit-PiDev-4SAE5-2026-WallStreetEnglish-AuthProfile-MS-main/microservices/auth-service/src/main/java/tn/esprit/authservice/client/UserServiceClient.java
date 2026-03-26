package tn.esprit.authservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "user-management-service", url = "http://localhost:8082")
public interface UserServiceClient {

    @PostMapping("/api/users/from-auth")
    void createProfile(
            @RequestParam("email") String email,
            @RequestParam("role") String role,
            @RequestParam(value = "firstName", required = false) String firstName,
            @RequestParam(value = "lastName", required = false) String lastName
    );

    @PostMapping("/api/users/record-login")
    void recordUserLogin(@RequestParam("email") String email);
}