package com.edubase.user.dto.internal;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record AuthRoleUpdateResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("refresh_token") String refreshToken,
        @JsonProperty("reactivation_link") String reactivationLink,
        @JsonProperty("user_id") Long userId,
        @JsonProperty("email") String email,
        @JsonProperty("roles") List<String> roles
) {
}
