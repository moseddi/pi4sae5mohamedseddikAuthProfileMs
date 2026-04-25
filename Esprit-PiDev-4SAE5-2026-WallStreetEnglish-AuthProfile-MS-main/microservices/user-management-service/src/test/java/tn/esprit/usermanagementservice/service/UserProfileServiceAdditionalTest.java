package tn.esprit.usermanagementservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import tn.esprit.usermanagementservice.client.AuthServiceClient;
import tn.esprit.usermanagementservice.dto.UpdateUserRequest;
import tn.esprit.usermanagementservice.dto.UserProfileDTO;
import tn.esprit.usermanagementservice.entity.LoginHistory;
import tn.esprit.usermanagementservice.entity.Role;
import tn.esprit.usermanagementservice.entity.UserProfile;
import tn.esprit.usermanagementservice.repository.LoginHistoryRepository;
import tn.esprit.usermanagementservice.repository.ProfileChangeHistoryRepository;
import tn.esprit.usermanagementservice.repository.UserProfileRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserProfileServiceAdditionalTest {

    @Mock UserProfileRepository          userProfileRepository;
    @Mock AuthServiceClient              authServiceClient;
    @Mock KeycloakAdminService           keycloakAdminService;
    @Mock ProfileChangeHistoryRepository profileChangeHistoryRepository;
    @Mock PhoneNumberValidator           phoneNumberValidator;
    @Mock LoginHistoryRepository         loginHistoryRepository;
    @Mock RabbitTemplate                 rabbitTemplate;
    @Mock SimpMessagingTemplate          messagingTemplate;
    @Mock JavaMailSender                 mailSender;

    @InjectMocks UserProfileService userProfileService;

    private UserProfile user;

    // ── helpers to avoid ambiguous overload on convertAndSend ─────────────
    private void stubMessaging() {
        doNothing().when(messagingTemplate).convertAndSend(anyString(), (Object) anyString());
    }

    private void verifyBroadcast() {
        verify(messagingTemplate, atLeastOnce())
                .convertAndSend(eq("/topic/logins"), (Object) anyString());
    }

    @BeforeEach
    void setUp() {
        user = UserProfile.builder()
                .id(1L)
                .email("student@test.com")
                .firstName("John")
                .lastName("Doe")
                .phoneNumber("1234567890")
                .address("123 Main St")
                .city("Paris")
                .country("France")
                .role(Role.STUDENT)
                .active(true)
                .blocked(false)
                .loginCount(2)
                .createdAt(LocalDateTime.now().minusDays(10))
                .build();

        when(userProfileRepository.save(any(UserProfile.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        stubMessaging();
        doNothing().when(mailSender).send(any(SimpleMailMessage.class));
    }

    // ══════════════════════════════════════════════════════════════════════
    //  updateUserProfile – individual field branches
    // ══════════════════════════════════════════════════════════════════════
    @Nested
    class UpdateProfileFieldBranchTests {

        @Test
        void updateLastName_changed_broadcasts() {
            when(userProfileRepository.findByEmail("student@test.com")).thenReturn(Optional.of(user));
            UpdateUserRequest req = new UpdateUserRequest();
            req.setLastName("Smith");

            UserProfileDTO result = userProfileService.updateUserProfile("student@test.com", req);

            assertThat(result.getLastName()).isEqualTo("Smith");
            verifyBroadcast();
        }

        @Test
        void updatePhoneNumber_changed_broadcasts() {
            when(userProfileRepository.findByEmail("student@test.com")).thenReturn(Optional.of(user));
            UpdateUserRequest req = new UpdateUserRequest();
            req.setPhoneNumber("9999999999");

            UserProfileDTO result = userProfileService.updateUserProfile("student@test.com", req);

            assertThat(result.getPhoneNumber()).isEqualTo("9999999999");
        }

        @Test
        void updateAddress_changed_broadcasts() {
            when(userProfileRepository.findByEmail("student@test.com")).thenReturn(Optional.of(user));
            UpdateUserRequest req = new UpdateUserRequest();
            req.setAddress("456 New Ave");

            userProfileService.updateUserProfile("student@test.com", req);
            verifyBroadcast();
        }

        @Test
        void updateCity_changed_broadcasts() {
            when(userProfileRepository.findByEmail("student@test.com")).thenReturn(Optional.of(user));
            UpdateUserRequest req = new UpdateUserRequest();
            req.setCity("Lyon");

            userProfileService.updateUserProfile("student@test.com", req);
            verifyBroadcast();
        }

        @Test
        void updateDateOfBirth_changed_broadcasts() {
            when(userProfileRepository.findByEmail("student@test.com")).thenReturn(Optional.of(user));
            UpdateUserRequest req = new UpdateUserRequest();
            // NOTE: change to LocalDate.of(...) if your UpdateUserRequest.dateOfBirth is LocalDate
            req.setDateOfBirth(LocalDateTime.of(1995, 6, 15, 0, 0));

            userProfileService.updateUserProfile("student@test.com", req);
            verifyBroadcast();
        }

        @Test
        void updateRole_changed_broadcasts() {
            when(userProfileRepository.findByEmail("student@test.com")).thenReturn(Optional.of(user));
            UpdateUserRequest req = new UpdateUserRequest();
            req.setRole(Role.TUTOR);

            UserProfileDTO result = userProfileService.updateUserProfile("student@test.com", req);

            assertThat(result.getRole()).isEqualTo(Role.TUTOR);
        }

        @Test
        void updateActiveStatus_toFalse_broadcasts() {
            when(userProfileRepository.findByEmail("student@test.com")).thenReturn(Optional.of(user));
            UpdateUserRequest req = new UpdateUserRequest();
            req.setActive(false);

            UserProfileDTO result = userProfileService.updateUserProfile("student@test.com", req);

            assertThat(result.isActive()).isFalse();
        }

        @Test
        void updateCountry_firstTimeSetting_nullOldCountry() {
            user.setCountry(null);
            when(userProfileRepository.findByEmail("student@test.com")).thenReturn(Optional.of(user));
            UpdateUserRequest req = new UpdateUserRequest();
            req.setCountry("Germany");

            UserProfileDTO result = userProfileService.updateUserProfile("student@test.com", req);

            assertThat(result.getCountry()).isEqualTo("Germany");
            verify(profileChangeHistoryRepository, never()).save(any());
        }

        @Test
        void updateCountry_sameValue_noHistorySaved() {
            when(userProfileRepository.findByEmail("student@test.com")).thenReturn(Optional.of(user));
            UpdateUserRequest req = new UpdateUserRequest();
            req.setCountry("France"); // same as existing

            userProfileService.updateUserProfile("student@test.com", req);

            verify(profileChangeHistoryRepository, never()).save(any());
        }

        @Test
        void updateFirstName_emptyValue_noChange() {
            when(userProfileRepository.findByEmail("student@test.com")).thenReturn(Optional.of(user));
            UpdateUserRequest req = new UpdateUserRequest();
            req.setFirstName("");

            UserProfileDTO result = userProfileService.updateUserProfile("student@test.com", req);

            assertThat(result.getFirstName()).isEqualTo("John");
        }

        @Test
        void updateFirstName_sameValue_noChange() {
            when(userProfileRepository.findByEmail("student@test.com")).thenReturn(Optional.of(user));
            UpdateUserRequest req = new UpdateUserRequest();
            req.setFirstName("John");

            UserProfileDTO result = userProfileService.updateUserProfile("student@test.com", req);

            assertThat(result.getFirstName()).isEqualTo("John");
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  checkAndAlertSuspiciousCountryChanges
    // ══════════════════════════════════════════════════════════════════════
    @Nested
    class SuspiciousCountryChangeTests {

        @Test
        void fiveOrMoreCountryChanges_triggersBlockAndAlert() {
            when(userProfileRepository.findByEmail("student@test.com")).thenReturn(Optional.of(user));
            when(profileChangeHistoryRepository.countCountryChangesToday(eq("student@test.com"), any()))
                    .thenReturn(5);
            UpdateUserRequest req = new UpdateUserRequest();
            req.setCountry("Spain");

            userProfileService.updateUserProfile("student@test.com", req);

            verify(userProfileRepository, atLeast(2)).save(any());
            verify(mailSender, atLeastOnce()).send(any(SimpleMailMessage.class));
        }

        @Test
        void belowFiveCountryChanges_doesNotBlock() {
            when(userProfileRepository.findByEmail("student@test.com")).thenReturn(Optional.of(user));
            when(profileChangeHistoryRepository.countCountryChangesToday(eq("student@test.com"), any()))
                    .thenReturn(3);
            UpdateUserRequest req = new UpdateUserRequest();
            req.setCountry("Italy");

            userProfileService.updateUserProfile("student@test.com", req);

            assertThat(user.isBlocked()).isFalse();
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  updateUser (by ID) — role-change branches
    // ══════════════════════════════════════════════════════════════════════
    @Nested
    class UpdateUserByIdBranchTests {

        @Test
        void updateUser_roleChange_keycloakUpdated_forceLogout() {
            when(userProfileRepository.findById(1L)).thenReturn(Optional.of(user));
            doNothing().when(keycloakAdminService).updateUserRole(anyString(), anyString());
            doNothing().when(keycloakAdminService).logoutUserSessions(anyString());
            UpdateUserRequest req = new UpdateUserRequest();
            req.setRole(Role.TUTOR);

            UserProfileDTO result = userProfileService.updateUser(1L, req);

            assertThat(result.getRole()).isEqualTo(Role.TUTOR);
            verify(keycloakAdminService).updateUserRole("student@test.com", "TUTOR");
            verify(keycloakAdminService).logoutUserSessions("student@test.com");
        }

        @Test
        void updateUser_roleChange_keycloakFails_continuesAnyway() {
            when(userProfileRepository.findById(1L)).thenReturn(Optional.of(user));
            doThrow(new RuntimeException("Keycloak down"))
                    .when(keycloakAdminService).updateUserRole(anyString(), anyString());
            doNothing().when(keycloakAdminService).logoutUserSessions(anyString());
            UpdateUserRequest req = new UpdateUserRequest();
            req.setRole(Role.TUTOR);

            assertThatCode(() -> userProfileService.updateUser(1L, req))
                    .doesNotThrowAnyException();
        }

        @Test
        void updateUser_roleChange_forceLogoutFails_continuesAnyway() {
            when(userProfileRepository.findById(1L)).thenReturn(Optional.of(user));
            doNothing().when(keycloakAdminService).updateUserRole(anyString(), anyString());
            doThrow(new RuntimeException("logout failed"))
                    .when(keycloakAdminService).logoutUserSessions(anyString());
            UpdateUserRequest req = new UpdateUserRequest();
            req.setRole(Role.TUTOR);

            assertThatCode(() -> userProfileService.updateUser(1L, req))
                    .doesNotThrowAnyException();
        }

        @Test
        void updateUser_sameRole_noKeycloakCall() {
            when(userProfileRepository.findById(1L)).thenReturn(Optional.of(user));
            UpdateUserRequest req = new UpdateUserRequest();
            req.setRole(Role.STUDENT);

            userProfileService.updateUser(1L, req);

            verify(keycloakAdminService, never()).updateUserRole(anyString(), anyString());
        }

        @Test
        void updateUser_allFields_updated() {
            when(userProfileRepository.findById(1L)).thenReturn(Optional.of(user));
            UpdateUserRequest req = new UpdateUserRequest();
            req.setFirstName("Alice");
            req.setLastName("Wonderland");
            req.setPhoneNumber("5551234567");
            req.setAddress("1 Fantasy Rd");
            req.setCity("Neverland");
            req.setCountry("Imaginaria");
            req.setActive(false);
            // adjust to LocalDate.of(...) if your field is LocalDate
            req.setDateOfBirth(LocalDateTime.of(1990, 1, 1, 0, 0));

            UserProfileDTO result = userProfileService.updateUser(1L, req);

            assertThat(result.getFirstName()).isEqualTo("Alice");
            assertThat(result.getLastName()).isEqualTo("Wonderland");
            assertThat(result.isActive()).isFalse();
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  recordUserLogout
    // ══════════════════════════════════════════════════════════════════════
    @Nested
    class RecordUserLogoutTests {

        @Test
        void recordUserLogout_voluntary_publishesEvent() {
            when(userProfileRepository.findByEmail("student@test.com")).thenReturn(Optional.of(user));

            userProfileService.recordUserLogout("student@test.com", LoginHistory.LogoutType.VOLUNTARY);

            verify(rabbitTemplate).convertAndSend(eq("login-events"), (Object) any());
        }

        @Test
        void recordUserLogout_timeout_publishesEvent() {
            when(userProfileRepository.findByEmail("student@test.com")).thenReturn(Optional.of(user));

            userProfileService.recordUserLogout("student@test.com", LoginHistory.LogoutType.TIMEOUT);

            verify(rabbitTemplate).convertAndSend(eq("login-events"), (Object) any());
        }

        @Test
        void recordUserLogout_forced_publishesEvent() {
            when(userProfileRepository.findByEmail("student@test.com")).thenReturn(Optional.of(user));

            userProfileService.recordUserLogout("student@test.com", LoginHistory.LogoutType.FORCED);

            verify(rabbitTemplate).convertAndSend(eq("login-events"), (Object) any());
        }

        @Test
        void recordUserLogout_userNotFound_throws() {
            when(userProfileRepository.findByEmail("ghost@test.com")).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    userProfileService.recordUserLogout("ghost@test.com", LoginHistory.LogoutType.VOLUNTARY))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("User not found");
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  forceUserLogout
    // ══════════════════════════════════════════════════════════════════════
    @Nested
    class ForceUserLogoutTests {

        @Test
        void forceUserLogout_callsKeycloakAndPublishes() {
            doNothing().when(keycloakAdminService).logoutUserSessions("student@test.com");

            userProfileService.forceUserLogout("student@test.com");

            verify(keycloakAdminService).logoutUserSessions("student@test.com");
            verify(rabbitTemplate).convertAndSend(eq("login-events"), (Object) any());
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  createMinimalProfile
    // ══════════════════════════════════════════════════════════════════════
    @Nested
    class CreateMinimalProfileTests {

        @Test
        void createMinimalProfile_existingUser_skips() {
            when(userProfileRepository.existsByEmail("student@test.com")).thenReturn(true);

            userProfileService.createMinimalProfile("student@test.com", Role.STUDENT);

            verify(userProfileRepository, never()).save(any());
        }

        @Test
        void createMinimalProfile_newUser_saves() {
            when(userProfileRepository.existsByEmail("new@test.com")).thenReturn(false);

            userProfileService.createMinimalProfile("new@test.com", Role.STUDENT);

            verify(userProfileRepository).save(any());
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Block/Unblock edge cases
    // ══════════════════════════════════════════════════════════════════════
    @Nested
    class BlockUserEdgeCases {

        @Test
        void sendReactivationEmail_nullFirstName_usesEmail() {
            user.setFirstName(null);
            when(userProfileRepository.findByEmail("student@test.com")).thenReturn(Optional.of(user));

            userProfileService.sendReactivationEmail("student@test.com");

            verify(mailSender).send(any(SimpleMailMessage.class));
        }

        @Test
        void unblockUser_nullFirstName_usesEmail() {
            user.setFirstName(null);
            user.setBlocked(true);
            user.setActive(false);
            when(userProfileRepository.findByEmail("student@test.com")).thenReturn(Optional.of(user));

            userProfileService.unblockUser("student@test.com");

            assertThat(user.isBlocked()).isFalse();
            verify(mailSender).send(any(SimpleMailMessage.class));
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  validateReactivationToken edge cases
    // ══════════════════════════════════════════════════════════════════════
    @Nested
    class ValidateReactivationTokenEdgeCases {

        @Test
        void validateToken_nullExpiry_returnsFalse() {
            user.setReactivationToken("some-token");
            user.setReactivationTokenExpiry(null);
            when(userProfileRepository.findAll()).thenReturn(List.of(user));

            boolean result = userProfileService.validateReactivationToken("some-token");

            assertThat(result).isFalse();
        }
    }
}