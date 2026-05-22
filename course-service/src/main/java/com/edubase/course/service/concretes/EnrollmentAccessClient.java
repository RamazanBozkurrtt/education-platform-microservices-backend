package com.edubase.course.service.concretes;

import com.edubase.commonCore.utils.RestResponse;
import com.edubase.course.dto.internal.EnrollmentAccessResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
@Component
@RequiredArgsConstructor
public class EnrollmentAccessClient {

    private static final String INTERNAL_API_KEY_HEADER = "X-Internal-Api-Key";

    private final RestClient internalEnrollmentServiceRestClient;

    @Value("${app.internal.api-key}")
    private String internalApiKey;

    public boolean hasActiveEnrollment(String userId, String courseId) {
        if (userId == null || userId.isBlank() || courseId == null || courseId.isBlank()) {
            return false;
        }
        try {
            RestResponse<EnrollmentAccessResponse> response = internalEnrollmentServiceRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/internal/enrollments/access")
                            .queryParam("userId", userId)
                            .queryParam("courseId", courseId)
                            .build())
                    .header(INTERNAL_API_KEY_HEADER, internalApiKey)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});

            return response != null
                    && response.getData() != null
                    && response.getData().isEnrolled();
        } catch (RestClientException ex) {
            log.warn("Failed to validate enrollment for userId={} courseId={}", userId, courseId, ex);
            return false;
        }
    }
}
