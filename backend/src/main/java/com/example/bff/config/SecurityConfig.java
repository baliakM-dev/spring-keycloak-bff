package com.example.bff.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Stage 2A security configuration.
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
 *   <li>{@code /api/public/**} and {@code /actuator/health} remain public.</li>
 *   <li>Everything else still requires authentication - now reachable via
 *       OAuth2/OIDC login against Keycloak.</li>
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
                        .requestMatchers("/api/public/**", "/actuator/health").permitAll()
                        .anyRequest().authenticated())
                .oauth2Login(oauth2 -> oauth2.authorizationEndpoint(
                        endpoint -> endpoint.authorizationRequestResolver(
                                authorizationRequestResolver(clientRegistrationRepository))));
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
