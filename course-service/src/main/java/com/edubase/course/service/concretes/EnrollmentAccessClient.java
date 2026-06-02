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

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.stream.Collectors;

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

    public Map<String, Long> countSuccessfulEnrollmentsByCourseIds(Collection<String> courseIds) {
        LinkedHashSet<String> normalizedCourseIds = normalizeCourseIds(courseIds);
        if (normalizedCourseIds.isEmpty()) {
            return Map.of();
        }
        try {
            RestResponse<Map<String, Long>> response = internalEnrollmentServiceRestClient.post()
                    .uri("/api/v1/internal/enrollments/course-counts")
                    .header(INTERNAL_API_KEY_HEADER, internalApiKey)
                    .body(Map.of("courseIds", normalizedCourseIds))
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});

            if (response == null || response.getData() == null) {
                return zeroCounts(normalizedCourseIds);
            }
            return normalizedCourseIds.stream()
                    .collect(Collectors.toMap(
                            courseId -> courseId,
                            courseId -> normalizeCount(response.getData().get(courseId)),
                            (left, right) -> left,
                            LinkedHashMap::new
                    ));
        } catch (RestClientException ex) {
            log.warn("Failed to fetch enrollment counts for courseIds={}", normalizedCourseIds, ex);
            return zeroCounts(normalizedCourseIds);
        }
    }

    private LinkedHashSet<String> normalizeCourseIds(Collection<String> courseIds) {
        if (courseIds == null || courseIds.isEmpty()) {
            return new LinkedHashSet<>();
        }
        return courseIds.stream()
                .filter(courseId -> courseId != null && !courseId.isBlank())
                .map(String::trim)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Map<String, Long> zeroCounts(Collection<String> courseIds) {
        return courseIds.stream()
                .collect(Collectors.toMap(
                        courseId -> courseId,
                        courseId -> 0L,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
    }

    private long normalizeCount(Long count) {
        if (count == null || count < 0L) {
            return 0L;
        }
        return count;
    }
}
