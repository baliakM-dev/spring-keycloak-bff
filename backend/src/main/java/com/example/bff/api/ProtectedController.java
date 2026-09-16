package com.example.bff.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Minimal authenticated-only endpoint, used only to prove that a Spring
 * application session established via OAuth2/OIDC login is authenticated.
 *
 * <p>Protected by the default-deny {@code anyRequest().authenticated()}
 * rule in {@code SecurityConfig} - no endpoint-specific security
 * configuration is needed here. Anonymous requests never reach this method;
 * they are handled by Spring Security's standard, framework-native
 * unauthenticated-request behavior (no custom exception handling).
 *
 * <p>Stage 2A only: does not return any OIDC claims, tokens, or profile
 * information. {@code /api/auth/me} is a later stage.
 */
@RestController
public class ProtectedController {

    @GetMapping("/api/protected/hello")
    public Map<String, String> hello() {
        return Map.of("message", "authenticated");
    }
}
