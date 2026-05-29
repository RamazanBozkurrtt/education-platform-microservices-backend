package com.edubase.course.dto.response.finalexam;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Value
@Builder
public class FinalExamManageResponse {
    Long finalExamId;
    String courseId;
    String title;
    String description;
    BigDecimal passingScore;
    Integer questionCount;
    Integer durationMinutes;
    Integer maxAttempts;
    Integer availabilityDays;
    boolean active;
    String createdBy;
    String updatedBy;
    Instant createdAt;
    Instant updatedAt;
    List<ManageQuestion> questions;

    @Value
    @Builder
    public static class ManageQuestion {
        Long questionId;
        String questionText;
        String imageUrl;
        String imageObjectKey;
        Integer orderIndex;
        BigDecimal points;
        boolean active;
        List<ManageOption> options;
    }

    @Value
    @Builder
    public static class ManageOption {
        Long optionId;
        String optionText;
        boolean correct;
        Integer orderIndex;
    }
}
