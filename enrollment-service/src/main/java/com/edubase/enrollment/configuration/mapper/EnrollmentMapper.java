package com.edubase.enrollment.configuration.mapper;

import com.edubase.enrollment.dto.request.EnrollmentCreateRequest;
import com.edubase.enrollment.dto.response.EnrollmentResponse;
import com.edubase.enrollment.entity.Enrollment;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE,
        builder = @Builder(disableBuilder = true)
)
public interface EnrollmentMapper extends BaseMapper<Enrollment, EnrollmentResponse, EnrollmentCreateRequest> {

    @Override
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    Enrollment toEntityFromRequest(EnrollmentCreateRequest dto);
}
