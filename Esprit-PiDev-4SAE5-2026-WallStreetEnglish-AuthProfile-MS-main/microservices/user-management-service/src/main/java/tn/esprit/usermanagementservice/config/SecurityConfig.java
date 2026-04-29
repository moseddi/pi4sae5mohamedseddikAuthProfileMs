package tn.esprit.usermanagementservice.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@Slf4j
@RequiredArgsConstructor
public class SecurityConfig {

    private final KeycloakJwtRoleConverter keycloakJwtRoleConverter;

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
    private String issuerUri;

    @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}")
    private String jwkSetUri;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/users/from-auth").permitAll()
                        .requestMatchers("/api/users/record-login").permitAll()
                        .requestMatchers("/api/users/record-logout").permitAll()
                        .requestMatchers("/api/users/sync-from-auth").permitAll()
                        .requestMatchers("/api/users/test").permitAll()
                        .requestMatchers("/api/users/stats/**").permitAll()
                        .requestMatchers("/api/users/recent-logins").permitAll()
                        .requestMatchers("/api/users/recent-logins-formatted").permitAll()
                        .requestMatchers("/api/users/logins/today").permitAll()
                        .requestMatchers("/api/users/sessions/active-count").permitAll()
                        .requestMatchers("/api/users/logins/suspicious-count").permitAll()
                        .requestMatchers("/api/users/active-sessions").permitAll()
                        .requestMatchers("/api/users/statistics").permitAll()
                        .requestMatchers("/api/users/reactivate/**").permitAll()
                        .requestMatchers("/api/users/reactivate-request").permitAll()
                        .requestMatchers("/api/users/check-blocked/**").permitAll()
                        .requestMatchers("/api/users/unblock/**").permitAll()
                        .requestMatchers("/ws/**").permitAll()
                        .requestMatchers("/ws/info").permitAll()
                        .requestMatchers("/topic/**").permitAll()
                        .requestMatchers("/app/**").permitAll()
                        .requestMatchers("/api/users/email/**").authenticated()
                        .requestMatchers("/api/users/profile/**").authenticated()
                        .requestMatchers("/api/users/force-logout/**").authenticated()
                        .requestMatchers("/api/users/{id}").authenticated()
                        .requestMatchers("/api/users").authenticated()
                        .requestMatchers("/api/users/block/**").authenticated()
                        .requestMatchers("/api/users/send-reactivation-email/**").authenticated()
                        .anyRequest().authenticated()
                )
                .sessionManagement(sess -> sess
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .jwtAuthenticationConverter(jwtAuthenticationConverter())
                        )
                );

        return http.build();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(keycloakJwtRoleConverter);
        return converter;
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        log.info("🔐 Initializing Flexible JwtDecoder for Issuer: {} and JWKS: {}", issuerUri, jwkSetUri);
        NimbusJwtDecoder jwtDecoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        
        // We allow both the public issuer (localhost:6083) and the internal one (keycloak:8080)
        String internalIssuer = "http://keycloak:8080/realms/myapp2";
        
        org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator<org.springframework.security.oauth2.jwt.Jwt> validator = 
            new org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator<>(
                new org.springframework.security.oauth2.jwt.JwtTimestampValidator(),
                jwt -> {
                    String iss = jwt.getIssuer().toString();
                    if (iss.equals(issuerUri) || iss.equals(internalIssuer)) {
                        return org.springframework.security.oauth2.core.OAuth2TokenValidatorResult.success();
                    }
                    log.warn("❌ Invalid Issuer in token: {}. Expected either {} or {}", iss, issuerUri, internalIssuer);
                    return org.springframework.security.oauth2.core.OAuth2TokenValidatorResult.failure(
                        new org.springframework.security.oauth2.core.OAuth2Error("invalid_token", "The issuer is not trusted", null));
                }
            );
        
        jwtDecoder.setJwtValidator(validator);
        return jwtDecoder;
    }
}