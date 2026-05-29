package com.edubase.course.dto.response.finalexam;

import com.edubase.course.entity.finalexam.AttemptStatus;
import com.edubase.course.entity.finalexam.ResultStatus;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.Instant;

@Value
@Builder
public class AttemptSummaryResponse {
    Long attemptId;
    Integer attemptNumber;
    AttemptStatus attemptStatus;
    ResultStatus resultStatus;
    BigDecimal score;
    Boolean passed;
    Instant startedAt;
    Instant submittedAt;
    Instant terminatedAt;
    Instant expiredAt;
}
