package com.edubase.user.dto.internal;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record InstructorSummariesRequest(
        @NotEmpty(message = "instructorIds cannot be empty")
        @Size(max = 500, message = "Maximum 500 instructorIds are allowed")
        List<String> instructorIds
) {
}
