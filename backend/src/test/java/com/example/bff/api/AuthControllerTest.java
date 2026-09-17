package com.example.bff.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the Stage 2B {@code GET /api/auth/me} session-view contract:
 * bare 401 JSON for anonymous callers, minimal id/displayName DTO for
 * authenticated callers (with the documented displayName fallback chain),
 * and {@code Cache-Control: no-store} on both branches.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithAnonymousUser
    void anonymousRequestReceivesUnauthenticatedJsonWithoutRedirect() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.authenticated").value(false))
                .andExpect(jsonPath("$.user").doesNotExist());
    }

    @Test
    void authenticatedUserReceivesIdAndDisplayNameFromNameClaim() throws Exception {
        mockMvc.perform(get("/api/auth/me")
                        .with(oidcLogin().idToken(token -> token.subject("user-123")
                                .claim("name", "Jane Doe")
                                .claim("preferred_username", "jane.doe"))))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.user.id").value("user-123"))
                .andExpect(jsonPath("$.user.displayName").value("Jane Doe"));
    }

    @Test
    void authenticatedUserFallsBackToPreferredUsernameWhenNameClaimMissing() throws Exception {
        mockMvc.perform(get("/api/auth/me")
                        .with(oidcLogin().idToken(
                                token -> token.subject("user-456").claim("preferred_username", "jane.doe"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.id").value("user-456"))
                .andExpect(jsonPath("$.user.displayName").value("jane.doe"));
    }

    @Test
    void authenticatedUserFallsBackToSubjectWhenNoOptionalClaimsPresent() throws Exception {
        mockMvc.perform(get("/api/auth/me").with(oidcLogin().idToken(token -> token.subject("user-789"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.id").value("user-789"))
                .andExpect(jsonPath("$.user.displayName").value("user-789"));
    }

    /**
     * Stage 2C: {@code GET /api/auth/csrf} contract - 200, the exact {token,
     * headerName, parameterName} shape (sourced from the resolved {@code
     * CsrfToken}, never hardcoded), and {@code Cache-Control: no-store}.
     * Reachable anonymously - it is {@code permitAll()} in {@code
     * SecurityConfig} and performs no authentication check of its own.
     */
    @Test
    @WithAnonymousUser
    void csrfEndpointReturnsTokenShapeWithNoStoreForAnonymousCaller() throws Exception {
        mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.headerName").value("X-CSRF-TOKEN"))
                .andExpect(jsonPath("$.parameterName").value("_csrf"));
    }

    @Test
    void csrfEndpointReturnsTokenShapeForAuthenticatedCallerToo() throws Exception {
        mockMvc.perform(get("/api/auth/csrf").with(oidcLogin()))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.headerName").isNotEmpty())
                .andExpect(jsonPath("$.parameterName").isNotEmpty());
    }
}
