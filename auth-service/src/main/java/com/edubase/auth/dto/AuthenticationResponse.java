package com.edubase.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuthenticationResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("refresh_token") String refreshToken,
        @JsonProperty("reactivation_link") String reactivationLink,
        @JsonProperty("message") String message,
        @JsonProperty("reactivation_required") Boolean reactivationRequired,
        @JsonProperty("user_id") Long userId,
        @JsonProperty("email") String email,
        @JsonProperty("roles") List<String> roles
) {
    public AuthenticationResponse(String accessToken, String refreshToken) {
        this(accessToken, refreshToken, null, null, null, null, null, List.of());
    }

    public static AuthenticationResponse forTokens(String accessToken, String refreshToken) {
        return new AuthenticationResponse(accessToken, refreshToken, null, null, null, null, null, List.of());
    }

    public static AuthenticationResponse forTokens(String accessToken,
                                                   String refreshToken,
                                                   Long userId,
                                                   String email,
                                                   List<String> roles) {
        return new AuthenticationResponse(accessToken, refreshToken, null, null, null, userId, email, roles == null ? List.of() : roles);
    }

    public static AuthenticationResponse forReactivationRequired(String message) {
        return new AuthenticationResponse(null, null, null, message, true, null, null, List.of());
    }
}
