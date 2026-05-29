package com.edubase.course.dto.response.finalexam;

import com.edubase.course.entity.finalexam.AttemptStatus;
import com.edubase.course.entity.finalexam.ResultStatus;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.List;

@Value
@Builder
public class StudentExamAttemptResponse {
    Long attemptId;
    Long finalExamId;
    String courseId;
    Integer attemptNumber;
    AttemptStatus attemptStatus;
    ResultStatus resultStatus;
    Instant startedAt;
    Instant submittedAt;
    Instant terminatedAt;
    Instant expiredAt;
    Instant expiresAt;
    Long remainingDurationSeconds;
    String statusMessage;
    List<StudentQuestion> questions;

    @Value
    @Builder
    public static class StudentQuestion {
        Long questionId;
        String questionText;
        String imageUrl;
        Integer orderIndex;
        Long selectedOptionId;
        List<StudentOption> options;
    }

    @Value
    @Builder
    public static class StudentOption {
        Long optionId;
        String optionText;
        Integer orderIndex;
    }
}
