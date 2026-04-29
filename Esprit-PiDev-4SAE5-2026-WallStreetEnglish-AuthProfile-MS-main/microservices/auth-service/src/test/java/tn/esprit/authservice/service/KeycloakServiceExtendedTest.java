package tn.esprit.authservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.*;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for KeycloakService.updateUserRole().
 *
 * The service uses REALM-level roles (keycloakAdmin.realm(realm).roles()),
 * NOT client-level roles. All mocks are aligned accordingly.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("KeycloakService Tests")
class KeycloakServiceExtendedTest {

    @Mock private Keycloak keycloakAdmin;
    @InjectMocks private KeycloakService keycloakService;

    // Realm-level mock chain
    @Mock private RealmResource    realmResource;
    @Mock private UsersResource    usersResource;
    @Mock private UserResource     userResource;
    @Mock private RolesResource    realmRolesResource;    // realm roles resource
    @Mock private RoleResource     realmRoleResource;     // single realm role
    @Mock private RoleMappingResource roleMappingResource;
    @Mock private RoleScopeResource   realmScopeResource; // realm-level scope

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(keycloakService, "realm", "myapp2");
        ReflectionTestUtils.setField(keycloakService, "clientId", "angular-app");
    }

    @Nested
    @DisplayName("updateUserRole")
    class UpdateUserRoleTests {

        @Test
        @DisplayName("Should throw when user not found in Keycloak")
        void updateUserRole_UserNotFound_ThrowsException() {
            when(keycloakAdmin.realm("myapp2")).thenReturn(realmResource);
            when(realmResource.users()).thenReturn(usersResource);
            when(usersResource.search("student@test.com")).thenReturn(Collections.emptyList());

            RuntimeException ex = assertThrows(RuntimeException.class, () ->
                    keycloakService.updateUserRole("student@test.com", "TUTOR"));

            assertTrue(ex.getMessage().contains("Failed to update role in Keycloak"));
        }

        @Test
        @DisplayName("Should throw when target realm role not found in Keycloak")
        void updateUserRole_ClientNotFound_ThrowsException() {
            UserRepresentation kcUser = new UserRepresentation();
            kcUser.setId("user-id-123");
            kcUser.setEmail("student@test.com");

            when(keycloakAdmin.realm("myapp2")).thenReturn(realmResource);
            when(realmResource.users()).thenReturn(usersResource);
            when(usersResource.search("student@test.com")).thenReturn(List.of(kcUser));

            // Service calls keycloakAdmin.realm(realm).roles().get(newRole).toRepresentation()
            when(realmResource.roles()).thenReturn(realmRolesResource);
            when(realmRolesResource.get("TUTOR")).thenReturn(realmRoleResource);
            when(realmRoleResource.toRepresentation())
                    .thenThrow(new RuntimeException("Role not found in Keycloak realm"));

            RuntimeException ex = assertThrows(RuntimeException.class, () ->
                    keycloakService.updateUserRole("student@test.com", "TUTOR"));

            assertTrue(ex.getMessage().contains("Failed to update role in Keycloak"));
        }

        @Test
        @DisplayName("Should throw when target role name is unknown")
        void updateUserRole_RoleNotFound_ThrowsException() {
            UserRepresentation kcUser = new UserRepresentation();
            kcUser.setId("user-id-123");

            when(keycloakAdmin.realm("myapp2")).thenReturn(realmResource);
            when(realmResource.users()).thenReturn(usersResource);
            when(usersResource.search("student@test.com")).thenReturn(List.of(kcUser));

            when(realmResource.roles()).thenReturn(realmRolesResource);
            when(realmRolesResource.get("ADMIN")).thenReturn(realmRoleResource);
            when(realmRoleResource.toRepresentation())
                    .thenThrow(new RuntimeException("Unknown role: ADMIN"));

            RuntimeException ex = assertThrows(RuntimeException.class, () ->
                    keycloakService.updateUserRole("student@test.com", "ADMIN"));

            assertTrue(ex.getMessage().contains("Failed to update role in Keycloak"));
        }

        @Test
        @DisplayName("Should successfully update role when all Keycloak resources exist")
        void updateUserRole_AllResourcesExist_ShouldSucceed() {
            UserRepresentation kcUser = new UserRepresentation();
            kcUser.setId("user-id-123");

            RoleRepresentation targetRole = new RoleRepresentation();
            targetRole.setName("TUTOR");

            RoleRepresentation currentRole = new RoleRepresentation();
            currentRole.setName("STUDENT");

            when(keycloakAdmin.realm("myapp2")).thenReturn(realmResource);
            when(realmResource.users()).thenReturn(usersResource);
            when(usersResource.search("student@test.com")).thenReturn(List.of(kcUser));

            // realm roles().get("TUTOR").toRepresentation()
            when(realmResource.roles()).thenReturn(realmRolesResource);
            when(realmRolesResource.get("TUTOR")).thenReturn(realmRoleResource);
            when(realmRoleResource.toRepresentation()).thenReturn(targetRole);

            // users().get(userId).roles().realmLevel().listAll()
            when(usersResource.get("user-id-123")).thenReturn(userResource);
            when(userResource.roles()).thenReturn(roleMappingResource);
            when(roleMappingResource.realmLevel()).thenReturn(realmScopeResource);
            when(realmScopeResource.listAll()).thenReturn(List.of(currentRole));

            doNothing().when(realmScopeResource).remove(anyList());
            doNothing().when(realmScopeResource).add(anyList());
            doNothing().when(userResource).logout();

            assertDoesNotThrow(() -> keycloakService.updateUserRole("student@test.com", "TUTOR"));

            verify(realmScopeResource).remove(List.of(currentRole));
            verify(realmScopeResource).add(List.of(targetRole));
            verify(userResource).logout();
        }

        @Test
        @DisplayName("Should skip remove step when user has no current business roles")
        void updateUserRole_NoCurrentRoles_ShouldSkipRemove() {
            UserRepresentation kcUser = new UserRepresentation();
            kcUser.setId("user-id-123");

            RoleRepresentation targetRole = new RoleRepresentation();
            targetRole.setName("TUTOR");

            when(keycloakAdmin.realm("myapp2")).thenReturn(realmResource);
            when(realmResource.users()).thenReturn(usersResource);
            when(usersResource.search("student@test.com")).thenReturn(List.of(kcUser));

            when(realmResource.roles()).thenReturn(realmRolesResource);
            when(realmRolesResource.get("TUTOR")).thenReturn(realmRoleResource);
            when(realmRoleResource.toRepresentation()).thenReturn(targetRole);

            when(usersResource.get("user-id-123")).thenReturn(userResource);
            when(userResource.roles()).thenReturn(roleMappingResource);
            when(roleMappingResource.realmLevel()).thenReturn(realmScopeResource);
            // No current roles → rolesToRemove is empty → remove() should NOT be called
            when(realmScopeResource.listAll()).thenReturn(Collections.emptyList());

            doNothing().when(realmScopeResource).add(anyList());
            doNothing().when(userResource).logout();

            assertDoesNotThrow(() -> keycloakService.updateUserRole("student@test.com", "TUTOR"));

            verify(realmScopeResource, never()).remove(anyList());
            verify(realmScopeResource).add(List.of(targetRole));
        }
    }
}