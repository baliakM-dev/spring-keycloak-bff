package com.example.bff.api;

/**
 * Response body for {@code GET /api/auth/csrf}.
 *
 * <p>A CSRF token is distinct from an OAuth token: it protects unsafe,
 * session-cookie-authenticated requests against forgery, and never grants
 * access to any resource by itself. {@code headerName}/{@code
 * parameterName} are sourced directly from the resolved {@code CsrfToken} -
 * never hardcoded - since Spring Security, not this class, owns those
 * names.
 */
public record CsrfResponse(String token, String headerName, String parameterName) {}
