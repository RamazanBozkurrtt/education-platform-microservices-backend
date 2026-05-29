package com.edubase.course.dto.response.finalexam;

import com.edubase.course.entity.finalexam.AttemptStatus;
import com.edubase.course.entity.finalexam.ResultStatus;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

@Value
@Builder
public class SubmitExamResponse {
    Long attemptId;
    AttemptStatus attemptStatus;
    ResultStatus resultStatus;
    BigDecimal score;
    boolean passed;
    Integer remainingAttempts;
    boolean certificateEligible;
}
