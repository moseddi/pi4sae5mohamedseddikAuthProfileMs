package tn.esprit.usermanagementservice.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import tn.esprit.usermanagementservice.entity.Role;

import java.time.LocalDateTime;

@Data
public class CreateUserRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Email should be valid")
    private String email;

    @NotBlank(message = "First name is required")
    private String firstName;

    private String lastName;

    private String phoneNumber;
    private String address;
    private String city;
    private String country;
    @NotNull(message = "Role is required")
    private Role role;

    private String password;

    private String createdBy;
    private LocalDateTime dateOfBirth;
}