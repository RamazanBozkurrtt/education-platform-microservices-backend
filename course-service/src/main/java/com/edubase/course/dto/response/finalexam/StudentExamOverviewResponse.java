package com.edubase.course.dto.response.finalexam;

import com.edubase.course.entity.finalexam.AttemptStatus;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Value
@Builder
public class StudentExamOverviewResponse {
    boolean examExists;
    boolean examActive;
    Long finalExamId;
    String title;
    String description;
    Integer totalQuestionCount;
    Integer durationMinutes;
    BigDecimal passingScore;
    Integer maxAttempts;
    Integer remainingAttempts;
    Integer totalVideos;
    Integer completedVideos;
    BigDecimal completionPercentage;
    List<AttemptSummaryResponse> attempts;
    boolean hasActiveAttempt;
    Long activeAttemptId;
    Instant activeAttemptExpiresAt;
    boolean courseCompleted;
    boolean canStartExam;
    String canStartReason;
    AttemptStatus lastAttemptStatus;
    boolean certificateEligible;
}
