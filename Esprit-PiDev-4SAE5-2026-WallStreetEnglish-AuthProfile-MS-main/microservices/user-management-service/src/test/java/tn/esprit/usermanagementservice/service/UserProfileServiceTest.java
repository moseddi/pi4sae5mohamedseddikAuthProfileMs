package tn.esprit.usermanagementservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import tn.esprit.usermanagementservice.client.AuthServiceClient;
import tn.esprit.usermanagementservice.dto.UpdateUserRequest;
import tn.esprit.usermanagementservice.dto.UserProfileDTO;
import tn.esprit.usermanagementservice.entity.Role;
import tn.esprit.usermanagementservice.entity.UserProfile;
import tn.esprit.usermanagementservice.repository.LoginHistoryRepository;
import tn.esprit.usermanagementservice.repository.ProfileChangeHistoryRepository;
import tn.esprit.usermanagementservice.repository.UserProfileRepository;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserProfileService Unit Tests")
class UserProfileServiceTest {

    @Mock
    private UserProfileRepository userProfileRepository;

    @Mock
    private AuthServiceClient authServiceClient;

    @Mock
    private KeycloakAdminService keycloakAdminService;

    @Mock
    private ProfileChangeHistoryRepository profileChangeHistoryRepository;

    @Mock
    private PhoneNumberValidator phoneNumberValidator;

    @Mock
    private LoginHistoryRepository loginHistoryRepository;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private JavaMailSender mailSender;

    @InjectMocks
    private UserProfileService userProfileService;

    private UserProfile testUser;
    private UpdateUserRequest updateRequest;

    @BeforeEach
    void setUp() {
        testUser = UserProfile.builder()
                .id(1L)
                .email("student@test.com")
                .firstName("John")
                .lastName("Doe")
                .country("USA")
                .role(Role.STUDENT)
                .active(true)
                .blocked(false)
                .loginCount(5)
                .createdAt(LocalDateTime.now().minusDays(30))
                .lastLoginAt(LocalDateTime.now().minusDays(5))
                .lastActivityAt(LocalDateTime.now().minusDays(1))
                .build();

        updateRequest = new UpdateUserRequest();
    }

    // ==================== GET USER TESTS ====================

    @Nested
    @DisplayName("Get User Tests")
    class GetUserTests {

        @Test
        @DisplayName("Should return user when email exists")
        void getUserByEmail_WhenExists_ShouldReturnUser() {
            when(userProfileRepository.findByEmail("student@test.com"))
                    .thenReturn(Optional.of(testUser));

            UserProfileDTO result = userProfileService.getUserByEmail("student@test.com");

            assertNotNull(result);
            assertEquals("student@test.com", result.getEmail());
            assertEquals(Role.STUDENT, result.getRole());
        }

        @Test
        @DisplayName("Should throw exception when email not found")
        void getUserByEmail_WhenNotFound_ShouldThrowException() {
            when(userProfileRepository.findByEmail("nonexistent@test.com"))
                    .thenReturn(Optional.empty());

            RuntimeException exception = assertThrows(RuntimeException.class, () -> {
                userProfileService.getUserByEmail("nonexistent@test.com");
            });

            assertEquals("User not found with email: nonexistent@test.com", exception.getMessage());
        }

        @Test
        @DisplayName("Should return user when id exists")
        void getUserById_WhenExists_ShouldReturnUser() {
            when(userProfileRepository.findById(1L))
                    .thenReturn(Optional.of(testUser));

            UserProfileDTO result = userProfileService.getUserById(1L);

            assertNotNull(result);
            assertEquals("student@test.com", result.getEmail());
        }

        @Test
        @DisplayName("Should throw exception when id not found")
        void getUserById_WhenNotFound_ShouldThrowException() {
            when(userProfileRepository.findById(999L))
                    .thenReturn(Optional.empty());

            RuntimeException exception = assertThrows(RuntimeException.class, () -> {
                userProfileService.getUserById(999L);
            });

            assertEquals("User not found with id: 999", exception.getMessage());
        }
    }

    // ==================== UPDATE PROFILE TESTS ====================

    @Nested
    @DisplayName("Update Profile Tests")
    class UpdateProfileTests {

        @Test
        @DisplayName("Should update country successfully")
        void updateUserProfile_ChangeCountry_ShouldUpdate() {
            when(userProfileRepository.findByEmail("student@test.com"))
                    .thenReturn(Optional.of(testUser));
            when(userProfileRepository.save(any(UserProfile.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            updateRequest.setCountry("France");

            UserProfileDTO result = userProfileService.updateUserProfile("student@test.com", updateRequest);

            assertEquals("France", result.getCountry());
            verify(profileChangeHistoryRepository, times(1)).save(any());
        }

        @Test
        @DisplayName("Should update first name successfully")
        void updateUserProfile_ChangeFirstName_ShouldUpdate() {
            when(userProfileRepository.findByEmail("student@test.com"))
                    .thenReturn(Optional.of(testUser));
            when(userProfileRepository.save(any(UserProfile.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            updateRequest.setFirstName("Jane");

            UserProfileDTO result = userProfileService.updateUserProfile("student@test.com", updateRequest);

            assertEquals("Jane", result.getFirstName());
        }

        @Test
        @DisplayName("Should throw exception when user not found for update")
        void updateUserProfile_UserNotFound_ShouldThrowException() {
            when(userProfileRepository.findByEmail("nonexistent@test.com"))
                    .thenReturn(Optional.empty());

            RuntimeException exception = assertThrows(RuntimeException.class, () -> {
                userProfileService.updateUserProfile("nonexistent@test.com", updateRequest);
            });

            assertEquals("User not found with email: nonexistent@test.com", exception.getMessage());
        }
    }

    // ==================== RECORD LOGIN TESTS ====================

    @Nested
    @DisplayName("Record Login Tests")
    class RecordLoginTests {

        @Test
        @DisplayName("Should record login successfully for active user")
        void recordUserLogin_WhenUserExists_ShouldUpdateLoginCount() {
            when(userProfileRepository.findByEmail("student@test.com"))
                    .thenReturn(Optional.of(testUser));
            when(userProfileRepository.save(any(UserProfile.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            userProfileService.recordUserLogin("student@test.com");

            verify(userProfileRepository, times(1)).save(any(UserProfile.class));
        }

        @Test
        @DisplayName("Should throw exception when user not found for login")
        void recordUserLogin_UserNotFound_ShouldThrowException() {
            when(userProfileRepository.findByEmail("nonexistent@test.com"))
                    .thenReturn(Optional.empty());

            RuntimeException exception = assertThrows(RuntimeException.class, () -> {
                userProfileService.recordUserLogin("nonexistent@test.com");
            });

            assertEquals("User not found with email: nonexistent@test.com", exception.getMessage());
        }

        @Test
        @DisplayName("Should throw exception when user is blocked")
        void recordUserLogin_WhenUserBlocked_ShouldThrowException() {
            testUser.setBlocked(true);
            when(userProfileRepository.findByEmail("student@test.com"))
                    .thenReturn(Optional.of(testUser));

            RuntimeException exception = assertThrows(RuntimeException.class, () -> {
                userProfileService.recordUserLogin("student@test.com");
            });

            assertEquals("Your account is blocked. Please check your email for reactivation instructions.",
                    exception.getMessage());
            verify(userProfileRepository, never()).save(any(UserProfile.class));
        }
    }

    // ==================== BLOCK/UNBLOCK TESTS ====================

    @Nested
    @DisplayName("Block/Unblock User Tests")
    class BlockUnblockTests {

        @Test
        @DisplayName("Should block user successfully")
        void blockUser_WhenUserExists_ShouldBlock() {
            when(userProfileRepository.findByEmail("student@test.com"))
                    .thenReturn(Optional.of(testUser));
            when(userProfileRepository.save(any(UserProfile.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            doNothing().when(messagingTemplate).convertAndSend(anyString(), anyString());
            // FIXED: Use SimpleMailMessage.class instead of any()
            doNothing().when(mailSender).send(any(SimpleMailMessage.class));

            userProfileService.blockUser("student@test.com", "Suspicious activity");

            assertTrue(testUser.isBlocked());
            assertFalse(testUser.isActive());
            assertNotNull(testUser.getBlockedAt());
            assertEquals("Suspicious activity", testUser.getBlockedReason());
        }

        @Test
        @DisplayName("Should throw exception when blocking non-existent user")
        void blockUser_UserNotFound_ShouldThrowException() {
            when(userProfileRepository.findByEmail("nonexistent@test.com"))
                    .thenReturn(Optional.empty());

            RuntimeException exception = assertThrows(RuntimeException.class, () -> {
                userProfileService.blockUser("nonexistent@test.com", "Reason");
            });

            assertEquals("User not found: nonexistent@test.com", exception.getMessage());
        }

        @Test
        @DisplayName("Should unblock user successfully")
        void unblockUser_WhenUserExists_ShouldUnblock() {
            testUser.setBlocked(true);
            testUser.setActive(false);

            when(userProfileRepository.findByEmail("student@test.com"))
                    .thenReturn(Optional.of(testUser));
            when(userProfileRepository.save(any(UserProfile.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            doNothing().when(messagingTemplate).convertAndSend(anyString(), anyString());
            // FIXED: Use SimpleMailMessage.class instead of any()
            doNothing().when(mailSender).send(any(SimpleMailMessage.class));

            userProfileService.unblockUser("student@test.com");

            assertFalse(testUser.isBlocked());
            assertTrue(testUser.isActive());
            assertNull(testUser.getBlockedAt());
            assertNull(testUser.getBlockedReason());
        }
    }

    // ==================== DELETE USER TESTS ====================

    @Nested
    @DisplayName("Delete User Tests")
    class DeleteUserTests {

        @Test
        @DisplayName("Should delete user when exists")
        void deleteUser_WhenExists_ShouldDelete() {
            when(userProfileRepository.existsById(1L)).thenReturn(true);
            doNothing().when(userProfileRepository).deleteById(1L);

            assertDoesNotThrow(() -> userProfileService.deleteUser(1L));
            verify(userProfileRepository, times(1)).deleteById(1L);
        }

        @Test
        @DisplayName("Should throw exception when deleting non-existent user")
        void deleteUser_WhenNotFound_ShouldThrowException() {
            when(userProfileRepository.existsById(999L)).thenReturn(false);

            RuntimeException exception = assertThrows(RuntimeException.class, () -> {
                userProfileService.deleteUser(999L);
            });

            assertEquals("User not found with id: 999", exception.getMessage());
            verify(userProfileRepository, never()).deleteById(anyLong());
        }
    }

    // ==================== GET ALL USERS TESTS ====================

    @Nested
    @DisplayName("Get All Users Tests")
    class GetAllUsersTests {

        @Test
        @DisplayName("Should return all users")
        void getAllUsers_ShouldReturnList() {
            when(userProfileRepository.findAll()).thenReturn(java.util.List.of(testUser));

            var result = userProfileService.getAllUsers();

            assertNotNull(result);
            assertEquals(1, result.size());
            assertEquals("student@test.com", result.get(0).getEmail());
        }
    }

    // ==================== GET USERS BY ROLE TESTS ====================

    @Nested
    @DisplayName("Get Users By Role Tests")
    class GetUsersByRoleTests {

        @Test
        @DisplayName("Should return users by role")
        void getUsersByRole_ShouldReturnFilteredList() {
            when(userProfileRepository.findByRole(Role.STUDENT)).thenReturn(java.util.List.of(testUser));

            var result = userProfileService.getUsersByRole(Role.STUDENT);

            assertNotNull(result);
            assertEquals(1, result.size());
            assertEquals(Role.STUDENT, result.get(0).getRole());
        }
    }

}