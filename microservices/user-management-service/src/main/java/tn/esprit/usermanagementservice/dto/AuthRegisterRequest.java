package tn.esprit.usermanagementservice.dto;

import lombok.Data;

@Data
public class AuthRegisterRequest {
    private String email;
    private String password;
    private String confirmPassword;
    private String role;
}