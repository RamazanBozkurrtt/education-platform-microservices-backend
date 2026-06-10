package com.edubase.course.service.concretes;

import com.edubase.commonCore.utils.RestResponse;
import com.edubase.course.dto.internal.InstructorSummariesRequest;
import com.edubase.course.dto.response.InstructorSummaryResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserInstructorSummaryClient {

    private static final String INTERNAL_API_KEY_HEADER = "X-Internal-Api-Key";

    private final RestClient internalUserServiceRestClient;

    @Value("${app.internal.api-key}")
    private String internalApiKey;

    public Optional<InstructorSummaryResponse> getById(String instructorId) {
        try {
            RestResponse<InstructorSummaryResponse> response = internalUserServiceRestClient.get()
                    .uri("/api/v1/internal/instructors/{id}/summary", instructorId)
                    .header(INTERNAL_API_KEY_HEADER, internalApiKey)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            if (response == null || response.getData() == null) {
                return Optional.empty();
            }
            return Optional.of(response.getData());
        } catch (RestClientException ex) {
            log.warn("Failed to fetch instructor summary from user-service for id={}", instructorId, ex);
            return Optional.empty();
        }
    }

    public List<InstructorSummaryResponse> getByIds(List<String> instructorIds) {
        if (instructorIds == null || instructorIds.isEmpty()) {
            return List.of();
        }
        try {
            RestResponse<List<InstructorSummaryResponse>> response = internalUserServiceRestClient.post()
                    .uri("/api/v1/internal/instructors/summaries")
                    .header(INTERNAL_API_KEY_HEADER, internalApiKey)
                    .body(new InstructorSummariesRequest(instructorIds))
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            if (response == null || response.getData() == null) {
                return List.of();
            }
            return response.getData();
        } catch (RestClientException ex) {
            log.warn("Failed to fetch instructor summaries from user-service for {} ids", instructorIds.size(), ex);
            return Collections.emptyList();
        }
    }
}
