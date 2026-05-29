package com.edubase.course.dto.request.finalexam;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class ExamQuestionCreateRequest {

    @NotBlank(message = "Question text is required")
    @Size(max = 10000, message = "Question text must be at most 10000 characters")
    private String questionText;

    @NotNull(message = "Order index is required")
    @PositiveOrZero(message = "Order index must be zero or positive")
    private Integer orderIndex;

    @Positive(message = "Points must be positive")
    private BigDecimal points = BigDecimal.ONE;

    @NotNull(message = "Active is required")
    private Boolean active = true;

    @Valid
    @NotEmpty(message = "Options are required")
    private List<ExamOptionRequest> options;
}
