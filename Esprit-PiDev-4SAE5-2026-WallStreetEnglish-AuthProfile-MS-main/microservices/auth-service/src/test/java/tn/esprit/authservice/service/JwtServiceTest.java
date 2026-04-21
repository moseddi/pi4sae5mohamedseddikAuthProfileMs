package tn.esprit.authservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;
import tn.esprit.authservice.entity.Role;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("JwtService Unit Tests")
class JwtServiceTest {

    @InjectMocks
    private JwtService jwtService;

    private final String secretKey = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";
    private final long expiration = 86400000; // 24 hours

    private UserDetails userDetails;
    private tn.esprit.authservice.entity.User testUser;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(jwtService, "secretKey", secretKey);
        ReflectionTestUtils.setField(jwtService, "jwtExpiration", expiration);

        userDetails = User.builder()
                .username("student@test.com")
                .password("")
                .authorities(() -> "ROLE_STUDENT")
                .build();

        testUser = tn.esprit.authservice.entity.User.builder()
                .id(1L)
                .email("student@test.com")
                .role(Role.STUDENT)
                .build();
    }

    @Test
    @DisplayName("Should generate valid token")
    void generateToken_ShouldReturnValidToken() {
        // Act
        String token = jwtService.generateToken(userDetails);

        // Assert
        assertNotNull(token);
        assertFalse(token.isEmpty());
    }

    @Test
    @DisplayName("Should extract username from token")
    void extractUsername_ShouldReturnCorrectUsername() {
        // Arrange
        String token = jwtService.generateToken(userDetails);

        // Act
        String username = jwtService.extractUsername(token);

        // Assert
        assertEquals("student@test.com", username);
    }

    @Test
    @DisplayName("Should validate correct token")
    void isTokenValid_ValidToken_ShouldReturnTrue() {
        // Arrange
        String token = jwtService.generateToken(userDetails);

        // Act
        boolean isValid = jwtService.isTokenValid(token, userDetails);

        // Assert
        assertTrue(isValid);
    }

    @Test
    @DisplayName("Should reject token for different user")
    void isTokenValid_DifferentUser_ShouldReturnFalse() {
        // Arrange
        String token = jwtService.generateToken(userDetails);

        UserDetails differentUser = User.builder()
                .username("different@test.com")
                .password("")
                .authorities(() -> "ROLE_STUDENT")
                .build();

        // Act
        boolean isValid = jwtService.isTokenValid(token, differentUser);

        // Assert
        assertFalse(isValid);
    }

    @Test
    @DisplayName("Should generate token with extra claims")
    void generateToken_WithExtraClaims_ShouldIncludeClaims() {
        // Arrange
        Map<String, Object> extraClaims = new HashMap<>();
        extraClaims.put("userId", 1L);
        extraClaims.put("role", "STUDENT");

        // Act
        String token = jwtService.generateToken(extraClaims, userDetails);

        // Assert
        Long userId = jwtService.extractUserId(token);
        String role = jwtService.extractRole(token);

        assertEquals(1L, userId);
        assertEquals("STUDENT", role);
    }
}