package tn.esprit.usermanagementservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tn.esprit.usermanagementservice.entity.LoginHistory;
import tn.esprit.usermanagementservice.entity.Role;
import tn.esprit.usermanagementservice.entity.UserProfile;
import tn.esprit.usermanagementservice.repository.LoginHistoryRepository;
import tn.esprit.usermanagementservice.repository.UserProfileRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("LoginHistoryService Tests")
class LoginHistoryServiceTest {

    @Mock
    private LoginHistoryRepository loginHistoryRepository;

    @Mock
    private UserProfileRepository userProfileRepository;

    @InjectMocks
    private LoginHistoryService loginHistoryService;

    private LoginHistory testLoginHistory;
    private UserProfile testUser;

    @BeforeEach
    void setUp() {
        testUser = UserProfile.builder()
                .id(1L)
                .email("test@test.com")
                .firstName("John")
                .lastName("Doe")
                .role(Role.STUDENT)
                .build();

        testLoginHistory = new LoginHistory();
        testLoginHistory.setEmail("test@test.com");
        testLoginHistory.setMessage("LOGIN: test@test.com from 127.0.0.1");
        testLoginHistory.setLoginTime(LocalDateTime.now());
        testLoginHistory.setSuspicious(false);
        testLoginHistory.setBrowser("Chrome");
        testLoginHistory.setOs("Windows");
        testLoginHistory.setDeviceType("Desktop");
    }

    @Nested
    @DisplayName("getRecentLogins Tests")
    class GetRecentLoginsTests {

        @Test
        @DisplayName("Should return recent logins with user details")
        void getRecentLogins_WhenHistoryExists_ShouldReturnFormattedList() {
            when(loginHistoryRepository.findTop20ByOrderByLoginTimeDesc())
                    .thenReturn(List.of(testLoginHistory));
            when(userProfileRepository.findByEmail("test@test.com"))
                    .thenReturn(Optional.of(testUser));

            List<Map<String, Object>> result = loginHistoryService.getRecentLogins();

            assertThat(result).isNotEmpty();
            assertThat(result).hasSize(1);

            Map<String, Object> firstResult = result.get(0);
            assertThat(firstResult.get("email")).isEqualTo("test@test.com");
            assertThat(firstResult.get("role")).isEqualTo("STUDENT");
            assertThat(firstResult.get("firstName")).isEqualTo("John");
            assertThat(firstResult.get("lastName")).isEqualTo("Doe");
            assertThat(firstResult.get("browser")).isEqualTo("Chrome");
        }

        @Test
        @DisplayName("Should handle user not found in profile repository")
        void getRecentLogins_WhenUserNotFound_ShouldUseDefaults() {
            when(loginHistoryRepository.findTop20ByOrderByLoginTimeDesc())
                    .thenReturn(List.of(testLoginHistory));
            when(userProfileRepository.findByEmail("test@test.com"))
                    .thenReturn(Optional.empty());

            List<Map<String, Object>> result = loginHistoryService.getRecentLogins();

            assertThat(result).isNotEmpty();

            Map<String, Object> firstResult = result.get(0);
            assertThat(firstResult.get("email")).isEqualTo("test@test.com");
            assertThat(firstResult.get("role")).isEqualTo("STUDENT"); // default
            assertThat(firstResult.get("firstName")).isEqualTo("");
            assertThat(firstResult.get("lastName")).isEqualTo("");
        }

        @Test
        @DisplayName("Should return empty list when no login history")
        void getRecentLogins_WhenNoHistory_ShouldReturnEmptyList() {
            when(loginHistoryRepository.findTop20ByOrderByLoginTimeDesc())
                    .thenReturn(List.of());

            List<Map<String, Object>> result = loginHistoryService.getRecentLogins();

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should handle null values in login history")
        void getRecentLogins_WithNullValues_ShouldHandleGracefully() {
            LoginHistory historyWithNulls = new LoginHistory();
            historyWithNulls.setEmail("test@test.com");
            historyWithNulls.setLoginTime(null);
            historyWithNulls.setBrowser(null);
            historyWithNulls.setOs(null);
            historyWithNulls.setDeviceType(null);

            when(loginHistoryRepository.findTop20ByOrderByLoginTimeDesc())
                    .thenReturn(List.of(historyWithNulls));
            when(userProfileRepository.findByEmail("test@test.com"))
                    .thenReturn(Optional.of(testUser));

            List<Map<String, Object>> result = loginHistoryService.getRecentLogins();

            assertThat(result).isNotEmpty();

            Map<String, Object> firstResult = result.get(0);
            assertThat(firstResult.get("loginTime")).isEqualTo("");
            assertThat(firstResult.get("browser")).isNull();
        }

        @Test
        @DisplayName("Should handle suspicious login flag")
        void getRecentLogins_WithSuspiciousLogin_ShouldIncludeFlag() {
            testLoginHistory.setSuspicious(true);
            testLoginHistory.setSuspiciousReason("Multiple country changes");

            when(loginHistoryRepository.findTop20ByOrderByLoginTimeDesc())
                    .thenReturn(List.of(testLoginHistory));
            when(userProfileRepository.findByEmail("test@test.com"))
                    .thenReturn(Optional.of(testUser));

            List<Map<String, Object>> result = loginHistoryService.getRecentLogins();

            assertThat(result).isNotEmpty();

            Map<String, Object> firstResult = result.get(0);
            assertThat(firstResult.get("suspicious")).isEqualTo(true);
            assertThat(firstResult.get("suspiciousReason")).isEqualTo("Multiple country changes");
        }
    }
}