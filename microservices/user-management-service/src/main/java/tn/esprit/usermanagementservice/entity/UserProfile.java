package tn.esprit.usermanagementservice.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_profiles")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String firstName;

    private String lastName;

    private String phoneNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    private LocalDateTime dateOfBirth;

    private String address;

    private String city;

    private String country;

    // Tracking comme vous vouliez
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @Column(name = "account_created_at")
    private LocalDateTime accountCreatedAt;

    @Column(name = "last_activity_at")
    private LocalDateTime lastActivityAt;

    private boolean active = true;

    private Integer loginCount = 0;

    private String createdBy;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (accountCreatedAt == null) {
            accountCreatedAt = LocalDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public void recordLogin() {
        lastLoginAt = LocalDateTime.now();
        lastActivityAt = LocalDateTime.now();
        loginCount = (loginCount == null) ? 1 : loginCount + 1;
    }

    public void recordActivity() {
        lastActivityAt = LocalDateTime.now();
    }
}