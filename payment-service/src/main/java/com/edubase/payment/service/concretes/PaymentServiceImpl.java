package com.edubase.payment.service.concretes;

import com.edubase.commonCore.exceptions.BusinessException;
import com.edubase.commonCore.exceptions.ErrorCode;
import com.edubase.commonCore.utils.TsidUtil;
import com.edubase.payment.configuration.mapper.InvoiceMapper;
import com.edubase.payment.configuration.mapper.PaymentMapper;
import com.edubase.payment.dto.request.PaymentConfirmRequest;
import com.edubase.payment.dto.request.PaymentCreateRequest;
import com.edubase.payment.dto.request.PaymentStatusUpdateRequest;
import com.edubase.payment.dto.response.CustomPageResponse;
import com.edubase.payment.dto.response.InvoiceResponse;
import com.edubase.payment.dto.response.PaymentResponse;
import com.edubase.payment.entity.Invoice;
import com.edubase.payment.entity.Payment;
import com.edubase.payment.entity.PaymentProvider;
import com.edubase.payment.entity.PaymentStatus;
import com.edubase.payment.exception.InvoiceNotFoundException;
import com.edubase.payment.exception.PaymentAlreadyCompletedException;
import com.edubase.payment.exception.PaymentAlreadyRefundedException;
import com.edubase.payment.exception.PaymentNotFoundException;
import com.edubase.payment.grpc.CourseCheckoutSummary;
import com.edubase.payment.grpc.CourseGrpcClient;
import com.edubase.payment.grpc.UserGrpcClient;
import com.edubase.payment.messaging.PaymentRefundedDomainEvent;
import com.edubase.payment.messaging.PaymentSucceededDomainEvent;
import com.edubase.payment.repository.InvoiceRepository;
import com.edubase.payment.repository.PaymentRepository;
import com.edubase.payment.security.AuthContext;
import com.edubase.payment.security.UserRole;
import com.edubase.payment.service.abstracts.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {
    private static final String HMAC_SHA256 = "HmacSHA256";


    private final PaymentRepository paymentRepository;
    private final InvoiceRepository invoiceRepository;
    private final PaymentMapper paymentMapper;
    private final InvoiceMapper invoiceMapper;
    private final UserGrpcClient userGrpcClient;
    private final CourseGrpcClient courseGrpcClient;
    private final ApplicationEventPublisher applicationEventPublisher;

    @Value("${payment.gateway.webhook-secret:${PAYMENT_GATEWAY_WEBHOOK_SECRET:local-payment-webhook-secret-change-me}}")
    private String gatewayWebhookSecret;

    @Value("${payment.gateway.allowed-clock-skew-seconds:300}")
    private long allowedClockSkewSeconds;

    @Value("${payment.gateway.mock-confirm-enabled:false}")
    private boolean mockConfirmEnabled;

    @Override
    @Transactional
    @PreAuthorize("@paymentSecurity.canCreatePayment(#authContext, #request)")
    public PaymentResponse createPayment(AuthContext authContext, PaymentCreateRequest request) {
        requireAuthenticatedRole(authContext);
        Long actorUserId = requireUserId(authContext);
        Long targetUserId = request.getUserId() != null ? request.getUserId() : actorUserId;
        if (!isAdmin(authContext) && !actorUserId.equals(targetUserId)) {
            throw new AccessDeniedException("Cannot create payment for another user");
        }

        String courseId = normalizeCourseId(request.getCourseId());
        String idempotencyKey = normalizeOptional(request.getIdempotencyKey());
        if (idempotencyKey != null) {
            Payment existing = paymentRepository.findByUserIdAndCourseIdAndIdempotencyKey(targetUserId, courseId, idempotencyKey)
                    .orElse(null);
            if (existing != null) {
                return toResponse(existing);
            }
        }

        userGrpcClient.assertUserExists(targetUserId);
        CourseCheckoutSummary course = courseGrpcClient.getPublishedCourse(courseId);
        if (course.price() == null || course.price().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.PAYMENT_NOT_REQUIRED);
        }

        Payment payment = paymentMapper.toEntityFromRequest(request);
        payment.setUserId(targetUserId);
        payment.setCourseId(courseId);
        payment.setCourseTitleSnapshot(defaultIfBlank(course.title(), courseId));
        payment.setAmount(course.price());
        payment.setCurrency(normalizeCurrency(course.currency()));
        payment.setStatus(PaymentStatus.PENDING);
        payment.setProviderPaymentId("PAY-" + TsidUtil.generateId());
        payment.setIdempotencyKey(idempotencyKey);
        payment.setFailureReason(null);

        Payment saved = paymentRepository.save(payment);
        boolean autoConfirm = request.getAutoConfirm() != null && request.getAutoConfirm();
        if (autoConfirm) {
            if (!isAdmin(authContext)) {
                throw new AccessDeniedException("autoConfirm is restricted to administrative flows");
            }
            saved = markPaymentSucceeded(saved, toBillingDetails(request));
        }
        return toResponse(saved);
    }

    @Override
    @PreAuthorize("@paymentSecurity.canAccessPayment(#authContext, #id)")
    public PaymentResponse getPaymentById(AuthContext authContext, Long id) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(PaymentNotFoundException::new);
        return toResponse(payment);
    }

    @Override
    @Transactional
    @PreAuthorize("@paymentSecurity.isAuthenticatedUser(#authContext)")
    public PaymentResponse confirmPayment(AuthContext authContext, Long id, PaymentConfirmRequest request) {
        Payment payment = paymentRepository.findById(id).orElseThrow(PaymentNotFoundException::new);
        requireConfirmOwner(authContext, payment);
        requirePendingStatus(payment);

        if (isMockConfirmBypassEnabled(payment)) {
            payment = markPaymentSucceeded(payment, toBillingDetails(request));
            return toResponse(payment);
        }

        if (request == null) {
            throw new BusinessException(ErrorCode.MISSING_CONFIRMATION_PAYLOAD);
        }

        Boolean approved = request.getApproved();
        if (approved == null) {
            throw new BusinessException(ErrorCode.INVALID_PROVIDER_STATUS);
        }

        validateGatewayConfirmation(payment, request, approved);
        if (approved) {
            payment = markPaymentSucceeded(payment, toBillingDetails(request));
        } else {
            payment = markPaymentFailed(payment, request.getFailureReason());
        }
        return toResponse(payment);
    }

    @Override
    @PreAuthorize("@paymentSecurity.isAuthenticatedUser(#authContext)")
    public CustomPageResponse<PaymentResponse> getMyPayments(AuthContext authContext, int pageNumber, int pageSize) {
        requireAuthenticatedRole(authContext);
        Long userId = requireUserId(authContext);
        PageRequest pageRequest = PageRequest.of(pageNumber, pageSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Payment> page = paymentRepository.findAllByUserId(userId, pageRequest);
        List<PaymentResponse> responses = page.getContent().stream().map(this::toResponse).toList();
        return CustomPageResponse.of(page, responses);
    }

    @Override
    @PreAuthorize("@paymentSecurity.isAdmin(#authContext)")
    public CustomPageResponse<PaymentResponse> getPaymentsByCourse(AuthContext authContext, String courseId, int pageNumber, int pageSize) {
        String normalizedCourseId = normalizeCourseId(courseId);
        PageRequest pageRequest = PageRequest.of(pageNumber, pageSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Payment> page = paymentRepository.findAllByCourseId(normalizedCourseId, pageRequest);
        List<PaymentResponse> responses = page.getContent().stream().map(this::toResponse).toList();
        return CustomPageResponse.of(page, responses);
    }

    @Override
    @Transactional
    @PreAuthorize("@paymentSecurity.isAdmin(#authContext)")
    public PaymentResponse updatePaymentStatus(AuthContext authContext, Long id, PaymentStatusUpdateRequest request) {
        Payment payment = paymentRepository.findById(id).orElseThrow(PaymentNotFoundException::new);
        PaymentStatus targetStatus = request.getStatus();
        if (targetStatus == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }

        switch (targetStatus) {
            case SUCCEEDED -> payment = markPaymentSucceeded(payment, BillingDetails.empty());
            case REFUNDED -> payment = markPaymentRefunded(payment);
            case FAILED -> payment = markPaymentFailed(payment, request.getFailureReason());
            case PENDING -> throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        return toResponse(payment);
    }

    @Override
    @PreAuthorize("@paymentSecurity.canAccessPayment(#authContext, #paymentId)")
    public InvoiceResponse getInvoiceByPaymentId(AuthContext authContext, Long paymentId) {
        paymentRepository.findById(paymentId).orElseThrow(PaymentNotFoundException::new);
        Invoice invoice = invoiceRepository.findByPaymentId(paymentId)
                .orElseThrow(InvoiceNotFoundException::new);
        return invoiceMapper.toResponseFromEntity(invoice);
    }

    private void validateGatewayConfirmation(Payment payment, PaymentConfirmRequest request, boolean approved) {
        String gatewayTransactionId = normalizeOptional(request.getGatewayTransactionId());
        String providedSignature = normalizeOptional(request.getGatewaySignature());
        Long timestamp = request.getGatewayTimestampEpochSeconds();

        if (gatewayTransactionId == null || providedSignature == null || timestamp == null || timestamp <= 0L) {
            throw new BusinessException(ErrorCode.MISSING_CONFIRMATION_PAYLOAD);
        }

        if (!isGatewayTransactionIdCompatible(payment.getProvider(), gatewayTransactionId)) {
            throw new BusinessException(ErrorCode.INVALID_PROVIDER_STATUS);
        }

        long nowEpochSeconds = Instant.now().getEpochSecond();
        long driftSeconds = Math.abs(nowEpochSeconds - timestamp);
        if (driftSeconds > Math.max(30L, allowedClockSkewSeconds)) {
            throw new BusinessException(ErrorCode.INVALID_SIGNATURE);
        }

        String expected = computeGatewaySignature(payment, gatewayTransactionId, approved, timestamp);
        byte[] expectedBytes = expected.getBytes(StandardCharsets.UTF_8);
        byte[] providedBytes = providedSignature.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expectedBytes, providedBytes)) {
            throw new BusinessException(ErrorCode.INVALID_SIGNATURE);
        }
    }

    private boolean isGatewayTransactionIdCompatible(PaymentProvider provider, String gatewayTransactionId) {
        if (provider == null || gatewayTransactionId == null) {
            return false;
        }

        return switch (provider) {
            case STRIPE -> gatewayTransactionId.startsWith("pi_") || gatewayTransactionId.startsWith("ch_");
            case IYZICO -> gatewayTransactionId.startsWith("iyz_") || gatewayTransactionId.startsWith("pay_");
            case MOCK_GATEWAY -> gatewayTransactionId.startsWith("mock_");
        };
    }

    private String computeGatewaySignature(Payment payment, String gatewayTransactionId, boolean approved, long timestamp) {
        String secret = normalizeOptional(gatewayWebhookSecret);
        if (secret == null) {
            throw new BusinessException(ErrorCode.INVALID_SIGNATURE);
        }

        String payload = String.join("|",
                String.valueOf(payment.getId()),
                payment.getProvider() == null ? "" : payment.getProvider().name(),
                payment.getProviderPaymentId() == null ? "" : payment.getProviderPaymentId(),
                String.valueOf(payment.getUserId()),
                payment.getCourseId() == null ? "" : payment.getCourseId(),
                payment.getAmount() == null ? "" : payment.getAmount().toPlainString(),
                payment.getCurrency() == null ? "" : payment.getCurrency(),
                gatewayTransactionId,
                String.valueOf(approved),
                String.valueOf(timestamp)
        );

        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.INVALID_SIGNATURE, ex);
        }
    }

    private void requireConfirmOwner(AuthContext authContext, Payment payment) {
        if (isAdmin(authContext)) {
            return;
        }
        Long actorUserId = requireUserId(authContext);
        if (!actorUserId.equals(payment.getUserId())) {
            throw new BusinessException(ErrorCode.PAYMENT_OWNER_MISMATCH);
        }
    }

    private void requirePendingStatus(Payment payment) {
        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new BusinessException(ErrorCode.PAYMENT_NOT_PENDING);
        }
    }

    private boolean isMockConfirmBypassEnabled(Payment payment) {
        return mockConfirmEnabled && payment != null && payment.getProvider() == PaymentProvider.MOCK_GATEWAY;
    }

    private Payment markPaymentSucceeded(Payment payment, BillingDetails billingDetails) {
        if (payment.getStatus() == PaymentStatus.SUCCEEDED) {
            throw new PaymentAlreadyCompletedException();
        }
        if (payment.getStatus() == PaymentStatus.REFUNDED) {
            throw new PaymentAlreadyRefundedException();
        }
        if (payment.getStatus() == PaymentStatus.FAILED) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }

        payment.setStatus(PaymentStatus.SUCCEEDED);
        payment.setPaidAt(LocalDateTime.now());
        payment.setFailureReason(null);
        Payment saved = paymentRepository.save(payment);
        Invoice invoice = createInvoiceIfAbsent(saved, billingDetails == null ? BillingDetails.empty() : billingDetails);

        applicationEventPublisher.publishEvent(new PaymentSucceededDomainEvent(
                saved.getId(),
                saved.getUserId(),
                saved.getCourseId(),
                saved.getAmount(),
                saved.getCurrency(),
                invoice.getInvoiceNumber()
        ));
        return saved;
    }

    private Payment markPaymentFailed(Payment payment, String failureReason) {
        if (payment.getStatus() == PaymentStatus.SUCCEEDED) {
            throw new PaymentAlreadyCompletedException();
        }
        if (payment.getStatus() == PaymentStatus.REFUNDED) {
            throw new PaymentAlreadyRefundedException();
        }
        payment.setStatus(PaymentStatus.FAILED);
        payment.setFailureReason(normalizeFailureReason(failureReason));
        return paymentRepository.save(payment);
    }

    private Payment markPaymentRefunded(Payment payment) {
        if (payment.getStatus() == PaymentStatus.REFUNDED) {
            throw new PaymentAlreadyRefundedException();
        }
        if (payment.getStatus() != PaymentStatus.SUCCEEDED) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }

        payment.setStatus(PaymentStatus.REFUNDED);
        payment.setRefundedAt(LocalDateTime.now());
        Payment saved = paymentRepository.save(payment);
        applicationEventPublisher.publishEvent(new PaymentRefundedDomainEvent(
                saved.getId(),
                saved.getUserId(),
                saved.getCourseId(),
                saved.getAmount(),
                saved.getCurrency()
        ));
        return saved;
    }

    private Invoice createInvoiceIfAbsent(Payment payment, BillingDetails billingDetails) {
        Invoice existing = invoiceRepository.findByPaymentId(payment.getId()).orElse(null);
        if (existing != null) {
            return existing;
        }

        Invoice invoice = Invoice.builder()
                .paymentId(payment.getId())
                .invoiceNumber("INV-" + TsidUtil.generateId())
                .invoiceDate(LocalDateTime.now())
                .buyerFullName(billingDetails.buyerFullName())
                .buyerEmail(billingDetails.buyerEmail())
                .buyerTaxNumber(billingDetails.buyerTaxNumber())
                .buyerAddress(billingDetails.buyerAddress())
                .courseTitleSnapshot(payment.getCourseTitleSnapshot())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .build();
        return invoiceRepository.save(invoice);
    }

    private PaymentResponse toResponse(Payment payment) {
        PaymentResponse response = paymentMapper.toResponseFromEntity(payment);
        invoiceRepository.findByPaymentId(payment.getId())
                .ifPresent(invoice -> response.setInvoiceNumber(invoice.getInvoiceNumber()));
        return response;
    }

    private BillingDetails toBillingDetails(PaymentCreateRequest request) {
        if (request == null) {
            return BillingDetails.empty();
        }
        return new BillingDetails(
                normalizeOptional(request.getBuyerFullName()),
                normalizeOptional(request.getBuyerEmail()),
                normalizeOptional(request.getBuyerTaxNumber()),
                normalizeOptional(request.getBuyerAddress())
        );
    }

    private BillingDetails toBillingDetails(PaymentConfirmRequest request) {
        if (request == null) {
            return BillingDetails.empty();
        }
        return new BillingDetails(
                normalizeOptional(request.getBuyerFullName()),
                normalizeOptional(request.getBuyerEmail()),
                normalizeOptional(request.getBuyerTaxNumber()),
                normalizeOptional(request.getBuyerAddress())
        );
    }

    private String normalizeFailureReason(String failureReason) {
        String normalized = normalizeOptional(failureReason);
        return normalized == null ? "Simulated gateway decline" : normalized;
    }

    private void requireAuthenticatedRole(AuthContext authContext) {
        if (authContext == null || authContext.role() == UserRole.UNKNOWN) {
            throw new AccessDeniedException("Role required");
        }
    }

    private Long requireUserId(AuthContext authContext) {
        if (authContext == null || authContext.userId() == null || authContext.userId().isBlank()) {
            throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED);
        }
        try {
            return Long.parseLong(authContext.userId().trim());
        } catch (NumberFormatException ex) {
            throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED);
        }
    }

    private boolean isAdmin(AuthContext authContext) {
        return authContext != null && authContext.role() == UserRole.ADMIN;
    }

    private String normalizeCourseId(String courseId) {
        if (courseId == null || courseId.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        return courseId.trim();
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private String normalizeCurrency(String currency) {
        String normalized = normalizeOptional(currency);
        if (normalized == null) {
            return "TRY";
        }
        String upper = normalized.toUpperCase();
        return upper.length() > 3 ? upper.substring(0, 3) : upper;
    }

    private String defaultIfBlank(String value, String fallback) {
        String normalized = normalizeOptional(value);
        return normalized == null ? fallback : normalized;
    }

    private record BillingDetails(
            String buyerFullName,
            String buyerEmail,
            String buyerTaxNumber,
            String buyerAddress
    ) {
        private static BillingDetails empty() {
            return new BillingDetails(null, null, null, null);
        }
    }
}
