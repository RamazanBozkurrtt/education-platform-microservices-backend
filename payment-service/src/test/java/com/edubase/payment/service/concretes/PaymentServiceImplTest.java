package com.edubase.payment.service.concretes;

import com.edubase.commonCore.exceptions.BusinessException;
import com.edubase.commonCore.exceptions.ErrorCode;
import com.edubase.payment.configuration.mapper.InvoiceMapper;
import com.edubase.payment.configuration.mapper.PaymentMapper;
import com.edubase.payment.dto.request.PaymentConfirmRequest;
import com.edubase.payment.dto.response.PaymentResponse;
import com.edubase.payment.entity.Invoice;
import com.edubase.payment.entity.Payment;
import com.edubase.payment.entity.PaymentMethod;
import com.edubase.payment.entity.PaymentProvider;
import com.edubase.payment.entity.PaymentStatus;
import com.edubase.payment.grpc.CourseGrpcClient;
import com.edubase.payment.grpc.UserGrpcClient;
import com.edubase.payment.repository.InvoiceRepository;
import com.edubase.payment.repository.PaymentRepository;
import com.edubase.payment.security.AuthContext;
import com.edubase.payment.security.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    private static final String WEBHOOK_SECRET = "test-webhook-secret";

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private InvoiceRepository invoiceRepository;

    @Mock
    private PaymentMapper paymentMapper;

    @Mock
    private InvoiceMapper invoiceMapper;

    @Mock
    private UserGrpcClient userGrpcClient;

    @Mock
    private CourseGrpcClient courseGrpcClient;

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @InjectMocks
    private PaymentServiceImpl paymentService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(paymentService, "gatewayWebhookSecret", WEBHOOK_SECRET);
        ReflectionTestUtils.setField(paymentService, "allowedClockSkewSeconds", 300L);
        ReflectionTestUtils.setField(paymentService, "mockConfirmEnabled", false);
    }

    @Test
    void confirmPayment_shouldRejectWhenGatewaySignatureMissing() {
        Payment payment = pendingPayment();
        AuthContext authContext = new AuthContext("1", UserRole.STUDENT);
        PaymentConfirmRequest request = PaymentConfirmRequest.builder()
                .approved(true)
                .gatewayTransactionId("mock_tx_1")
                .gatewayTimestampEpochSeconds(Instant.now().getEpochSecond())
                .gatewaySignature(null)
                .build();

        when(paymentRepository.findById(10L)).thenReturn(Optional.of(payment));

        assertThrows(BusinessException.class, () -> paymentService.confirmPayment(authContext, 10L, request));
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    void confirmPayment_shouldSucceedWhenGatewaySignatureIsValid() {
        Payment payment = pendingPayment();
        AuthContext authContext = new AuthContext("1", UserRole.STUDENT);
        long timestamp = Instant.now().getEpochSecond();
        String gatewayTransactionId = "mock_tx_2";
        String signature = computeSignature(payment, gatewayTransactionId, true, timestamp);

        PaymentConfirmRequest request = PaymentConfirmRequest.builder()
                .approved(true)
                .gatewayTransactionId(gatewayTransactionId)
                .gatewayTimestampEpochSeconds(timestamp)
                .gatewaySignature(signature)
                .build();

        Payment savedPayment = pendingPayment();
        savedPayment.setId(10L);
        savedPayment.setStatus(PaymentStatus.SUCCEEDED);
        PaymentResponse response = PaymentResponse.builder()
                .id(10L)
                .status(PaymentStatus.SUCCEEDED)
                .build();

        when(paymentRepository.findById(10L)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenReturn(savedPayment);
        when(invoiceRepository.findByPaymentId(10L)).thenReturn(Optional.empty());
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentMapper.toResponseFromEntity(savedPayment)).thenReturn(response);

        PaymentResponse result = paymentService.confirmPayment(authContext, 10L, request);

        assertEquals(PaymentStatus.SUCCEEDED, result.getStatus());
        verify(paymentRepository).save(any(Payment.class));
    }

    @Test
    void confirmPayment_shouldSucceedWithoutGatewayPayload_whenMockBypassEnabled() {
        Payment payment = pendingPayment();
        AuthContext authContext = new AuthContext("1", UserRole.STUDENT);
        ReflectionTestUtils.setField(paymentService, "mockConfirmEnabled", true);

        Payment savedPayment = pendingPayment();
        savedPayment.setId(10L);
        savedPayment.setStatus(PaymentStatus.SUCCEEDED);
        PaymentResponse response = PaymentResponse.builder()
                .id(10L)
                .status(PaymentStatus.SUCCEEDED)
                .build();

        when(paymentRepository.findById(10L)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenReturn(savedPayment);
        when(invoiceRepository.findByPaymentId(10L)).thenReturn(Optional.empty());
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentMapper.toResponseFromEntity(savedPayment)).thenReturn(response);

        PaymentResponse result = paymentService.confirmPayment(authContext, 10L, null);

        assertEquals(PaymentStatus.SUCCEEDED, result.getStatus());
        verify(paymentRepository).save(any(Payment.class));
    }

    @Test
    void confirmPayment_shouldRejectWhenOwnerMismatch() {
        Payment payment = pendingPayment();
        AuthContext authContext = new AuthContext("2", UserRole.STUDENT);

        when(paymentRepository.findById(10L)).thenReturn(Optional.of(payment));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> paymentService.confirmPayment(authContext, 10L, null));

        assertEquals(ErrorCode.PAYMENT_OWNER_MISMATCH, ex.getErrorCode());
    }

    private Payment pendingPayment() {
        Payment payment = Payment.builder()
                .userId(1L)
                .courseId("course-1")
                .courseTitleSnapshot("Course")
                .amount(BigDecimal.valueOf(199.99))
                .currency("TRY")
                .status(PaymentStatus.PENDING)
                .provider(PaymentProvider.MOCK_GATEWAY)
                .paymentMethod(PaymentMethod.CARD)
                .providerPaymentId("PAY-TEST-123")
                .build();
        payment.setId(10L);
        return payment;
    }

    private String computeSignature(Payment payment, String gatewayTransactionId, boolean approved, long timestamp) {
        String payload = String.join("|",
                String.valueOf(payment.getId()),
                payment.getProvider().name(),
                payment.getProviderPaymentId(),
                String.valueOf(payment.getUserId()),
                payment.getCourseId(),
                payment.getAmount().toPlainString(),
                payment.getCurrency(),
                gatewayTransactionId,
                String.valueOf(approved),
                String.valueOf(timestamp)
        );
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(WEBHOOK_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getEncoder().encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}
