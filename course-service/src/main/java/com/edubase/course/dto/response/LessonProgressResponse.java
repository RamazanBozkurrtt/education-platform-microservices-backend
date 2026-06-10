package com.edubase.course.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Lesson progress response")
public class LessonProgressResponse {

    private String courseId;
    private String lessonId;
    private Integer durationSeconds;
    private Integer lastWatchedSecond;
    private BigDecimal watchedPercentage;
    private boolean completed;
    private Instant completedAt;
    private Instant updatedAt;
}
