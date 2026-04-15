package com.edubase.enrollment.service.abstracts;

import com.edubase.enrollment.dto.request.EnrollmentCreateRequest;
import com.edubase.enrollment.dto.response.CustomPageResponse;
import com.edubase.enrollment.dto.response.EnrollmentResponse;
import com.edubase.enrollment.security.AuthContext;

public interface EnrollmentService {

    EnrollmentResponse createEnrollment(AuthContext authContext, EnrollmentCreateRequest request);

    EnrollmentResponse getEnrollmentById(AuthContext authContext, Long id);

    CustomPageResponse<EnrollmentResponse> getMyEnrollments(AuthContext authContext, int pageNumber, int pageSize);

    CustomPageResponse<EnrollmentResponse> getEnrollmentsByCourse(AuthContext authContext, String courseId, int pageNumber, int pageSize);

    void cancelEnrollment(AuthContext authContext, Long id);
}
