package com.edubase.enrollment.dto.response;

import com.edubase.enrollment.entity.EnrollmentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EnrollmentResponse {

    private Long id;
    private String courseId;
    private Long userId;
    private EnrollmentStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
