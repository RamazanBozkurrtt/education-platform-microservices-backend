package com.edubase.course.dto.request.finalexam;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class SaveExamAnswerRequest {

    @Valid
    @NotEmpty(message = "answers are required")
    private List<AnswerItem> answers;

    @Data
    public static class AnswerItem {

        @NotNull(message = "questionId is required")
        private Long questionId;

        @NotNull(message = "selectedOptionId is required")
        private Long selectedOptionId;
    }
}
