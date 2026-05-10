package com.edubase.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record InstructorApplyRequest(
        @NotBlank(message = "displayName cannot be blank")
        @Size(max = 100, message = "displayName must be at most 100 characters")
        String displayName,

        @NotBlank(message = "biography cannot be blank")
        @Size(max = 2000, message = "biography must be at most 2000 characters")
        String biography,

        @NotEmpty(message = "expertise cannot be empty")
        List<@NotBlank(message = "expertise item cannot be blank") @Size(max = 100, message = "expertise item must be at most 100 characters") String> expertise,

        @Size(max = 500, message = "websiteUrl must be at most 500 characters")
        String websiteUrl,

        @Size(max = 500, message = "linkedinUrl must be at most 500 characters")
        String linkedinUrl,

        @Size(max = 500, message = "githubUrl must be at most 500 characters")
        String githubUrl,

        @Size(max = 500, message = "profileImageUrl must be at most 500 characters")
        String profileImageUrl
) {
}
