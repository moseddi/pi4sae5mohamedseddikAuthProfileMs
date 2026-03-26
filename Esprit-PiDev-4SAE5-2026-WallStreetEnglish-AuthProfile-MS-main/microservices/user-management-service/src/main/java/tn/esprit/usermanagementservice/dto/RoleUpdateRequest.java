package tn.esprit.usermanagementservice.dto;

import lombok.Data;
import tn.esprit.usermanagementservice.entity.Role;

@Data
public class RoleUpdateRequest {
    private String email;
    private Role role;
}