package com.edubase.payment.service.abstracts;

import com.edubase.payment.dto.request.PaymentCreateRequest;
import com.edubase.payment.dto.request.PaymentStatusUpdateRequest;
import com.edubase.payment.dto.response.CustomPageResponse;
import com.edubase.payment.dto.response.InvoiceResponse;
import com.edubase.payment.dto.response.PaymentResponse;
import com.edubase.payment.security.AuthContext;

public interface PaymentService {

    PaymentResponse createPayment(AuthContext authContext, PaymentCreateRequest request);

    PaymentResponse getPaymentById(AuthContext authContext, Long id);

    CustomPageResponse<PaymentResponse> getMyPayments(AuthContext authContext, int pageNumber, int pageSize);

    CustomPageResponse<PaymentResponse> getPaymentsByCourse(AuthContext authContext, String courseId, int pageNumber, int pageSize);

    PaymentResponse updatePaymentStatus(AuthContext authContext, Long id, PaymentStatusUpdateRequest request);

    InvoiceResponse getInvoiceByPaymentId(AuthContext authContext, Long paymentId);
}
