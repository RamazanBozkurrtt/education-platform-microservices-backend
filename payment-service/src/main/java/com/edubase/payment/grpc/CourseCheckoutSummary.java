package com.edubase.payment.grpc;

import java.math.BigDecimal;

public record CourseCheckoutSummary(
        String courseId,
        String title,
        BigDecimal price,
        String currency
) {
}
