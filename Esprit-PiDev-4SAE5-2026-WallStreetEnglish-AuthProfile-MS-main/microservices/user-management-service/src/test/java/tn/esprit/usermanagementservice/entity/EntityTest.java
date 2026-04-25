package tn.esprit.usermanagementservice.entity;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class EntityTest {

    // ══════════════════════════════════════════════════════════════════════
    //  UserProfile
    // ══════════════════════════════════════════════════════════════════════
    @Nested
    class UserProfileTest {

        // ── recordLogin() ─────────────────────────────────────────────────

        @Test
        void recordLogin_nullLoginCount_setsCountToOne() {
            UserProfile user = new UserProfile();
            user.setLoginCount(null);

            user.recordLogin();

            assertThat(user.getLoginCount()).isEqualTo(1);
            assertThat(user.getLastLoginAt()).isNotNull();
            assertThat(user.getLastActivityAt()).isNotNull();
        }

        @Test
        void recordLogin_existingLoginCount_incrementsByOne() {
            UserProfile user = new UserProfile();
            user.setLoginCount(5);

            user.recordLogin();

            assertThat(user.getLoginCount()).isEqualTo(6);
        }

        @Test
        void recordLogin_zeroLoginCount_incrementsToOne() {
            UserProfile user = new UserProfile();
            user.setLoginCount(0);

            user.recordLogin();

            assertThat(user.getLoginCount()).isEqualTo(1);
        }

        @Test
        void recordLogin_updatesLastLoginAtAndLastActivityAt() {
            UserProfile user = new UserProfile();
            user.setLoginCount(1);
            LocalDateTime before = LocalDateTime.now().minusSeconds(1);

            user.recordLogin();

            assertThat(user.getLastLoginAt()).isAfter(before);
            assertThat(user.getLastActivityAt()).isAfter(before);
        }

        // ── recordActivity() ──────────────────────────────────────────────

        @Test
        void recordActivity_updatesLastActivityAt() {
            UserProfile user = new UserProfile();
            LocalDateTime before = LocalDateTime.now().minusSeconds(1);

            user.recordActivity();

            assertThat(user.getLastActivityAt()).isAfter(before);
        }

        // ── @PrePersist onCreate() ────────────────────────────────────────

        @Test
        void onCreate_accountCreatedAtNull_setsIt() {
            UserProfile user = new UserProfile();
            user.setAccountCreatedAt(null);

            // invoke directly since JPA lifecycle callbacks aren't triggered in unit tests
            user.onCreate();

            assertThat(user.getCreatedAt()).isNotNull();
            assertThat(user.getUpdatedAt()).isNotNull();
            assertThat(user.getAccountCreatedAt()).isNotNull();
        }

        @Test
        void onCreate_accountCreatedAtAlreadySet_doesNotOverwrite() {
            UserProfile user = new UserProfile();
            LocalDateTime existing = LocalDateTime.of(2020, 1, 1, 0, 0);
            user.setAccountCreatedAt(existing);

            user.onCreate();

            // accountCreatedAt should remain unchanged
            assertThat(user.getAccountCreatedAt()).isEqualTo(existing);
        }

        // ── @PreUpdate onUpdate() ─────────────────────────────────────────

        @Test
        void onUpdate_setsUpdatedAt() {
            UserProfile user = new UserProfile();
            LocalDateTime before = LocalDateTime.now().minusSeconds(1);

            user.onUpdate();

            assertThat(user.getUpdatedAt()).isAfter(before);
        }

        // ── Builder & field defaults ──────────────────────────────────────

        @Test
        void builder_defaultValues() {
            UserProfile user = UserProfile.builder()
                    .email("test@test.com")
                    .firstName("John")
                    .role(Role.STUDENT)
                    .build();

            assertThat(user.getEmail()).isEqualTo("test@test.com");
            assertThat(user.getRole()).isEqualTo(Role.STUDENT);
        }

        @Test
        void blockedFields_defaultFalse() {
            UserProfile user = new UserProfile();

            assertThat(user.isBlocked()).isFalse();
            assertThat(user.getBlockedAt()).isNull();
            assertThat(user.getBlockedReason()).isNull();
        }

        @Test
        void reactivationToken_setAndGet() {
            UserProfile user = new UserProfile();
            LocalDateTime expiry = LocalDateTime.now().plusHours(24);
            user.setReactivationToken("token-abc");
            user.setReactivationTokenExpiry(expiry);

            assertThat(user.getReactivationToken()).isEqualTo("token-abc");
            assertThat(user.getReactivationTokenExpiry()).isEqualTo(expiry);
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  ProfileChangeHistory
    // ══════════════════════════════════════════════════════════════════════
    @Nested
    class ProfileChangeHistoryTest {

        @Test
        void onCreate_setsChangedAt() {
            ProfileChangeHistory history = new ProfileChangeHistory();
            assertThat(history.getChangedAt()).isNull();

            history.onCreate();

            assertThat(history.getChangedAt()).isNotNull();
            assertThat(history.getChangedAt()).isBeforeOrEqualTo(LocalDateTime.now());
        }

        @Test
        void builder_allFields() {
            LocalDateTime now = LocalDateTime.now();
            ProfileChangeHistory history = ProfileChangeHistory.builder()
                    .id(1L)
                    .email("user@test.com")
                    .fieldChanged("country")
                    .oldValue("France")
                    .newValue("Germany")
                    .changedAt(now)
                    .build();

            assertThat(history.getEmail()).isEqualTo("user@test.com");
            assertThat(history.getFieldChanged()).isEqualTo("country");
            assertThat(history.getOldValue()).isEqualTo("France");
            assertThat(history.getNewValue()).isEqualTo("Germany");
            assertThat(history.getChangedAt()).isEqualTo(now);
        }

        @Test
        void noArgsConstructor_fieldsAreNull() {
            ProfileChangeHistory history = new ProfileChangeHistory();

            assertThat(history.getEmail()).isNull();
            assertThat(history.getFieldChanged()).isNull();
            assertThat(history.getOldValue()).isNull();
            assertThat(history.getNewValue()).isNull();
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  LoginHistory
    // ══════════════════════════════════════════════════════════════════════
    @Nested
    class LoginHistoryTest {

        @Test
        void setAndGet_allFields() {
            LoginHistory lh = new LoginHistory();
            LocalDateTime now = LocalDateTime.now();

            lh.setId(1L);
            lh.setEmail("user@test.com");
            lh.setRole("STUDENT");
            lh.setMessage("User logged in");
            lh.setType(LoginHistory.EventType.LOGIN);
            lh.setLoginTime(now);
            lh.setActive(true);
            lh.setSuspicious(false);
            lh.setIpAddress("127.0.0.1");
            lh.setBrowser("Chrome");
            lh.setOs("Windows");
            lh.setDeviceType("Desktop");

            assertThat(lh.getId()).isEqualTo(1L);
            assertThat(lh.getEmail()).isEqualTo("user@test.com");
            assertThat(lh.getRole()).isEqualTo("STUDENT");
            assertThat(lh.getMessage()).isEqualTo("User logged in");
            assertThat(lh.getType()).isEqualTo(LoginHistory.EventType.LOGIN);
            assertThat(lh.getLoginTime()).isEqualTo(now);
            assertThat(lh.isActive()).isTrue();
            assertThat(lh.isSuspicious()).isFalse();
            assertThat(lh.getIpAddress()).isEqualTo("127.0.0.1");
            assertThat(lh.getBrowser()).isEqualTo("Chrome");
            assertThat(lh.getOs()).isEqualTo("Windows");
            assertThat(lh.getDeviceType()).isEqualTo("Desktop");
        }

        @Test
        void logoutFields_setAndGet() {
            LoginHistory lh = new LoginHistory();
            LocalDateTime now = LocalDateTime.now();

            lh.setLogoutTime(now);
            lh.setLogoutType(LoginHistory.LogoutType.VOLUNTARY);
            lh.setSessionDurationSeconds(3600L);
            lh.setSessionId("session-xyz");

            assertThat(lh.getLogoutTime()).isEqualTo(now);
            assertThat(lh.getLogoutType()).isEqualTo(LoginHistory.LogoutType.VOLUNTARY);
            assertThat(lh.getSessionDurationSeconds()).isEqualTo(3600L);
            assertThat(lh.getSessionId()).isEqualTo("session-xyz");
        }

        @Test
        void suspiciousFields_setAndGet() {
            LoginHistory lh = new LoginHistory();
            lh.setSuspicious(true);
            lh.setSuspiciousReason("Multiple country changes");

            assertThat(lh.isSuspicious()).isTrue();
            assertThat(lh.getSuspiciousReason()).isEqualTo("Multiple country changes");
        }

        @Test
        void eventType_allValues() {
            assertThat(LoginHistory.EventType.values())
                    .containsExactlyInAnyOrder(
                            LoginHistory.EventType.LOGIN,
                            LoginHistory.EventType.LOGOUT);
        }

        @Test
        void logoutType_allValues() {
            assertThat(LoginHistory.LogoutType.values())
                    .containsExactlyInAnyOrder(
                            LoginHistory.LogoutType.VOLUNTARY,
                            LoginHistory.LogoutType.TIMEOUT,
                            LoginHistory.LogoutType.FORCED);
        }

        @Test
        void logoutType_forced_setAndGet() {
            LoginHistory lh = new LoginHistory();
            lh.setLogoutType(LoginHistory.LogoutType.FORCED);
            assertThat(lh.getLogoutType()).isEqualTo(LoginHistory.LogoutType.FORCED);
        }

        @Test
        void logoutType_timeout_setAndGet() {
            LoginHistory lh = new LoginHistory();
            lh.setLogoutType(LoginHistory.LogoutType.TIMEOUT);
            assertThat(lh.getLogoutType()).isEqualTo(LoginHistory.LogoutType.TIMEOUT);
        }
    }
}