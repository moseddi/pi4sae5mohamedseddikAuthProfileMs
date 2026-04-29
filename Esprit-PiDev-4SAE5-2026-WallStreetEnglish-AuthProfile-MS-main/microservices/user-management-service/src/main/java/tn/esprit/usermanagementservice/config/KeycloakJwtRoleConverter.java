package tn.esprit.usermanagementservice.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import tn.esprit.usermanagementservice.repository.UserProfileRepository;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.stream.Collectors;

@Component
@Slf4j
@RequiredArgsConstructor
public class KeycloakJwtRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    private final UserProfileRepository userProfileRepository;

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        log.debug("Converting JWT roles. Claims: {}", jwt.getClaims());
        Set<String> allRoles = new HashSet<>();

        // 1. Extract from realm_access (Standard Keycloak)
        Map<String, Object> realmAccess = jwt.getClaim("realm_access");
        if (realmAccess != null && realmAccess.containsKey("roles")) {
            @SuppressWarnings("unchecked")
            List<String> roles = (List<String>) realmAccess.get("roles");
            if (roles != null) allRoles.addAll(roles);
        }

        // 2. Extract from resource_access (Client-specific roles)
        Map<String, Object> resourceAccess = jwt.getClaim("resource_access");
        if (resourceAccess != null) {
            resourceAccess.values().forEach(client -> {
                if (client instanceof Map) {
                    Map<String, Object> clientMap = (Map<String, Object>) client;
                    if (clientMap.containsKey("roles")) {
                        @SuppressWarnings("unchecked")
                        List<String> roles = (List<String>) clientMap.get("roles");
                        if (roles != null) allRoles.addAll(roles);
                    }
                }
            });
        }

        // 3. Extract from top-level roles claim (Custom mappers)
        if (jwt.hasClaim("roles")) {
            Object rolesObj = jwt.getClaim("roles");
            if (rolesObj instanceof List) {
                @SuppressWarnings("unchecked")
                List<String> roles = (List<String>) rolesObj;
                allRoles.addAll(roles);
            } else if (rolesObj instanceof String) {
                allRoles.add((String) rolesObj);
            }
        }

        // Check if we found any of our business roles
        boolean hasBusinessRole = allRoles.stream()
                .anyMatch(r -> r.equals("ADMIN") || r.equals("STUDENT") || r.equals("TUTOR"));

        if (!hasBusinessRole) {
            String email = jwt.getClaimAsString("email");
            if (email != null) {
                log.info("⚠️ No business roles in token for {}, checking database...", email);
                userProfileRepository.findByEmail(email).ifPresent(user -> {
                    allRoles.add(user.getRole().name());
                    log.info("✅ Found role in database: {}", user.getRole());
                });
            }
        }

        if (allRoles.isEmpty()) {
            log.warn("❌ No roles found in token or database for user: {}", jwt.getClaimAsString("preferred_username"));
        } else {
            log.info("✅ Final Authorities for {}: {}", jwt.getClaimAsString("email"), allRoles);
        }

        return allRoles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .collect(Collectors.toList());
    }
}
