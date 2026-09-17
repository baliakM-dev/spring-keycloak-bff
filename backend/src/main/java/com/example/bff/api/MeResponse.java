package com.example.bff.api;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Response body for {@code GET /api/auth/me}.
 *
 * <p>{@code user} is omitted from the JSON entirely (rather than serialized
 * as {@code null}) when {@code authenticated} is {@code false}, matching the
 * documented anonymous contract of {@code { "authenticated": false }}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MeResponse(boolean authenticated, UserView user) {

    public static MeResponse authenticated(UserView user) {
        return new MeResponse(true, user);
    }

    public static MeResponse unauthenticated() {
        return new MeResponse(false, null);
    }
}
