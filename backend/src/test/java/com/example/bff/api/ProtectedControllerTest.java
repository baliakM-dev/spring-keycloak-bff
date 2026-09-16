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
 * Verifies Stage 2A's default-deny baseline still holds for
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
    void anonymousRequestDoesNotReceiveAuthenticatedBody() throws Exception {
        mockMvc.perform(get("/api/protected/hello"))
                // Default Spring Security behavior for an unauthenticated request
                // once oauth2Login() is configured: redirected towards
                // authentication rather than served the protected body. We assert
                // only that the authenticated payload was NOT returned, not a
                // hand-picked status code.
                .andExpect(result -> {
                    int statusCode = result.getResponse().getStatus();
                    String body = result.getResponse().getContentAsString();
                    boolean gotAuthenticatedBody = statusCode == 200 && body.contains("authenticated");
                    org.assertj.core.api.Assertions.assertThat(gotAuthenticatedBody)
                            .as("anonymous request must not receive the authenticated body")
                            .isFalse();
                });
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
