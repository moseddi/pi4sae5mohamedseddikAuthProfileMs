package tn.esprit.authservice.config;

import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@Slf4j  // Add this annotation
public class KeycloakConfig {

    @Value("${keycloak.server-url}")
    private String serverUrl;

    @Value("${keycloak.realm}")
    private String realm;

    @Value("${keycloak.admin-username}")
    private String adminUsername;

    @Value("${keycloak.admin-password}")
    private String adminPassword;

    @Value("${keycloak.admin-client-id}")
    private String adminClientId;

    @Bean
    public Keycloak keycloakAdmin() {
        log.info("Initializing Keycloak admin client with server: {}", serverUrl);

        try {
            Keycloak keycloak = KeycloakBuilder.builder()
                    .serverUrl(serverUrl)
                    .realm("master")  // Admin uses master realm
                    .username(adminUsername)
                    .password(adminPassword)
                    .clientId(adminClientId)
                    .build();

            // Test the connection by getting server info
            keycloak.serverInfo().getInfo();
            log.info("Keycloak admin client initialized successfully");

            return keycloak;
        } catch (Exception e) {
            log.error("Failed to initialize Keycloak admin client: {}", e.getMessage(), e);
            throw new RuntimeException("Keycloak initialization failed", e);
        }
    }

    @Bean
    public WebClient webClient() {
        return WebClient.builder()
                .baseUrl(serverUrl)
                .build();
    }
}