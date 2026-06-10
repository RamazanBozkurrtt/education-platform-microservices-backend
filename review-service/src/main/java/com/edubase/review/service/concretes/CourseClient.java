package com.edubase.review.service.concretes;

import com.edubase.commonCore.exceptions.BusinessException;
import com.edubase.commonCore.exceptions.ErrorCode;
import com.edubase.commonCore.utils.RestResponse;
import com.edubase.review.dto.internal.CourseLookupResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Slf4j
@Component
@RequiredArgsConstructor
public class CourseClient {

    private final RestClient courseServiceRestClient;

    public CourseLookupResponse getPublishedCourseById(String courseId) {
        try {
            RestResponse<CourseLookupResponse> response = courseServiceRestClient.get()
                    .uri("/api/v1/courses/public/{id}", courseId)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });

            if (response == null || response.getData() == null) {
                throw new BusinessException(ErrorCode.COURSE_NOT_FOUND);
            }
            return response.getData();
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 404) {
                throw new BusinessException(ErrorCode.COURSE_NOT_FOUND);
            }
            log.error("Course-service call failed for courseId={} status={}", courseId, ex.getStatusCode(), ex);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        } catch (Exception ex) {
            if (ex instanceof BusinessException) {
                throw ex;
            }
            log.error("Course-service call failed for courseId={}", courseId, ex);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
    }
}
