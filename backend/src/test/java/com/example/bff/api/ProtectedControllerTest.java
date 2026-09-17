package com.example.bff.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies Stage 2A/2B's default-deny baseline still holds for
 * {@code GET /api/protected/hello}: unreachable anonymously, reachable only
 * for an authenticated OIDC session - using Spring Security Test's OIDC
 * login support rather than a real Keycloak round-trip.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ProtectedControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithAnonymousUser
    void anonymousRequestReceivesBareUnauthorized() throws Exception {
        // Stage 2B: a request-matcher-scoped HttpStatusEntryPoint for /api/**
        // (SecurityConfig) now guarantees a bare 401 for anonymous requests to
        // protected API paths, instead of Spring Security's default
        // redirect-to-Keycloak behavior.
        mockMvc.perform(get("/api/protected/hello")).andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedOidcUserReceivesAuthenticatedBody() throws Exception {
        mockMvc.perform(get("/api/protected/hello").with(oidcLogin()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.message").value("authenticated"));
    }

    @Test
    @WithAnonymousUser
    void publicEndpointRemainsAccessibleAfterOAuth2LoginIsEnabled() throws Exception {
        mockMvc.perform(get("/api/public/hello"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("spring-keycloak-bff"));
    }
}
