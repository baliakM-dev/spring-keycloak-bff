package com.example.bff.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Public, unauthenticated endpoints.
 *
 * <p>Stage 1 skeleton only: no authentication, no session, no business
 * functionality. Used by the frontend to verify BFF connectivity.
 */
@RestController
public class PublicController {

    @GetMapping("/api/public/hello")
    public Map<String, String> hello() {
        return Map.of("message", "spring-keycloak-bff");
    }
}
