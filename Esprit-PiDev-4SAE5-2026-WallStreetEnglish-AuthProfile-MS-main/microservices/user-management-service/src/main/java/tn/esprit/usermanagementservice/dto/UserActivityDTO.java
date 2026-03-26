package tn.esprit.usermanagementservice.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserActivityDTO {
    private Long id;
    private String email;
    private String firstName;
    private String lastName;
    private String role;
    private String city;
    private LocalDateTime lastLogin;
    private LocalDateTime lastActivity;
    private int loginCount;
    private long daysSinceLastLogin;
    private double averageLoginsPerMonth;
}