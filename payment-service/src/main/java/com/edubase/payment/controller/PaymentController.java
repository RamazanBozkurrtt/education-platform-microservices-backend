package com.edubase.payment.controller;

import com.edubase.commonCore.utils.RestResponse;
import com.edubase.payment.controller.base.RestBaseController;
import com.edubase.payment.dto.request.PaymentConfirmRequest;
import com.edubase.payment.dto.request.PaymentCreateRequest;
import com.edubase.payment.dto.request.PaymentStatusUpdateRequest;
import com.edubase.payment.dto.response.CustomPageResponse;
import com.edubase.payment.dto.response.InvoiceResponse;
import com.edubase.payment.dto.response.PaymentResponse;
import com.edubase.payment.security.AuthContext;
import com.edubase.payment.security.AuthContextResolver;
import com.edubase.payment.service.abstracts.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/payments")
@Tag(name = "Payments", description = "Payment and invoice endpoints")
@SecurityRequirement(name = "bearerAuth")
public class PaymentController extends RestBaseController {

    private final PaymentService paymentService;
    private final AuthContextResolver authContextResolver;

    @PostMapping
    @Operation(summary = "Create payment", description = "Creates a payment record for a published paid course.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Payment created"),
            @ApiResponse(responseCode = "400", description = "Validation failed"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "409", description = "Conflict")
    })
    public ResponseEntity<RestResponse<PaymentResponse>> createPayment(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody @Valid PaymentCreateRequest request) {
        AuthContext authContext = authContextResolver.requireAuth(jwt);
        return created(paymentService.createPayment(authContext, request));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get payment by id", description = "Returns payment details by id.")
    public ResponseEntity<RestResponse<PaymentResponse>> getPaymentById(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id) {
        AuthContext authContext = authContextResolver.requireAuth(jwt);
        return ok(paymentService.getPaymentById(authContext, id));
    }

    @PostMapping("/{id}/confirm")
    @Operation(summary = "Confirm payment", description = "Confirms or declines a pending payment (simulated gateway result).")
    public ResponseEntity<RestResponse<PaymentResponse>> confirmPayment(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id,
            @RequestBody @Valid PaymentConfirmRequest request) {
        AuthContext authContext = authContextResolver.requireAuth(jwt);
        return ok(paymentService.confirmPayment(authContext, id, request));
    }

    @GetMapping("/me")
    @Operation(summary = "List my payments", description = "Returns paginated payments for authenticated user.")
    public ResponseEntity<RestResponse<CustomPageResponse<PaymentResponse>>> getMyPayments(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int pageNumber,
            @RequestParam(defaultValue = "10") int pageSize) {
        AuthContext authContext = authContextResolver.requireAuth(jwt);
        return ok(paymentService.getMyPayments(authContext, pageNumber, pageSize));
    }

    @GetMapping("/by-course/{courseId}")
    @Operation(summary = "List payments by course", description = "Returns paginated payments for a course (admin only).")
    public ResponseEntity<RestResponse<CustomPageResponse<PaymentResponse>>> getPaymentsByCourse(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String courseId,
            @RequestParam(defaultValue = "0") int pageNumber,
            @RequestParam(defaultValue = "10") int pageSize) {
        AuthContext authContext = authContextResolver.requireAuth(jwt);
        return ok(paymentService.getPaymentsByCourse(authContext, courseId, pageNumber, pageSize));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Update payment status", description = "Updates payment status (admin only).")
    public ResponseEntity<RestResponse<PaymentResponse>> updatePaymentStatus(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id,
            @RequestBody @Valid PaymentStatusUpdateRequest request) {
        AuthContext authContext = authContextResolver.requireAuth(jwt);
        return ok(paymentService.updatePaymentStatus(authContext, id, request));
    }

    @GetMapping("/{id}/invoice")
    @Operation(summary = "Get invoice for payment", description = "Returns invoice for a payment if it exists.")
    public ResponseEntity<RestResponse<InvoiceResponse>> getInvoiceByPaymentId(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id) {
        AuthContext authContext = authContextResolver.requireAuth(jwt);
        return ok(paymentService.getInvoiceByPaymentId(authContext, id));
    }
}
