package com.edubase.course.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Lesson progress update request")
public class LessonProgressUpdateRequest {

    @NotNull(message = "lastWatchedSecond is required")
    @Min(value = 0, message = "lastWatchedSecond must be >= 0")
    @Schema(description = "Last watched second in the video", example = "240", minimum = "0")
    private Integer lastWatchedSecond;

    @NotNull(message = "videoDurationSecond is required")
    @Positive(message = "videoDurationSecond must be > 0")
    @Schema(description = "Video duration in seconds", example = "600", minimum = "1")
    private Integer videoDurationSecond;

    @Schema(description = "Optional client-calculated percentage, validated only if present", example = "40.00")
    private BigDecimal watchedPercentage;
}
