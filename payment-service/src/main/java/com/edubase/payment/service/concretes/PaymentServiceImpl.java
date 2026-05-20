package com.edubase.payment.service.concretes;

import com.edubase.commonCore.exceptions.BusinessException;
import com.edubase.commonCore.exceptions.ErrorCode;
import com.edubase.commonCore.utils.TsidUtil;
import com.edubase.payment.configuration.mapper.InvoiceMapper;
import com.edubase.payment.configuration.mapper.PaymentMapper;
import com.edubase.payment.dto.request.PaymentCreateRequest;
import com.edubase.payment.dto.request.PaymentStatusUpdateRequest;
import com.edubase.payment.dto.response.CustomPageResponse;
import com.edubase.payment.dto.response.InvoiceResponse;
import com.edubase.payment.dto.response.PaymentResponse;
import com.edubase.payment.entity.Invoice;
import com.edubase.payment.entity.Payment;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final InvoiceRepository invoiceRepository;
    private final PaymentMapper paymentMapper;
    private final InvoiceMapper invoiceMapper;
    private final UserGrpcClient userGrpcClient;
    private final CourseGrpcClient courseGrpcClient;
    private final ApplicationEventPublisher applicationEventPublisher;

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

        Payment saved = paymentRepository.save(payment);
        boolean autoConfirm = request.getAutoConfirm() == null || request.getAutoConfirm();
        if (autoConfirm) {
            saved = markPaymentSucceeded(saved, request);
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
            case SUCCEEDED -> payment = markPaymentSucceeded(payment, null);
            case REFUNDED -> payment = markPaymentRefunded(payment);
            case FAILED -> payment = markPaymentFailed(payment);
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

    private Payment markPaymentSucceeded(Payment payment, PaymentCreateRequest createRequest) {
        if (payment.getStatus() == PaymentStatus.SUCCEEDED) {
            throw new PaymentAlreadyCompletedException();
        }
        if (payment.getStatus() == PaymentStatus.REFUNDED) {
            throw new PaymentAlreadyRefundedException();
        }
        if (payment.getStatus() == PaymentStatus.FAILED) {
            throw new PaymentAlreadyCompletedException();
        }

        payment.setStatus(PaymentStatus.SUCCEEDED);
        payment.setPaidAt(LocalDateTime.now());
        Payment saved = paymentRepository.save(payment);
        Invoice invoice = createInvoiceIfAbsent(saved, createRequest);

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

    private Payment markPaymentFailed(Payment payment) {
        if (payment.getStatus() == PaymentStatus.SUCCEEDED) {
            throw new PaymentAlreadyCompletedException();
        }
        if (payment.getStatus() == PaymentStatus.REFUNDED) {
            throw new PaymentAlreadyRefundedException();
        }
        payment.setStatus(PaymentStatus.FAILED);
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

    private Invoice createInvoiceIfAbsent(Payment payment, PaymentCreateRequest createRequest) {
        Invoice existing = invoiceRepository.findByPaymentId(payment.getId()).orElse(null);
        if (existing != null) {
            return existing;
        }

        Invoice invoice = Invoice.builder()
                .paymentId(payment.getId())
                .invoiceNumber("INV-" + TsidUtil.generateId())
                .invoiceDate(LocalDateTime.now())
                .buyerFullName(createRequest == null ? null : normalizeOptional(createRequest.getBuyerFullName()))
                .buyerEmail(createRequest == null ? null : normalizeOptional(createRequest.getBuyerEmail()))
                .buyerTaxNumber(createRequest == null ? null : normalizeOptional(createRequest.getBuyerTaxNumber()))
                .buyerAddress(createRequest == null ? null : normalizeOptional(createRequest.getBuyerAddress()))
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
}
