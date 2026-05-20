package com.edubase.enrollment.grpc;

import com.edubase.commonCore.exceptions.BusinessException;
import com.edubase.commonCore.exceptions.ErrorCode;
import com.edubase.contracts.course.v1.CourseByIdRequest;
import com.edubase.contracts.course.v1.CourseByIdResponse;
import com.edubase.contracts.course.v1.CourseQueryServiceGrpc;
import io.grpc.StatusRuntimeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class CourseGrpcClient {

    private final CourseQueryServiceGrpc.CourseQueryServiceBlockingStub courseQueryServiceBlockingStub;

    public CourseSummary getPublishedCourse(String courseId) {
        if (courseId == null || courseId.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }

        try {
            CourseByIdResponse response = courseQueryServiceBlockingStub
                    .withDeadlineAfter(1500, TimeUnit.MILLISECONDS)
                    .getCourseById(CourseByIdRequest.newBuilder().setCourseId(courseId).build());

            if (!response.getExists() || !response.getPublished()) {
                throw new BusinessException(ErrorCode.COURSE_NOT_FOUND);
            }
            BigDecimal price = parsePrice(response.getPrice());
            String currency = response.getCurrency() == null || response.getCurrency().isBlank()
                    ? "TRY"
                    : response.getCurrency().trim();
            return new CourseSummary(courseId, price, currency);
        } catch (StatusRuntimeException ex) {
            log.error("gRPC call to course-service failed for courseId={}", courseId, ex);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
    }

    private BigDecimal parsePrice(String value) {
        if (value == null || value.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException ex) {
            return BigDecimal.ZERO;
        }
    }
}
