package com.edubase.payment.configuration.mapper;

import com.edubase.payment.dto.request.PaymentCreateRequest;
import com.edubase.payment.dto.response.PaymentResponse;
import com.edubase.payment.entity.Payment;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE,
        builder = @Builder(disableBuilder = true)
)
public interface PaymentMapper extends BaseMapper<Payment, PaymentResponse, PaymentCreateRequest> {

    @Override
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "courseId", ignore = true)
    @Mapping(target = "courseTitleSnapshot", ignore = true)
    @Mapping(target = "amount", ignore = true)
    @Mapping(target = "currency", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "providerPaymentId", ignore = true)
    @Mapping(target = "paidAt", ignore = true)
    @Mapping(target = "refundedAt", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    Payment toEntityFromRequest(PaymentCreateRequest dto);
}
