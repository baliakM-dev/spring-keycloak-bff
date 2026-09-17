package com.example.bff.api;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Application-owned session view for the frontend, per Stage 2B.
 *
 * <p>{@code /api/auth/me} is {@code permitAll()} in {@link
 * com.example.bff.config.SecurityConfig} - the authentication check happens
 * here by inspecting the resolved principal, not via the security filter
 * chain. This lets an anonymous caller receive a bare {@code 401} JSON body
 * instead of Spring Security's default redirect-to-Keycloak behavior, which
 * would otherwise apply to any authenticated-only route.
 *
 * <p>Never returns tokens, raw claims, email, roles, or framework
 * {@code Authentication}/{@code OidcUser}/{@code OAuth2AuthorizedClient}
 * objects - only the minimal {@link UserView} shape.
 */
@RestController
public class AuthController {

    @GetMapping("/api/auth/me")
    public ResponseEntity<MeResponse> me(@AuthenticationPrincipal OidcUser oidcUser) {
        if (oidcUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .cacheControl(CacheControl.noStore())
                    .body(MeResponse.unauthenticated());
        }

        UserView user = new UserView(oidcUser.getSubject(), displayName(oidcUser));
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(MeResponse.authenticated(user));
    }

    /**
     * Stage 2C: resolves and returns the current CSRF token so the frontend
     * can attach it to the logout form (and, generically, to any other
     * unsafe same-origin request).
     *
     * <p>{@code permitAll()} in {@link com.example.bff.config.SecurityConfig}
     * - reachable before login, since the Logout control's CSRF bootstrap
     * must work regardless of session state, and resolving a token here may
     * itself establish an anonymous session (not an authenticated one).
     *
     * <p>{@code CsrfToken} is resolved as a request attribute by {@code
     * CsrfFilter} upstream of this controller; it is normally lazily
     * deferred (BREACH-protected {@code XorCsrfTokenRequestAttributeHandler})
     * until something calls {@link CsrfToken#getToken()} - that call here is
     * what forces resolution and persistence into the session-backed
     * repository. {@code headerName}/{@code parameterName} are read from the
     * same object, never hardcoded.
     */
    @GetMapping("/api/auth/csrf")
    public ResponseEntity<CsrfResponse> csrf(CsrfToken csrfToken) {
        String token = csrfToken.getToken();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new CsrfResponse(token, csrfToken.getHeaderName(), csrfToken.getParameterName()));
    }

    /**
     * Fallback chain: {@code name} claim -> {@code preferred_username} claim
     * -> {@code sub} claim. Uses {@link OidcUser}'s typed
     * {@code StandardClaimAccessor} getters, which return {@code null} for a
     * missing claim rather than throwing, so a token missing optional
     * profile claims cannot crash this endpoint.
     */
    private String displayName(OidcUser oidcUser) {
        String fullName = oidcUser.getFullName();
        if (fullName != null) {
            return fullName;
        }
        String preferredUsername = oidcUser.getPreferredUsername();
        if (preferredUsername != null) {
            return preferredUsername;
        }
        return oidcUser.getSubject();
    }
}
