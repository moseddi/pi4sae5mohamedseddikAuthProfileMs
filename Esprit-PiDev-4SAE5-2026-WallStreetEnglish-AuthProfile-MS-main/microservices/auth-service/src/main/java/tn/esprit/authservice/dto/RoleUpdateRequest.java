package tn.esprit.authservice.dto;

import lombok.Data;
import tn.esprit.authservice.entity.Role;

@Data
public class RoleUpdateRequest {
    private String email;
    private Role role;
}