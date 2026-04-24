package tn.esprit.authservice.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import tn.esprit.authservice.entity.Role;
import tn.esprit.authservice.entity.User;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@DisplayName("UserRepository Additional Coverage Tests")
class UserRepositoryAdditionalTest {

    @Autowired private UserRepository userRepository;
    @Autowired private TestEntityManager entityManager;

    private User adminUser;
    private User tutorUser;

    @BeforeEach
    void setUp() {
        adminUser = User.builder()
                .email("admin@coverage.com")
                .password("hash-admin")
                .role(Role.ADMIN)
                .keycloakId("kc-admin-cov")
                .active(true)
                .emailVerified(true)
                .build();

        tutorUser = User.builder()
                .email("tutor@coverage.com")
                .password("hash-tutor")
                .role(Role.TUTOR)
                .keycloakId("kc-tutor-cov")
                .active(false)
                .emailVerified(false)
                .build();

        entityManager.persistAndFlush(adminUser);
        entityManager.persistAndFlush(tutorUser);
    }

    @Test
    @DisplayName("findByEmail returns ADMIN role correctly")
    void findByEmail_Admin_ReturnsAdminRole() {
        Optional<User> found = userRepository.findByEmail("admin@coverage.com");
        assertThat(found).isPresent();
        assertThat(found.get().getRole()).isEqualTo(Role.ADMIN);
    }

    @Test
    @DisplayName("findByKeycloakId returns tutor user")
    void findByKeycloakId_Tutor_ReturnsUser() {
        Optional<User> found = userRepository.findByKeycloakId("kc-tutor-cov");
        assertThat(found).isPresent();
        assertThat(found.get().getEmail()).isEqualTo("tutor@coverage.com");
    }

    @Test
    @DisplayName("existsByEmail returns true for admin")
    void existsByEmail_Admin_ReturnsTrue() {
        assertThat(userRepository.existsByEmail("admin@coverage.com")).isTrue();
    }

    @Test
    @DisplayName("existsByEmail returns false for unknown email")
    void existsByEmail_Unknown_ReturnsFalse() {
        assertThat(userRepository.existsByEmail("nobody@nowhere.com")).isFalse();
    }

    @Test
    @DisplayName("isEnabled false when active=false and emailVerified=false")
    void userEntity_BothFalse_IsEnabledFalse() {
        assertThat(tutorUser.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("isEnabled true when active=true and emailVerified=true")
    void userEntity_BothTrue_IsEnabledTrue() {
        assertThat(adminUser.isEnabled()).isTrue();
    }

    @Test
    @DisplayName("getUsername returns email for admin user")
    void getUsername_AdminUser_ReturnsEmail() {
        assertThat(adminUser.getUsername()).isEqualTo("admin@coverage.com");
    }

    @Test
    @DisplayName("getAuthorities returns ROLE_ADMIN for admin")
    void getAuthorities_Admin_ReturnsRoleAdmin() {
        assertThat(adminUser.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_ADMIN");
    }

    @Test
    @DisplayName("getAuthorities returns ROLE_TUTOR for tutor")
    void getAuthorities_Tutor_ReturnsRoleTutor() {
        assertThat(tutorUser.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_TUTOR");
    }

    @Test
    @DisplayName("deleteById removes user")
    void deleteById_AdminUser_NotFoundAfter() {
        userRepository.deleteById(adminUser.getId());
        entityManager.flush();
        assertThat(userRepository.findByEmail("admin@coverage.com")).isEmpty();
    }

    @Test
    @DisplayName("count increases after save")
    void save_NewUser_CountIncreases() {
        long before = userRepository.count();
        User u = User.builder()
                .email("extra@coverage.com")
                .password("h")
                .role(Role.STUDENT)
                .keycloakId("kc-extra")
                .active(true)
                .emailVerified(true)
                .build();
        userRepository.save(u);
        entityManager.flush();
        assertThat(userRepository.count()).isEqualTo(before + 1);
    }

    @Test
    @DisplayName("Account state flags always true")
    void accountStateFlags_AllTrue() {
        assertThat(adminUser.isAccountNonExpired()).isTrue();
        assertThat(adminUser.isAccountNonLocked()).isTrue();
        assertThat(adminUser.isCredentialsNonExpired()).isTrue();
    }
}