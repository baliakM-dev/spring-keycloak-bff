package com.example.bff.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.RequestCacheConfigurer;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

/**
 * Stage 2A/2B security configuration.
 *
 * <p>Adds framework-native {@code oauth2Login()} (OAuth2/OIDC Authorization
 * Code flow against Keycloak) on top of the Stage 1 default-deny baseline.
 * No manual code exchange, token parsing, or OAuth state/nonce handling is
 * implemented - Spring Security owns all of that via
 * {@link ClientRegistrationRepository} (see {@link OAuth2ClientConfig}), the
 * default {@code HttpSessionOAuth2AuthorizationRequestRepository} (state),
 * and {@code OidcAuthorizationCodeAuthenticationProvider} (nonce, ID token
 * validation).
 *
 * <ul>
 *   <li>{@code /api/public/**}, {@code /api/auth/me}, and
 *       {@code /actuator/health} remain public at the filter-chain level.
 *       {@code /api/auth/me} still enforces its own authentication check
 *       inside the controller (see {@code AuthController}), so it can return
 *       a bare {@code 401} JSON body to an anonymous caller instead of the
 *       redirect-to-Keycloak behavior below.</li>
 *   <li>Everything else still requires authentication - now reachable via
 *       OAuth2/OIDC login against Keycloak. Anonymous requests to other
 *       {@code /api/**} paths (e.g. {@code /api/protected/hello}) receive a
 *       bare {@code 401} via a request-matcher-scoped
 *       {@link org.springframework.security.web.authentication.HttpStatusEntryPoint},
 *       rather than Spring Security's default redirect to
 *       {@code /oauth2/authorization/bff-app}. Real browser navigation to
 *       that endpoint is unaffected - it is handled upstream by
 *       {@code OAuth2AuthorizationRequestRedirectFilter}.</li>
 *   <li>Successful login establishes a server-side Spring application
 *       session; OAuth2 access/refresh tokens are held server-side only.
 *       No custom {@code OAuth2AuthorizedClientService}/{@code Repository}
 *       bean is defined here, so Spring Boot's own autoconfiguration
 *       ({@code OAuth2ClientConfigurations.OAuth2AuthorizedClientServiceConfiguration}
 *       + {@code OAuth2ClientWebSecurityAutoConfiguration}, both
 *       {@code @ConditionalOnBean}-gated on the {@link ClientRegistrationRepository}
 *       bean this class receives) registers {@code InMemoryOAuth2AuthorizedClientService}
 *       wrapped by {@code AuthenticatedPrincipalOAuth2AuthorizedClientRepository} -
 *       an application-memory map keyed by (registrationId, principal name),
 *       not the {@code HttpSession}. Tokens still never reach the browser
 *       either way.</li>
 *   <li>The HTTP request cache is disabled and the post-login redirect
 *       target is a fixed, configured, absolute URL ({@code
 *       bff.oauth2.post-login-redirect-uri}) - never a request-derived
 *       relative path - so login always returns to the frontend home route
 *       regardless of which URL triggered authentication, and regardless of
 *       the inbound request's {@code Host} header (see {@code
 *       loginSuccessHandler} Javadoc for why a relative path is not safe
 *       here).</li>
 * </ul>
 *
 * <p>Spring Security's default CSRF protection is left untouched - no
 * {@code .csrf(...)} customization here, relying on the zero-configuration
 * default ({@code HttpSessionCsrfTokenRepository} +
 * {@code XorCsrfTokenRequestAttributeHandler}). The OAuth2 login/callback
 * endpoints ({@code /oauth2/authorization/bff-app},
 * {@code /login/oauth2/code/bff-app}) are both {@code GET}-only, so no CSRF
 * exemption is needed. {@code /api/auth/csrf} (Stage 2C, see {@code
 * AuthController}) is {@code permitAll()} for the same reason as {@code
 * /api/auth/me}: it performs no authentication check of its own and must be
 * reachable pre-login to bootstrap the logout form.
 *
 * <p>Stage 2C also adds {@code .logout(...)}: Spring Security's default
 * {@code POST /logout} processing (CSRF-protected, session invalidation via
 * the built-in {@code SecurityContextLogoutHandler}) plus two explicit
 * additions - an {@link OAuth2AuthorizedClientRepository}-removing {@code
 * LogoutHandler} (the built-in handlers never touch the authorized-client
 * store, which is independent of the {@code HttpSession} - see the {@code
 * InMemoryOAuth2AuthorizedClientService} note above) and {@link
 * OidcClientInitiatedLogoutSuccessHandler} to redirect the browser through
 * Keycloak's end-session endpoint and back to the frontend home page.
 */
@Configuration
public class SecurityConfig {

    private static final String REGISTRATION_ID = "bff-app";

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            ClientRegistrationRepository clientRegistrationRepository,
            OAuth2AuthorizedClientRepository authorizedClientRepository,
            @Value("${bff.oauth2.post-login-redirect-uri}") String postLoginRedirectUri,
            @Value("${bff.oauth2.post-logout-redirect-uri}") String postLogoutRedirectUri)
            throws Exception {
        http.authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/public/**", "/api/auth/me", "/api/auth/csrf", "/actuator/health")
                        .permitAll()
                        .anyRequest().authenticated())
                // /api/auth/me is permitAll() above (Stage 2B): it performs its own
                // authentication check on the resolved principal so it can return a
                // bare 401 JSON body to an anonymous caller instead of participating
                // in the redirect-to-Keycloak behavior below. This entry point only
                // governs OTHER /api/** paths that are still gated by
                // anyRequest().authenticated() (currently /api/protected/hello):
                // anonymous requests to those get a bare 401 instead of Spring
                // Security's default redirect to the OAuth2 authorization endpoint.
                // Real browser navigation to /oauth2/authorization/bff-app is
                // unaffected - that request is handled by
                // OAuth2AuthorizationRequestRedirectFilter, upstream of this
                // authentication entry point.
                .exceptionHandling(exceptionHandling -> exceptionHandling.defaultAuthenticationEntryPointFor(
                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                        PathPatternRequestMatcher.pathPattern("/api/**")))
                // Disabled so an anonymous hit on a protected /api/** path can never
                // populate a saved-request session attribute that would otherwise
                // hijack the post-login redirect target computed below.
                .requestCache(RequestCacheConfigurer::disable)
                .oauth2Login(oauth2 -> oauth2
                        .authorizationEndpoint(endpoint -> endpoint.authorizationRequestResolver(
                                authorizationRequestResolver(clientRegistrationRepository)))
                        // Fixed, absolute, configured post-login destination
                        // (bff.oauth2.post-login-redirect-uri) - previously the
                        // relative path "/", which is NOT safe: Spring's
                        // DefaultRedirectStrategy.calculateRedirectUrl() only returns a
                        // relative target verbatim - for a scheme-qualified absolute
                        // URL like this one it also returns it verbatim, but a bare "/"
                        // instead gets expanded into an absolute Location by the
                        // servlet container using request.getServerName()/getServerPort(),
                        // which this app's proxy derives from the inbound Host header
                        // with no allowlist. Confirmed end-to-end: a forged
                        // "Host: attacker.example" header on the OAuth2 callback request
                        // produced "Location: http://attacker.example/" immediately after
                        // a real, successful login - a direct, unmitigated open redirect
                        // (no downstream allowlist like Keycloak's own for post-logout).
                        // alwaysUse=true makes this unconditional even if a saved
                        // request were somehow present (requestCache is disabled above
                        // regardless).
                        .defaultSuccessUrl(postLoginRedirectUri, true))
                .logout(logout -> logout
                        // Removes the BFF's server-side OAuth2AuthorizedClient record
                        // (access/refresh token) for this principal. Does not call
                        // Keycloak to revoke anything - only clears this application's
                        // own record. Runs in addition to (not instead of) the default
                        // logout handlers (CsrfLogoutHandler, SecurityContextLogoutHandler)
                        // that Spring Security's LogoutConfigurer always registers, so
                        // session invalidation still happens unconditionally.
                        .addLogoutHandler((request, response, authentication) -> authorizedClientRepository
                                .removeAuthorizedClient(REGISTRATION_ID, authentication, request, response))
                        .logoutSuccessHandler(
                                oidcLogoutSuccessHandler(clientRegistrationRepository, postLogoutRedirectUri)));
        return http.build();
    }

    /**
     * Explicitly enables PKCE (S256) as defense-in-depth on top of the
     * confidential {@code bff-app} client, by wrapping the default
     * authorization request resolver with Spring Security's built-in PKCE
     * customizer. No custom PKCE implementation.
     */
    private OAuth2AuthorizationRequestResolver authorizationRequestResolver(
            ClientRegistrationRepository clientRegistrationRepository) {
        DefaultOAuth2AuthorizationRequestResolver resolver = new DefaultOAuth2AuthorizationRequestResolver(
                clientRegistrationRepository, "/oauth2/authorization");
        resolver.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce());
        return resolver;
    }

    /**
     * Redirects the browser through Keycloak's end-session endpoint (see the
     * {@code end_session_endpoint} provider metadata set in {@code
     * OAuth2ClientConfig}) with an {@code id_token_hint} and {@code
     * post_logout_redirect_uri}, then back to this application's own,
     * fixed, configured home URL. {@code id_token_hint} is built entirely
     * server-side from the session's {@code OidcUser} - React never
     * receives or constructs it.
     *
     * <p>{@code postLogoutRedirectUri} is a literal, configured value
     * ({@code bff.oauth2.post-logout-redirect-uri}) - deliberately
     * <strong>not</strong> {@code OidcClientInitiatedLogoutSuccessHandler}'s
     * {@code "{baseUrl}"} template. {@code "{baseUrl}"} resolves from the
     * current request's {@code getServerName()}/{@code getServerPort()},
     * which this app's proxy derives from the inbound {@code Host} header
     * ({@code nginx.conf}/{@code vite.config.ts} forward it verbatim as
     * {@code $http_host} for the reasons documented there) with no
     * allowlist. Confirmed end-to-end: a request with a forged
     * {@code Host: attacker.example} header, replayed against a real
     * authenticated session with a valid CSRF token, produced a {@code
     * Location} redirecting to Keycloak with {@code
     * post_logout_redirect_uri=http://attacker.example/} - i.e. an
     * attacker-controlled value the BFF itself computed and handed to
     * Keycloak, not something Keycloak invented. Keycloak's own {@code
     * post.logout.redirect.uris} allowlist independently rejected that
     * specific value in this deployment, but relying solely on the
     * downstream party's allowlist as the only defense against a
     * server-computed, attacker-influenced redirect target is exactly the
     * "derive an external redirect from an untrusted Host header" pattern
     * this project's invariants forbid. The equivalent relative-path
     * {@code defaultSuccessUrl("/", true)} used for login success was
     * initially assumed safe for the same reason, but was found to be
     * exploitable the same way (see the comment at that call site) and has
     * since been fixed identically, with a fixed absolute URL. The
     * configured value here must exactly match -
     * including the trailing slash - the {@code post.logout.redirect.uris}
     * attribute registered on the {@code bff-app} Keycloak client (see
     * {@code keycloak/import/bff-demo-realm.json}), since Keycloak validates
     * it with an exact string match, not a prefix or pattern match.
     */
    private LogoutSuccessHandler oidcLogoutSuccessHandler(
            ClientRegistrationRepository clientRegistrationRepository, String postLogoutRedirectUri) {
        OidcClientInitiatedLogoutSuccessHandler handler =
                new OidcClientInitiatedLogoutSuccessHandler(clientRegistrationRepository);
        handler.setPostLogoutRedirectUri(postLogoutRedirectUri);
        return handler;
    }
}
