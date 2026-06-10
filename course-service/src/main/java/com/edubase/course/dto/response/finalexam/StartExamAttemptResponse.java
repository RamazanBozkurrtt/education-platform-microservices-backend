package com.edubase.course.dto.response.finalexam;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class StartExamAttemptResponse {
    boolean createdNewAttempt;
    Integer remainingAttempts;
    AttemptSummaryResponse attempt;
}
