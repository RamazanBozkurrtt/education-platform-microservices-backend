package com.edubase.user.dto.internal;

import com.edubase.user.entity.UserStatus;
import jakarta.validation.constraints.NotNull;

public record InstructorStatusUpdateRequest(
        @NotNull(message = "status is required")
        UserStatus status
) {
}
