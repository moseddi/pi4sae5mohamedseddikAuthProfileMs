package tn.esprit.authservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class KeycloakService {

    private final Keycloak keycloakAdmin;

    @Value("${keycloak.realm}")
    private String realm;

    @Value("${keycloak.client-id}")
    private String clientId;

    public void updateUserRole(String email, String newRole) {
        log.info("Updating role in Keycloak for: {} to {}", email, newRole);

        try {
            // Find user in Keycloak
            List<UserRepresentation> users = keycloakAdmin.realm(realm).users().search(email);
            if (users.isEmpty()) {
                throw new RuntimeException("User not found in Keycloak");
            }

            String userId = users.get(0).getId();
            log.info("Found user in Keycloak with ID: {}", userId);

            // Find the realm role we want to assign
            RoleRepresentation targetRole = keycloakAdmin.realm(realm).roles()
                    .get(newRole).toRepresentation();

            // Get user's current realm roles
            List<RoleRepresentation> currentRoles = keycloakAdmin.realm(realm).users().get(userId)
                    .roles().realmLevel().listAll();
            log.info("Current realm roles: {}", currentRoles.stream().map(RoleRepresentation::getName).toList());

            // Filter out the business roles we want to replace
            List<String> businessRoles = List.of("STUDENT", "TUTOR", "ADMIN");
            List<RoleRepresentation> rolesToRemove = currentRoles.stream()
                    .filter(role -> businessRoles.contains(role.getName()))
                    .toList();

            // Remove all current business roles
            if (!rolesToRemove.isEmpty()) {
                keycloakAdmin.realm(realm).users().get(userId)
                        .roles().realmLevel().remove(rolesToRemove);
                log.info("Removed {} existing business roles", rolesToRemove.size());
            }

            // Add new role
            keycloakAdmin.realm(realm).users().get(userId)
                    .roles().realmLevel().add(Collections.singletonList(targetRole));

            log.info("✅ Role updated in Keycloak successfully");

            // Force logout all sessions
            keycloakAdmin.realm(realm).users().get(userId).logout();
            log.info("✅ User logged out from all sessions");

        } catch (Exception e) {
            log.error("❌ Failed to update role in Keycloak: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to update role in Keycloak", e);
        }
    }


}