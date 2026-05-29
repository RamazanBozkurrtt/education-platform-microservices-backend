package com.edubase.course.dto.request.finalexam;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class FinalExamUpdateRequest {

    @NotBlank(message = "Title is required")
    @Size(max = 255, message = "Title must be at most 255 characters")
    private String title;

    @Size(max = 5000, message = "Description must be at most 5000 characters")
    private String description;

    @NotNull(message = "Passing score is required")
    @Min(value = 0, message = "Passing score must be at least 0")
    @Max(value = 100, message = "Passing score must be at most 100")
    private BigDecimal passingScore;

    @NotNull(message = "Question count is required")
    @Min(value = 1, message = "Question count must be at least 1")
    private Integer questionCount;

    @NotNull(message = "Duration minutes is required")
    @Min(value = 1, message = "Duration minutes must be at least 1")
    private Integer durationMinutes;

    @NotNull(message = "Max attempts is required")
    @Min(value = 1, message = "Max attempts must be at least 1")
    private Integer maxAttempts;

    @NotNull(message = "Availability days is required")
    @Min(value = 1, message = "Availability days must be at least 1")
    private Integer availabilityDays;

    @NotNull(message = "Active is required")
    private Boolean active;
}
