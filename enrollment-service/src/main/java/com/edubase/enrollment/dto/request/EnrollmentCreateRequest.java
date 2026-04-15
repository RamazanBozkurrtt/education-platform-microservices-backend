package com.edubase.enrollment.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class EnrollmentCreateRequest {

    @NotBlank(message = "courseId is required")
    private String courseId;

    @Positive(message = "userId must be positive")
    private Long userId;
}
