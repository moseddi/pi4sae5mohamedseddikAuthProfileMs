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
@DisplayName("UserRepository Tests")
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TestEntityManager entityManager;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .email("repository@test.com")
                .password("encodedPassword")
                .role(Role.STUDENT)
                .keycloakId("keycloak-456")
                .active(true)
                .emailVerified(true)
                .build();
        entityManager.persistAndFlush(testUser);
    }

    @Test
    @DisplayName("Should find user by email - returns user")
    void findByEmail_ExistingEmail_ReturnsUser() {
        Optional<User> found = userRepository.findByEmail("repository@test.com");

        assertThat(found).isPresent();
        assertThat(found.get().getEmail()).isEqualTo("repository@test.com");
        assertThat(found.get().getRole()).isEqualTo(Role.STUDENT);
    }

    @Test
    @DisplayName("Should return empty Optional for non-existent email")
    void findByEmail_NonExistentEmail_ReturnsEmpty() {
        Optional<User> found = userRepository.findByEmail("nonexistent@test.com");

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("Should save and retrieve user correctly")
    void saveUser_ValidUser_SavesSuccessfully() {
        User newUser = User.builder()
                .email("newuser@test.com")
                .password("newPassword")
                .role(Role.TUTOR)
                .keycloakId("keycloak-new")
                .active(true)
                .emailVerified(false)
                .build();

        User savedUser = userRepository.save(newUser);

        assertThat(savedUser.getId()).isNotNull();
        assertThat(savedUser.getEmail()).isEqualTo("newuser@test.com");

        // Verify it exists in database
        Optional<User> found = userRepository.findByEmail("newuser@test.com");
        assertThat(found).isPresent();
    }
}