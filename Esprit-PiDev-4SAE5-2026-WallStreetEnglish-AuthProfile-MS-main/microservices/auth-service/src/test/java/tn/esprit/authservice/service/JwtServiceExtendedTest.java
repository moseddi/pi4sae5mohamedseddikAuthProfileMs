package tn.esprit.authservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("JwtService Extended Tests")
class JwtServiceExtendedTest {

    @InjectMocks
    private JwtService jwtService;

    private final String secretKey = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";
    private final long expiration = 86400000L;

    private UserDetails userDetails;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(jwtService, "secretKey", secretKey);
        ReflectionTestUtils.setField(jwtService, "jwtExpiration", expiration);

        userDetails = User.builder()
                .username("user@test.com")
                .password("")
                .authorities(() -> "ROLE_STUDENT")
                .build();
    }

    @Nested
    @DisplayName("generateToken - variants")
    class GenerateTokenVariants {

        @Test
        @DisplayName("No extra claims - token is non-null and non-empty")
        void generateToken_NoExtraClaims_ReturnsToken() {
            String token = jwtService.generateToken(userDetails);
            assertNotNull(token);
            assertFalse(token.isBlank());
        }

        @Test
        @DisplayName("With role claim - extractRole returns correct value")
        void generateToken_WithRoleClaim_ExtractsRole() {
            Map<String, Object> claims = new HashMap<>();
            claims.put("role", "TUTOR");
            String token = jwtService.generateToken(claims, userDetails);
            assertEquals("TUTOR", jwtService.extractRole(token));
        }

        @Test
        @DisplayName("With userId claim - extractUserId returns correct value")
        void generateToken_WithUserIdClaim_ExtractsUserId() {
            Map<String, Object> claims = new HashMap<>();
            claims.put("userId", 42L);
            String token = jwtService.generateToken(claims, userDetails);
            assertEquals(42L, jwtService.extractUserId(token));
        }

        @Test
        @DisplayName("With multiple extra claims - all extracted correctly")
        void generateToken_WithMultipleClaims_AllExtracted() {
            Map<String, Object> claims = new HashMap<>();
            claims.put("role", "ADMIN");
            claims.put("userId", 99L);
            String token = jwtService.generateToken(claims, userDetails);
            assertEquals("ADMIN", jwtService.extractRole(token));
            assertEquals(99L, jwtService.extractUserId(token));
        }

        @Test
        @DisplayName("Empty claims map - token still valid")
        void generateToken_EmptyClaimsMap_TokenIsValid() {
            String token = jwtService.generateToken(new HashMap<>(), userDetails);
            assertTrue(jwtService.isTokenValid(token, userDetails));
        }

        @Test
        @DisplayName("Two tokens for same user are both valid")
        void generateToken_CalledTwice_BothValid() {
            String t1 = jwtService.generateToken(userDetails);
            String t2 = jwtService.generateToken(userDetails);
            assertTrue(jwtService.isTokenValid(t1, userDetails));
            assertTrue(jwtService.isTokenValid(t2, userDetails));
        }
    }

    @Nested
    @DisplayName("extractUsername")
    class ExtractUsernameTests {

        @Test
        @DisplayName("Returns correct username from token")
        void extractUsername_ReturnsCorrectEmail() {
            String token = jwtService.generateToken(userDetails);
            assertEquals("user@test.com", jwtService.extractUsername(token));
        }
    }

    @Nested
    @DisplayName("isTokenValid")
    class IsTokenValidTests {

        @Test
        @DisplayName("Valid token for correct user returns true")
        void isTokenValid_CorrectUser_ReturnsTrue() {
            String token = jwtService.generateToken(userDetails);
            assertTrue(jwtService.isTokenValid(token, userDetails));
        }

        @Test
        @DisplayName("Valid token for wrong user returns false")
        void isTokenValid_WrongUser_ReturnsFalse() {
            String token = jwtService.generateToken(userDetails);
            UserDetails other = User.builder()
                    .username("other@test.com")
                    .password("")
                    .authorities(() -> "ROLE_STUDENT")
                    .build();
            assertFalse(jwtService.isTokenValid(token, other));
        }

        @Test
        @DisplayName("Expired token throws ExpiredJwtException")
        void isTokenValid_ExpiredToken_ThrowsExpiredJwtException() {
            // Set expiration to 1ms so token expires almost immediately
            ReflectionTestUtils.setField(jwtService, "jwtExpiration", 1L);
            String token = jwtService.generateToken(userDetails);

            // Wait long enough for the token to expire
            try { Thread.sleep(100); } catch (InterruptedException ignored) {}

            // JwtService does not swallow ExpiredJwtException — it propagates.
            // assertThrows is the correct and honest test here.
            assertThrows(io.jsonwebtoken.ExpiredJwtException.class,
                    () -> jwtService.isTokenValid(token, userDetails));

            // Restore normal expiration for subsequent tests
            ReflectionTestUtils.setField(jwtService, "jwtExpiration", expiration);
        }
    }

    @Nested
    @DisplayName("extractRole - edge cases")
    class ExtractRoleTests {

        @Test
        @DisplayName("No role claim returns null")
        void extractRole_NoRoleClaim_ReturnsNull() {
            String token = jwtService.generateToken(userDetails);
            assertNull(jwtService.extractRole(token));
        }
    }

    @Nested
    @DisplayName("extractUserId - edge cases")
    class ExtractUserIdTests {

        @Test
        @DisplayName("No userId claim returns null")
        void extractUserId_NoUserIdClaim_ReturnsNull() {
            String token = jwtService.generateToken(userDetails);
            assertNull(jwtService.extractUserId(token));
        }
    }
}