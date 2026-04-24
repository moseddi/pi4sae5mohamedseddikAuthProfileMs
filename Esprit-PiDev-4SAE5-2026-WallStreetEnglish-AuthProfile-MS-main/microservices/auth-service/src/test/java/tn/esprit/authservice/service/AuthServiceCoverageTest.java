package tn.esprit.authservice.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.*;
import org.keycloak.representations.idm.RoleRepresentation;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import tn.esprit.authservice.client.UserServiceClient;
import tn.esprit.authservice.dto.AuthResponse;
import tn.esprit.authservice.dto.LoginRequest;
import tn.esprit.authservice.dto.RegisterRequest;
import tn.esprit.authservice.entity.Role;
import tn.esprit.authservice.entity.User;
import tn.esprit.authservice.repository.UserRepository;

import java.net.URI;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceCoverageTest {

    @Mock private UserRepository userRepository;
    @Mock private UserServiceClient userServiceClient;
    @Mock private Keycloak keycloakAdmin;
    @Mock private WebClient webClient;
    @Mock private RabbitTemplate rabbitTemplate;
    @Mock private HttpServletRequest httpServletRequest;

    @Mock private RealmResource realmResource;
    @Mock private UsersResource usersResource;
    @Mock private UserResource userResource;
    @Mock private RoleMappingResource roleMappingResource;
    @Mock private RoleScopeResource roleScopeResource;
    @Mock private RolesResource rolesResource;
    @Mock private RoleResource roleResource;

    @InjectMocks private AuthService authService;

    @BeforeEach
    void injectValues() throws Exception {
        setField("realm", "test-realm");
        setField("clientId", "test-client");
        setField("serverUrl", "http://localhost:8080/auth");
    }

    private void setField(String name, String value) throws Exception {
        var f = AuthService.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(authService, value);
    }

    @SuppressWarnings("unchecked")
    private void mockWebClientTokenSuccess() {
        // Create mocks for the WebClient chain
        WebClient.RequestBodyUriSpec requestBodyUriSpec = mock(WebClient.RequestBodyUriSpec.class);
        WebClient.RequestBodySpec requestBodySpec = mock(WebClient.RequestBodySpec.class);
        WebClient.RequestHeadersSpec requestHeadersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);

        Map<String, Object> tokenMap = new HashMap<>();
        tokenMap.put("access_token", "mock-access-token");
        tokenMap.put("refresh_token", "mock-refresh-token");

        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.contentType(any())).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(Map.class)).thenReturn(Mono.just(tokenMap));
    }

    private void mockKeycloakCreateUser(String keycloakId, int status) throws Exception {
        URI location = new URI("http://localhost/users/" + keycloakId);
        Response response = mock(Response.class);
        when(response.getStatus()).thenReturn(status);
        when(response.getLocation()).thenReturn(location);

        when(keycloakAdmin.realm("test-realm")).thenReturn(realmResource);
        when(realmResource.users()).thenReturn(usersResource);
        when(usersResource.create(any())).thenReturn(response);
    }

    private void mockKeycloakRoleAssign(String keycloakId, String roleName) {
        RoleRepresentation roleRep = new RoleRepresentation();
        roleRep.setName(roleName);

        when(keycloakAdmin.realm("test-realm")).thenReturn(realmResource);
        when(realmResource.roles()).thenReturn(rolesResource);
        when(rolesResource.get(roleName)).thenReturn(roleResource);
        when(roleResource.toRepresentation()).thenReturn(roleRep);

        when(realmResource.users()).thenReturn(usersResource);
        when(usersResource.get(keycloakId)).thenReturn(userResource);
        when(userResource.roles()).thenReturn(roleMappingResource);
        when(roleMappingResource.realmLevel()).thenReturn(roleScopeResource);
        doNothing().when(roleScopeResource).add(anyList());
    }

    private void stubRequest() {
        when(httpServletRequest.getHeader("X-Forwarded-For")).thenReturn(null);
        when(httpServletRequest.getHeader("X-Real-IP")).thenReturn(null);
        when(httpServletRequest.getRemoteAddr()).thenReturn("127.0.0.1");
        when(httpServletRequest.getHeader("User-Agent"))
                .thenReturn("Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120");
    }

    private User buildUser(String email, Role role) {
        return User.builder()
                .id(1L)
                .email(email)
                .password("")
                .role(role)
                .keycloakId("kc-id")
                .active(true)
                .emailVerified(true)
                .build();
    }

    @Nested
    class RegisterTests {

        @Test
        void register_emailAlreadyExists_throwsException() {
            RegisterRequest req = new RegisterRequest();
            req.setEmail("existing@test.com");
            req.setPassword("pass123");
            req.setConfirmPassword("pass123");

            when(userRepository.findByEmail("existing@test.com"))
                    .thenReturn(Optional.of(buildUser("existing@test.com", Role.STUDENT)));

            assertThatThrownBy(() -> authService.register(req))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Email already exists");
        }

        @Test
        void register_success() throws Exception {
            RegisterRequest req = new RegisterRequest();
            req.setEmail("newuser@test.com");
            req.setPassword("pass123");
            req.setConfirmPassword("pass123");
            req.setRole(Role.STUDENT);

            when(userRepository.findByEmail("newuser@test.com")).thenReturn(Optional.empty());
            mockKeycloakCreateUser("kc-id-123", 201);
            mockKeycloakRoleAssign("kc-id-123", "STUDENT");

            User savedUser = buildUser("newuser@test.com", Role.STUDENT);
            when(userRepository.save(any(User.class))).thenReturn(savedUser);
            mockWebClientTokenSuccess();

            AuthResponse response = authService.register(req);

            assertThat(response).isNotNull();
            assertThat(response.getMessage()).isEqualTo("Registration successful");
        }
    }

    @Nested
    class LogoutTests {

        @Test
        void logout_success_returnsResponse() {
            User user = buildUser("user@test.com", Role.STUDENT);
            when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
            stubRequest();

            AuthResponse resp = authService.logout("user@test.com", "VOLUNTARY");

            assertThat(resp.getMessage()).isEqualTo("Logout successful");
            verify(rabbitTemplate, atLeastOnce()).convertAndSend(anyString(), any(Object.class));
        }

        @Test
        void logout_userNotFound_throwsException() {
            when(userRepository.findByEmail("ghost@test.com")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.logout("ghost@test.com", "VOLUNTARY"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Logout failed");
        }

        @Test
        void logout_rabbitMqFails_stillReturnsSuccess() {
            User user = buildUser("user@test.com", Role.STUDENT);
            when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
            stubRequest();
            doThrow(new RuntimeException("rabbit down"))
                    .when(rabbitTemplate).convertAndSend(anyString(), any(Object.class));

            AuthResponse resp = authService.logout("user@test.com", "VOLUNTARY");
            assertThat(resp.getMessage()).isEqualTo("Logout successful");
        }
    }
}