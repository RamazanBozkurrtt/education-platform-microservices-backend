package com.edubase.enrollment.grpc;

import java.math.BigDecimal;

public record CourseSummary(
        String courseId,
        BigDecimal price,
        String currency
) {
}
