package com.edubase.enrollment.grpc;

import com.edubase.commonCore.exceptions.BusinessException;
import com.edubase.commonCore.exceptions.ErrorCode;
import com.edubase.contracts.payment.v1.PaymentQueryServiceGrpc;
import com.edubase.contracts.payment.v1.SuccessfulPaymentRequest;
import com.edubase.contracts.payment.v1.SuccessfulPaymentResponse;
import io.grpc.StatusRuntimeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentGrpcClient {

    private final PaymentQueryServiceGrpc.PaymentQueryServiceBlockingStub paymentQueryServiceBlockingStub;

    public void assertSuccessfulPayment(Long userId, String courseId) {
        if (userId == null || userId <= 0 || courseId == null || courseId.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }

        try {
            SuccessfulPaymentResponse response = paymentQueryServiceBlockingStub
                    .withDeadlineAfter(1500, TimeUnit.MILLISECONDS)
                    .hasSuccessfulPayment(
                            SuccessfulPaymentRequest.newBuilder()
                                    .setUserId(userId)
                                    .setCourseId(courseId)
                                    .build()
                    );
            if (!response.getHasSuccessfulPayment()) {
                throw new BusinessException(ErrorCode.PAYMENT_REQUIRED_FOR_ENROLLMENT);
            }
        } catch (StatusRuntimeException ex) {
            log.error("gRPC call to payment-service failed for userId={} courseId={}", userId, courseId, ex);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
    }
}
