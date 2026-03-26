package tn.esprit.usermanagementservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

import static org.springframework.security.config.Customizer.withDefaults;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        // Public endpoints - no token needed
                        .requestMatchers("/api/users/from-auth").permitAll()
                        .requestMatchers("/api/users/record-login").permitAll()
                        .requestMatchers("/api/users/sync-from-auth").permitAll()
                        .requestMatchers("/api/users/test").permitAll()
                        .requestMatchers("/api/users/stats/**").permitAll()  // ← AJOUTE CETTE LIGNE

                        // Protected endpoints - need valid Keycloak token
                        .requestMatchers("/api/users/email/**").authenticated()
                        .requestMatchers("/api/users/profile/**").authenticated()
                        .requestMatchers("/api/users/force-logout/**").authenticated()
                        .requestMatchers("/api/users/{id}").authenticated()
                        .requestMatchers("/api/users").authenticated()
                        .anyRequest().authenticated()
                )
                .sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(withDefaults()));

        return http.build();
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        return NimbusJwtDecoder.withJwkSetUri("http://localhost:6083/realms/myapp2/protocol/openid-connect/certs").build();
    }
}