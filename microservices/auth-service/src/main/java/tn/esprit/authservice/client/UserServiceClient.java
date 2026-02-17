package tn.esprit.authservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import tn.esprit.authservice.entity.Role;

@FeignClient(name = "user-management-service")
public interface UserServiceClient {

    @PostMapping("/api/users/from-auth")
    void createProfile(@RequestParam("email") String email,
                       @RequestParam("role") Role role);
    @PostMapping("/api/users/record-login")
    void recordUserLogin(@RequestParam("email") String email);
}