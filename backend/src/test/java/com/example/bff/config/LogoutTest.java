package com.example.bff.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Stage 2C: {@code POST /logout} behavior - CSRF enforcement, local session
 * invalidation, the mandatory {@link org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository}
 * removal (see {@code SecurityConfig}'s added {@code LogoutHandler}), the
 * Keycloak end-session redirect, and confirmation that {@code GET /logout}
 * is inert.
 *
 * <p>Uses a real {@code bff-app} {@link ClientRegistration} (fetched from the
 * app's actual {@link ClientRegistrationRepository} bean, not a synthetic
 * "test" registration) so the established {@code OAuth2AuthenticationToken}'s
 * {@code authorizedClientRegistrationId} is {@code "bff-app"} - the same id
 * the {@code LogoutHandler} removes and {@code OidcClientInitiatedLogoutSuccessHandler}
 * looks up {@code end_session_endpoint} metadata for.
 */
@SpringBootTest
@AutoConfigureMockMvc
class LogoutTest {

    private static final String REGISTRATION_ID = "bff-app";
    private static final String TEST_PRINCIPAL_NAME = "logout-test-user";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ClientRegistrationRepository clientRegistrationRepository;

    @Autowired
    private OAuth2AuthorizedClientService authorizedClientService;

    private ClientRegistration bffAppRegistration() {
        ClientRegistration registration = clientRegistrationRepository.findByRegistrationId(REGISTRATION_ID);
        assertThat(registration).isNotNull();
        return registration;
    }

    /**
     * Establishes an authenticated MockMvc session by logging in via {@code
     * GET /api/auth/me} (permitAll, but performs its own principal check),
     * capturing the resulting {@link MockHttpSession} - which now has the
     * OIDC {@code SecurityContext} persisted - for reuse by subsequent
     * requests in the same test, mirroring a real browser's cookie jar.
     */
    private MockHttpSession authenticatedSession() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/auth/me")
                        .with(oidcLogin()
                                .clientRegistration(bffAppRegistration())
                                .idToken(token -> token.subject(TEST_PRINCIPAL_NAME))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andReturn();
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertThat(session).isNotNull();
        return session;
    }

    @Test
    void postLogoutWithoutCsrfTokenIsRejectedAndSessionSurvives() throws Exception {
        MockHttpSession session = authenticatedSession();

        mockMvc.perform(post("/logout").session(session)).andExpect(status().isForbidden());

        assertThat(session.isInvalid()).isFalse();
        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true));
    }

    @Test
    void getLogoutDoesNotInvalidateAuthentication() throws Exception {
        MockHttpSession session = authenticatedSession();

        // No controller maps GET /logout (LogoutFilter only matches POST
        // when CSRF is enabled, which it is by default) - it falls through
        // to the authenticated-only default-deny rule and then to a normal
        // 404, never touching the session.
        mockMvc.perform(get("/logout").session(session));

        assertThat(session.isInvalid()).isFalse();
        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true));
    }

    @Test
    void postLogoutWithValidCsrfTokenInvalidatesSessionAndRedirectsToKeycloakEndSession() throws Exception {
        MockHttpSession session = authenticatedSession();

        mockMvc.perform(post("/logout").session(session).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string(
                        "Location",
                        org.hamcrest.Matchers.allOf(
                                org.hamcrest.Matchers.startsWith(
                                        "http://localhost:8081/realms/bff-demo/protocol/openid-connect/logout"),
                                org.hamcrest.Matchers.containsString("id_token_hint="),
                                // The exact configured value (bff.oauth2.post-logout-redirect-uri,
                                // defaulted here to http://localhost:5173/), not a request-derived
                                // "{baseUrl}" value - see postLogoutRedirectIsFixedAndIgnoresForgedHostHeader
                                // for why this must be a literal, not request-derived.
                                org.hamcrest.Matchers.containsString(
                                        "post_logout_redirect_uri=http://localhost:5173/"))));

        assertThat(session.isInvalid()).isTrue();
    }

    /**
     * Security regression test: {@code post_logout_redirect_uri} must be the
     * fixed, configured value regardless of the inbound request's Host/server
     * name. Confirmed end-to-end against the real running stack (see {@code
     * docs/stage-2c-report.md}) that the earlier {@code
     * "{baseUrl}"}-templated implementation reflected a forged {@code Host}
     * header straight into this parameter - i.e. the BFF itself computed an
     * attacker-controlled redirect target and handed it to Keycloak. This
     * test simulates the same condition by forging the mock request's server
     * name (MockMvc's closest equivalent of a forged {@code Host} header
     * reaching a real servlet container via this app's Host-forwarding
     * proxy) and asserts the redirect target does not change.
     */
    @Test
    void postLogoutRedirectIsFixedAndIgnoresForgedHostHeader() throws Exception {
        MockHttpSession session = authenticatedSession();

        mockMvc.perform(post("/logout")
                        .session(session)
                        .with(csrf())
                        .with(request -> {
                            request.setServerName("attacker.example");
                            return request;
                        }))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string(
                        "Location",
                        org.hamcrest.Matchers.allOf(
                                org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("attacker.example")),
                                org.hamcrest.Matchers.containsString(
                                        "post_logout_redirect_uri=http://localhost:5173/"))));
    }

    /**
     * Mandatory acceptance-gate test (Stage 2C constraint 3): establishes an
     * authenticated session with a populated {@link OAuth2AuthorizedClient}
     * for the test principal, performs a CSRF-valid {@code POST /logout},
     * and asserts the authorized-client record - independent of the {@code
     * HttpSession} - is gone afterwards. A session-only assertion is not
     * sufficient evidence for this constraint; both must be checked.
     */
    @Test
    void postLogoutRemovesTheAuthorizedClientRecordSeparatelyFromTheSession() throws Exception {
        ClientRegistration registration = bffAppRegistration();
        OAuth2AccessToken accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER, "test-access-token", Instant.now(), Instant.now().plusSeconds(300));
        OAuth2AuthorizedClient authorizedClient =
                new OAuth2AuthorizedClient(registration, TEST_PRINCIPAL_NAME, accessToken);
        // Any Authentication whose getName() matches the principal used
        // below is sufficient here - InMemoryOAuth2AuthorizedClientService
        // keys solely on (registrationId, principal.getName()).
        authorizedClientService.saveAuthorizedClient(
                authorizedClient, new TestingAuthenticationToken(TEST_PRINCIPAL_NAME, "n/a"));
        OAuth2AuthorizedClient beforeLogout =
                authorizedClientService.loadAuthorizedClient(REGISTRATION_ID, TEST_PRINCIPAL_NAME);
        assertThat(beforeLogout).isNotNull();

        MockHttpSession session = authenticatedSession();

        mockMvc.perform(post("/logout").session(session).with(csrf())).andExpect(status().is3xxRedirection());

        assertThat(session.isInvalid()).isTrue();
        OAuth2AuthorizedClient afterLogout =
                authorizedClientService.loadAuthorizedClient(REGISTRATION_ID, TEST_PRINCIPAL_NAME);
        assertThat(afterLogout).isNull();
    }
}
