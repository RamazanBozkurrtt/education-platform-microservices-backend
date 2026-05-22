package com.edubase.payment.grpc;

import com.edubase.contracts.payment.v1.PaymentQueryServiceGrpc;
import com.edubase.contracts.payment.v1.SuccessfulPaymentRequest;
import com.edubase.contracts.payment.v1.SuccessfulPaymentResponse;
import com.edubase.payment.entity.PaymentStatus;
import com.edubase.payment.repository.PaymentRepository;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PaymentQueryGrpcService extends PaymentQueryServiceGrpc.PaymentQueryServiceImplBase {

    private final PaymentRepository paymentRepository;

    @Override
    public void hasSuccessfulPayment(SuccessfulPaymentRequest request, StreamObserver<SuccessfulPaymentResponse> responseObserver) {
        Long userId = request.getUserId() <= 0 ? null : request.getUserId();
        String courseId = request.getCourseId() == null ? "" : request.getCourseId().trim();

        boolean hasSuccessfulPayment = false;
        Long paymentId = 0L;
        if (userId != null && !courseId.isBlank()) {
            hasSuccessfulPayment = paymentRepository.existsByUserIdAndCourseIdAndStatus(userId, courseId, PaymentStatus.SUCCEEDED);
            if (hasSuccessfulPayment) {
                paymentId = paymentRepository
                        .findFirstByUserIdAndCourseIdAndStatusOrderByCreatedAtDesc(userId, courseId, PaymentStatus.SUCCEEDED)
                        .map(payment -> payment.getId())
                        .orElse(0L);
            }
        }

        SuccessfulPaymentResponse response = SuccessfulPaymentResponse.newBuilder()
                .setHasSuccessfulPayment(hasSuccessfulPayment)
                .setPaymentId(paymentId)
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}
