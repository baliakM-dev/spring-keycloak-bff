package com.example.bff.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.RequestCacheConfigurer;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
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
 *       target is fixed to {@code "/"}, so login always returns to the
 *       frontend home route regardless of which URL triggered
 *       authentication.</li>
 * </ul>
 *
 * <p>Spring Security's default CSRF protection is left untouched. The OAuth2
 * login/callback endpoints ({@code /oauth2/authorization/bff-app},
 * {@code /login/oauth2/code/bff-app}) are both {@code GET}-only, so no CSRF
 * exemption is needed.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http, ClientRegistrationRepository clientRegistrationRepository) throws Exception {
        http.authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/public/**", "/api/auth/me", "/actuator/health")
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
                        // Fixed post-login destination: the frontend origin's "/",
                        // never derived from Host/X-Forwarded-* headers or a request
                        // parameter. alwaysUse=true makes this unconditional even if a
                        // saved request were somehow present.
                        .defaultSuccessUrl("/", true));
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
}
