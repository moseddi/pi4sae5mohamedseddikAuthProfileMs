package tn.esprit.usermanagementservice.dto;

import lombok.Data;
import tn.esprit.usermanagementservice.entity.Role;
import java.time.LocalDateTime;

@Data
public class UserProfileDTO {
    private Long id;
    private String email;
    private String firstName;
    private String lastName;
    private String phoneNumber;
    private Role role;
    private LocalDateTime dateOfBirth;
    private String address;
    private String city;
    private String country;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime lastLoginAt;
    private LocalDateTime accountCreatedAt;
    private LocalDateTime lastActivityAt;
    private boolean active;
    private Integer loginCount;
    private String createdBy;
}