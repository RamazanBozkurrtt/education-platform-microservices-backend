package com.edubase.user.dto.internal;

import com.edubase.commonCore.events.InstructorStatus;
import lombok.Builder;

@Builder
public record InstructorSummaryResponse(
        String instructorId,
        String fullName,
        String email,
        String profileImageUrl,
        String headline,
        InstructorStatus status
) {
}
