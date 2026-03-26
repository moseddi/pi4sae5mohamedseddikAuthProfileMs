package tn.esprit.usermanagementservice.dto;

import lombok.Data;
import tn.esprit.usermanagementservice.entity.Role;

import java.time.LocalDateTime;

@Data
public class UpdateUserRequest {
    private String firstName;
    private String lastName;
    private String phoneNumber;
    private Role role;
    private Boolean active;
    private LocalDateTime dateOfBirth;
    private String address;
    private String city;
    private String country;
}