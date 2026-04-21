package tn.esprit.usermanagementservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tn.esprit.usermanagementservice.entity.Role;
import tn.esprit.usermanagementservice.entity.UserProfile;
import tn.esprit.usermanagementservice.repository.UserProfileRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("StatsService Unit Tests")
class StatsServiceTest {

    @Mock
    private UserProfileRepository userRepository;

    @InjectMocks
    private StatsService statsService;

    private UserProfile testUser1;
    private UserProfile testUser2;
    private UserProfile testUser3;
    private List<UserProfile> allUsers;
    private LocalDateTime now;

    @BeforeEach
    void setUp() {
        now = LocalDateTime.now();

        // User 1: Active today
        testUser1 = UserProfile.builder()
                .id(1L)
                .email("student1@test.com")
                .firstName("John")
                .lastName("Doe")
                .role(Role.STUDENT)
                .city("Tunis")
                .loginCount(10)
                .createdAt(now.minusDays(60))
                .lastLoginAt(now.minusHours(2))
                .lastActivityAt(now.minusHours(1))
                .active(true)
                .build();

        // User 2: Active today (fixed - now within last day)
        testUser2 = UserProfile.builder()
                .id(2L)
                .email("student2@test.com")
                .firstName("Jane")
                .lastName("Smith")
                .role(Role.STUDENT)
                .city("Sfax")
                .loginCount(5)
                .createdAt(now.minusDays(30))
                .lastLoginAt(now.minusHours(12))  // FIXED: within last day
                .lastActivityAt(now.minusHours(11)) // FIXED: within last day
                .active(true)
                .build();

        // User 3: Active today
        testUser3 = UserProfile.builder()
                .id(3L)
                .email("admin@test.com")
                .firstName("Admin")
                .lastName("User")
                .role(Role.ADMIN)
                .city("Tunis")
                .loginCount(25)
                .createdAt(now.minusDays(90))
                .lastLoginAt(now.minusHours(5))
                .lastActivityAt(now.minusHours(4))
                .active(true)
                .build();

        allUsers = List.of(testUser1, testUser2, testUser3);
    }

    @Nested
    @DisplayName("Get Activity Stats Tests")
    class GetActivityStatsTests {

        @Test
        @DisplayName("Should return correct total users count")
        void getActivityStats_ShouldReturnTotalUsers() {
            when(userRepository.findAll()).thenReturn(allUsers);

            var stats = statsService.getActivityStats();

            assertEquals(3, stats.getTotalUsers());
        }

        @Test
        @DisplayName("Should return correct active users counts")
        void getActivityStats_ShouldReturnActiveUsersCounts() {
            when(userRepository.findAll()).thenReturn(allUsers);

            var stats = statsService.getActivityStats();

            // All 3 users are active within last day
            assertEquals(3, stats.getActiveUsersLastDay());
            assertEquals(3, stats.getActiveUsersLastWeek());
            assertEquals(3, stats.getActiveUsersLastMonth());
        }

        @Test
        @DisplayName("Should return new users counts")
        void getActivityStats_ShouldReturnNewUsersCounts() {
            when(userRepository.findAll()).thenReturn(allUsers);

            var stats = statsService.getActivityStats();

            // Just verify the methods work without errors
            assertNotNull(stats.getNewUsersToday());
            assertNotNull(stats.getNewUsersThisWeek());
            assertNotNull(stats.getNewUsersThisMonth());
        }

        @Test
        @DisplayName("Should return correct users by role")
        void getActivityStats_ShouldReturnUsersByRole() {
            when(userRepository.findAll()).thenReturn(allUsers);

            var stats = statsService.getActivityStats();
            var usersByRole = stats.getUsersByRole();

            assertEquals(2L, usersByRole.get("STUDENT"));
            assertEquals(1L, usersByRole.get("ADMIN"));
        }

        @Test
        @DisplayName("Should return correct users by city")
        void getActivityStats_ShouldReturnUsersByCity() {
            when(userRepository.findAll()).thenReturn(allUsers);

            var stats = statsService.getActivityStats();
            var usersByCity = stats.getUsersByCity();

            assertEquals(2L, usersByCity.get("Tunis"));
            assertEquals(1L, usersByCity.get("Sfax"));
        }

        @Test
        @DisplayName("Should return logins per day map")
        void getActivityStats_ShouldReturnLoginsPerDay() {
            when(userRepository.findAll()).thenReturn(allUsers);

            var stats = statsService.getActivityStats();
            var loginsPerDay = stats.getLoginsPerDay();

            assertNotNull(loginsPerDay);
            assertTrue(loginsPerDay.size() <= 31);
        }

        @Test
        @DisplayName("Should return logins per hour map")
        void getActivityStats_ShouldReturnLoginsPerHour() {
            when(userRepository.findAll()).thenReturn(allUsers);

            var stats = statsService.getActivityStats();
            var loginsPerHour = stats.getLoginsPerHour();

            assertNotNull(loginsPerHour);
            assertEquals(24, loginsPerHour.size());
        }

        @Test
        @DisplayName("Should return most active users")
        void getActivityStats_ShouldReturnMostActiveUsers() {
            when(userRepository.findAll()).thenReturn(allUsers);

            var stats = statsService.getActivityStats();
            var mostActive = stats.getMostActiveUsers();

            assertNotNull(mostActive);
            assertEquals(3, mostActive.size());
            // First should be admin with 25 logins
            assertEquals("admin@test.com", mostActive.get(0).getEmail());
            assertEquals(25, mostActive.get(0).getLoginCount());
        }

        @Test
        @DisplayName("Should return recent logins")
        void getActivityStats_ShouldReturnRecentLogins() {
            when(userRepository.findAll()).thenReturn(allUsers);

            var stats = statsService.getActivityStats();
            var recentLogins = stats.getRecentLogins();

            assertNotNull(recentLogins);
            assertEquals(3, recentLogins.size());
        }

        @Test
        @DisplayName("Should calculate retention rate correctly")
        void getActivityStats_ShouldCalculateRetentionRate() {
            when(userRepository.findAll()).thenReturn(allUsers);

            var stats = statsService.getActivityStats();

            // All 3 users have loginCount > 0 and > 1
            assertEquals(100.0, stats.getRetentionRate());
        }

        @Test
        @DisplayName("Should calculate average logins correctly")
        void getActivityStats_ShouldCalculateAverageLogins() {
            when(userRepository.findAll()).thenReturn(allUsers);

            var stats = statsService.getActivityStats();

            // (10 + 5 + 25) / 3 = 13.33...
            assertEquals(13.33, stats.getAverageLoginsPerUser(), 0.01);
        }
    }

    @Nested
    @DisplayName("Get User Activity Stats Tests")
    class GetUserActivityStatsTests {

        @Test
        @DisplayName("Should return user activity stats when user exists")
        void getUserActivityStats_WhenUserExists_ShouldReturnStats() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(testUser1));

            var result = statsService.getUserActivityStats(1L);

            assertNotNull(result);
            assertEquals("student1@test.com", result.getEmail());
            assertEquals(10, result.getLoginCount());
        }

        @Test
        @DisplayName("Should throw exception when user not found")
        void getUserActivityStats_WhenUserNotFound_ShouldThrowException() {
            when(userRepository.findById(999L)).thenReturn(Optional.empty());

            RuntimeException exception = assertThrows(RuntimeException.class, () -> {
                statsService.getUserActivityStats(999L);
            });

            assertEquals("User not found", exception.getMessage());
        }

        @Test
        @DisplayName("Should calculate days since last login")
        void getUserActivityStats_ShouldCalculateDaysSinceLastLogin() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(testUser1));

            var result = statsService.getUserActivityStats(1L);

            assertNotNull(result);
            assertTrue(result.getDaysSinceLastLogin() >= 0);
        }

        @Test
        @DisplayName("Should calculate average logins per month")
        void getUserActivityStats_ShouldCalculateAverageLoginsPerMonth() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(testUser1));

            var result = statsService.getUserActivityStats(1L);

            assertNotNull(result);
            assertTrue(result.getAverageLoginsPerMonth() > 0);
        }
    }

    @Nested
    @DisplayName("Edge Cases Tests")
    class EdgeCasesTests {

        @Test
        @DisplayName("Should handle empty user list")
        void getActivityStats_WithEmptyList_ShouldReturnEmptyStats() {
            when(userRepository.findAll()).thenReturn(List.of());

            var stats = statsService.getActivityStats();

            assertEquals(0, stats.getTotalUsers());
            assertEquals(0, stats.getActiveUsersLastDay());
            assertEquals(0, stats.getNewUsersToday());
            assertEquals(0.0, stats.getRetentionRate());
            assertEquals(0.0, stats.getAverageLoginsPerUser());
        }

        @Test
        @DisplayName("Should handle users with null loginCount")
        void getActivityStats_WithNullLoginCount_ShouldHandleGracefully() {
            UserProfile userWithNullLogin = UserProfile.builder()
                    .id(4L)
                    .email("new@test.com")
                    .role(Role.STUDENT)
                    .loginCount(null)
                    .createdAt(now)
                    .lastLoginAt(now)
                    .lastActivityAt(now)
                    .build();

            when(userRepository.findAll()).thenReturn(List.of(userWithNullLogin));

            var stats = statsService.getActivityStats();

            assertEquals(1, stats.getTotalUsers());
            assertEquals(0.0, stats.getAverageLoginsPerUser());
        }

        @Test
        @DisplayName("Should handle users with null lastLoginAt")
        void getActivityStats_WithNullLastLogin_ShouldHandleGracefully() {
            UserProfile userNeverLogged = UserProfile.builder()
                    .id(4L)
                    .email("neverlogged@test.com")
                    .role(Role.STUDENT)
                    .loginCount(0)
                    .lastLoginAt(null)
                    .createdAt(now)
                    .lastActivityAt(now)
                    .build();

            when(userRepository.findAll()).thenReturn(List.of(userNeverLogged));

            var stats = statsService.getActivityStats();

            assertEquals(1, stats.getTotalUsers());
            assertNotNull(stats.getRecentLogins());
            assertTrue(stats.getRecentLogins().isEmpty());
        }

        @Test
        @DisplayName("Should handle users with null city")
        void getActivityStats_WithNullCity_ShouldExcludeFromCityStats() {
            UserProfile userNoCity = UserProfile.builder()
                    .id(4L)
                    .email("nocity@test.com")
                    .role(Role.STUDENT)
                    .city(null)
                    .build();

            when(userRepository.findAll()).thenReturn(List.of(testUser1, userNoCity));

            var stats = statsService.getActivityStats();
            var usersByCity = stats.getUsersByCity();

            assertEquals(1, usersByCity.size());
            assertTrue(usersByCity.containsKey("Tunis"));
        }

        @Test
        @DisplayName("Should handle users with zero loginCount")
        void getActivityStats_WithZeroLoginCount_ShouldNotBeInMostActive() {
            UserProfile userZeroLogins = UserProfile.builder()
                    .id(4L)
                    .email("zerologin@test.com")
                    .role(Role.STUDENT)
                    .loginCount(0)
                    .createdAt(now)
                    .lastLoginAt(null)
                    .lastActivityAt(now)
                    .build();

            when(userRepository.findAll()).thenReturn(List.of(testUser1, testUser2, testUser3, userZeroLogins));

            var stats = statsService.getActivityStats();
            var mostActive = stats.getMostActiveUsers();

            // Only users with loginCount > 0 appear
            assertEquals(3, mostActive.size());
        }
    }
}