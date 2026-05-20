package com.edubase.user.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

@Builder
public record InstructorProfileResponse(
        Long id,
        Long userId,
        String displayName,
        String biography,
        List<String> expertise,
        String profileImageUrl,
        String websiteUrl,
        String linkedinUrl,
        String githubUrl,
        List<String> roles,
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("refresh_token") String refreshToken,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
