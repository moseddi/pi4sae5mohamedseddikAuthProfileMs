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

            // Get the client
            List<ClientRepresentation> clients = keycloakAdmin.realm(realm).clients().findByClientId(clientId);
            if (clients.isEmpty()) {
                throw new RuntimeException("Client not found in Keycloak");
            }
            String clientUuid = clients.get(0).getId();
            log.info("Found client with UUID: {}", clientUuid);

            // Get all roles for this client
            List<RoleRepresentation> availableRoles = keycloakAdmin.realm(realm).clients().get(clientUuid).roles().list();
            log.info("Available roles: {}", availableRoles.stream().map(RoleRepresentation::getName).toList());

            // Find the role we want to assign
            RoleRepresentation targetRole = availableRoles.stream()
                    .filter(role -> role.getName().equals(newRole))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Role " + newRole + " not found in Keycloak"));

            // Get user's current roles
            List<RoleRepresentation> currentRoles = keycloakAdmin.realm(realm).users().get(userId)
                    .roles().clientLevel(clientUuid).listAll();
            log.info("Current roles: {}", currentRoles.stream().map(RoleRepresentation::getName).toList());

            // Remove all current roles
            if (!currentRoles.isEmpty()) {
                keycloakAdmin.realm(realm).users().get(userId)
                        .roles().clientLevel(clientUuid).remove(currentRoles);
                log.info("Removed {} existing roles", currentRoles.size());
            }

            // Add new role
            keycloakAdmin.realm(realm).users().get(userId)
                    .roles().clientLevel(clientUuid).add(Collections.singletonList(targetRole));

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