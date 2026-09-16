package com.example.bff.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Stage 1 security configuration.
 *
 * <p>This is intentionally minimal and does NOT configure OAuth2 login,
 * sessions, or CSRF integration with a frontend yet - those belong to a
 * later stage. It only establishes an explicit, default-deny baseline:
 *
 * <ul>
 *   <li>{@code /api/public/**} and {@code /actuator/health} are accessible
 *       without authentication.</li>
 *   <li>Everything else requires authentication. No authentication
 *       mechanism is configured yet, so those endpoints are effectively
 *       unreachable in this stage - this is intentional and forward-looking
 *       rather than a temporary {@code permitAll()} for the whole
 *       application.</li>
 * </ul>
 *
 * <p>Spring Security's default CSRF protection is left enabled (not
 * touched at all) since there is no cookie-authenticated mutating endpoint
 * yet. No {@code oauth2Login()}, {@code oauth2Client()}, or
 * {@code oauth2ResourceServer()} DSL is used in this stage, even though the
 * OAuth2 Client dependency is already on the classpath for a future stage.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/api/public/**", "/actuator/health").permitAll()
                .anyRequest().authenticated()
        );
        return http.build();
    }
}
