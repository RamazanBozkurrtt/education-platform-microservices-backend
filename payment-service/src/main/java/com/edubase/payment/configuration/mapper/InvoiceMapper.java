package com.edubase.payment.configuration.mapper;

import com.edubase.payment.dto.response.InvoiceResponse;
import com.edubase.payment.entity.Invoice;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE,
        builder = @Builder(disableBuilder = true)
)
public interface InvoiceMapper {

    InvoiceResponse toResponseFromEntity(Invoice entity);
}
