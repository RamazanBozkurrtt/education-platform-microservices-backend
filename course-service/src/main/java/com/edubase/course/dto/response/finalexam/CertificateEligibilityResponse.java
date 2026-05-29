package com.edubase.course.dto.response.finalexam;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;

@Value
@Builder
public class CertificateEligibilityResponse {
    String courseId;
    String studentId;
    Long finalExamId;
    Long attemptId;
    boolean eligible;
    Instant earnedAt;
}
