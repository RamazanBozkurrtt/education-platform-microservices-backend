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
@Schema(description = "Course progress summary for authenticated user")
public class CourseProgressSummaryResponse {

    private String courseId;
    private Integer totalLessons;
    private Integer completedLessons;
    private BigDecimal overallPercentage;
    private String lastLessonId;
    private Integer lastWatchedSecond;
    private Instant lastActivityAt;
}
