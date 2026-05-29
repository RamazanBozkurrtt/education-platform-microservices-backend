package com.edubase.course.dto.request.finalexam;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ExamOptionRequest {

    @JsonProperty("optionText")
    @JsonAlias({"text"})
    @NotBlank(message = "Option text is required")
    @Size(max = 5000, message = "Option text must be at most 5000 characters")
    private String optionText;

    @JsonProperty("isCorrect")
    @JsonAlias({"correct"})
    @NotNull(message = "isCorrect is required")
    private Boolean isCorrect;

    @JsonProperty("orderIndex")
    @NotNull(message = "Order index is required")
    @PositiveOrZero(message = "Order index must be zero or positive")
    private Integer orderIndex;
}
