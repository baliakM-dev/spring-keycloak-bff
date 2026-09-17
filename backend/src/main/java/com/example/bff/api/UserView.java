package com.example.bff.api;

/**
 * Minimal, application-owned view of the authenticated user, exposed via
 * {@code GET /api/auth/me}.
 *
 * <p>Deliberately excludes email, roles, raw OIDC claims, tokens, and any
 * framework {@code Authentication}/{@code OidcUser}/
 * {@code OAuth2AuthorizedClient} object - only an {@code id} (the OIDC
 * {@code sub} claim) and a human-readable {@code displayName} are surfaced.
 */
public record UserView(String id, String displayName) {}
