package com.example.bff.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;

/**
 * Explicit, manual {@link ClientRegistrationRepository} for the {@code bff-app}
 * Keycloak client.
 *
 * <p>This deliberately does NOT use Spring Boot's YAML {@code issuer-uri}
 * autodiscovery ({@code ClientRegistrations.fromIssuerLocation}). Keycloak is
 * configured with a fixed hostname ({@code KC_HOSTNAME=localhost},
 * {@code KC_HOSTNAME_PORT=8081}), so every OIDC discovery document - no
 * matter which URL is used to fetch it - advertises
 * {@code issuer=http://localhost:8081/realms/bff-demo}. Spring's
 * issuer-uri autodiscovery requires the fetched document's {@code issuer}
 * field to exactly equal the URL it was fetched from, which can never hold
 * here:
 *
 * <ul>
 *   <li>fetching from {@code http://localhost:8081/...} is unreachable from
 *       inside the backend container (no such DNS name on the Docker
 *       network);</li>
 *   <li>fetching from {@code http://keycloak:8080/...} (the Docker-internal
 *       name that IS reachable) returns a document whose {@code issuer} says
 *       {@code localhost:8081}, which fails Spring's equality check at
 *       startup.</li>
 * </ul>
 *
 * <p>Instead, this configuration explicitly separates browser-facing URLs
 * (authorization endpoint, redirect URI) from backend-container-facing URLs
 * (token, userinfo, JWK set endpoints, reached via Docker Compose service-name
 * DNS), while still validating the ID token's {@code iss} claim against
 * Keycloak's real, fixed issuer identity via {@code issuerUri}.
 */
@Configuration
public class OAuth2ClientConfig {

    private static final String REGISTRATION_ID = "bff-app";

    @Bean
    public ClientRegistrationRepository clientRegistrationRepository(
            @Value("${bff.oauth2.client-secret}") String clientSecret,
            @Value("${bff.oauth2.redirect-uri}") String redirectUri,
            @Value("${bff.oauth2.browser-issuer-uri}") String browserIssuerUri,
            @Value("${bff.oauth2.backend-issuer-uri}") String backendIssuerUri) {

        ClientRegistration bffApp = ClientRegistration.withRegistrationId(REGISTRATION_ID)
                .clientId(REGISTRATION_ID)
                .clientSecret(clientSecret)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri(redirectUri)
                .scope("openid", "profile", "email")
                // Browser-facing: the user's browser is redirected here directly.
                .authorizationUri(browserIssuerUri + "/protocol/openid-connect/auth")
                // Backend-container-facing: reached by the Spring Boot backend itself,
                // over the Docker Compose internal network.
                .tokenUri(backendIssuerUri + "/protocol/openid-connect/token")
                .userInfoUri(backendIssuerUri + "/protocol/openid-connect/userinfo")
                .jwkSetUri(backendIssuerUri + "/protocol/openid-connect/certs")
                // Must equal Keycloak's actual, fixed `iss` claim (browser-facing
                // hostname/port) regardless of which URL was used to reach an
                // endpoint. Used only for ID token issuer validation - does not
                // trigger any HTTP call when set directly on the builder.
                .issuerUri(browserIssuerUri)
                .userNameAttributeName("preferred_username")
                .clientName("bff-app")
                .build();

        return new InMemoryClientRegistrationRepository(bffApp);
    }
}
