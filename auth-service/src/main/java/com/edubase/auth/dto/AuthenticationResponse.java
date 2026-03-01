package com.edubase.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AuthenticationResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("refresh_token") String refreshToken,
        @JsonProperty("reactivation_link") String reactivationLink
) {
    public AuthenticationResponse(String accessToken, String refreshToken) {
        this(accessToken, refreshToken, null);
    }

    public static AuthenticationResponse forTokens(String accessToken, String refreshToken) {
        return new AuthenticationResponse(accessToken, refreshToken, null);
    }

    public static AuthenticationResponse forReactivation(String reactivationLink) {
        return new AuthenticationResponse(null, null, reactivationLink);
    }
}
