package com.example.bff.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Stage 2B: verifies that scoping the {@link
 * org.springframework.security.web.authentication.HttpStatusEntryPoint} 401
 * behavior to {@code /api/**} does not affect real browser-navigation login
 * initiation, which is handled upstream by
 * {@code OAuth2AuthorizationRequestRedirectFilter} and is outside
 * {@code /api/**}.
 *
 * <p>Stage 2C follow-up: also verifies the post-login redirect target
 * (previously the relative {@code "/"}, now the fixed, configured
 * {@code bff.oauth2.post-login-redirect-uri}) is immune to a forged
 * {@code Host} header - the same vulnerability class confirmed and fixed
 * for the post-logout redirect (see {@code SecurityConfig}'s
 * {@code oidcLogoutSuccessHandler} Javadoc and {@code LogoutTest}).
 */
@SpringBootTest
@AutoConfigureMockMvc
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Value("${bff.oauth2.post-login-redirect-uri}")
    private String postLoginRedirectUri;

    @Test
    @WithAnonymousUser
    void loginInitiationStillRedirectsAnonymousBrowserNavigationToKeycloak() throws Exception {
        mockMvc.perform(get("/oauth2/authorization/bff-app"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string(
                        "Location", org.hamcrest.Matchers.startsWith("http://localhost:8081/realms/bff-demo")));
    }

    /**
     * Exercises {@link SavedRequestAwareAuthenticationSuccessHandler} - the
     * exact class {@code .defaultSuccessUrl(url, true)} constructs internally
     * - directly with the actual configured {@code
     * bff.oauth2.post-login-redirect-uri} value, confirming it is returned
     * verbatim regardless of the request's server name.
     *
     * <p><strong>Important limitation, stated explicitly rather than
     * overclaimed:</strong> {@link MockHttpServletResponse#sendRedirect}
     * (Spring's test double) does not reproduce a real servlet container's
     * behavior of expanding a <em>relative</em> redirect target into an
     * absolute {@code Location} using the request's scheme/server-name/port
     * - it stores whatever string it is given verbatim, whether relative or
     * absolute. This is why the earlier vulnerable configuration
     * ({@code defaultSuccessUrl("/", true)}) could not be reproduced as a
     * failing assertion here even before the fix: {@code
     * MockHttpServletResponse.getRedirectedUrl()} would have returned the
     * literal {@code "/"} regardless of the forged server name, masking the
     * real container-level bug entirely. This test therefore does not - and
     * cannot, with this test infrastructure - prove the old relative
     * configuration was vulnerable, nor would it have caught a regression
     * back to it. The actual vulnerability and its fix were instead
     * confirmed against a <em>real</em> embedded/containerized servlet
     * container end-to-end: a forged {@code Host: attacker.example} header
     * replayed against the real OAuth2 callback of the actual running Docker
     * stack produced {@code Location: http://attacker.example/} before this
     * fix, and the fixed, configured absolute URL unconditionally afterward
     * (see {@code docs/stage-2c-report.md} for the full transcript). What
     * this test does verify: the fixed value, and the redirect strategy's
     * handling of an already-absolute target, contain no request-derived
     * component that could vary with a forged host even in principle.
     */
    @Test
    void configuredAbsolutePostLoginRedirectIsReturnedVerbatim() throws Exception {
        SavedRequestAwareAuthenticationSuccessHandler handler = new SavedRequestAwareAuthenticationSuccessHandler();
        handler.setDefaultTargetUrl(postLoginRedirectUri);
        handler.setAlwaysUseDefaultTargetUrl(true);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/login/oauth2/code/bff-app");
        request.setServerName("attacker.example");
        MockHttpServletResponse response = new MockHttpServletResponse();
        Authentication authentication = new TestingAuthenticationToken("test-user", "n/a");

        handler.onAuthenticationSuccess(request, response, authentication);

        assertThat(response.getRedirectedUrl()).isEqualTo(postLoginRedirectUri);
    }
}
