package com.edubase.course.recommendation.client;

import com.edubase.course.recommendation.dto.request.RecommendationDashboardRequest;
import com.edubase.course.recommendation.dto.request.RecommendationSearchRequest;
import com.edubase.course.recommendation.dto.response.RecommendationServiceResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@RequiredArgsConstructor
public class RecommendationServiceClient {

    private final RestClient recommendationRestClient;

    public RecommendationServiceResponse getDashboardRecommendations(RecommendationDashboardRequest request) {
        return recommendationRestClient.post()
                .uri("/api/v1/recommendations/dashboard")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(RecommendationServiceResponse.class);
    }

    public RecommendationServiceResponse getSearchRecommendations(RecommendationSearchRequest request) {
        return recommendationRestClient.post()
                .uri("/api/v1/recommendations/search")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(RecommendationServiceResponse.class);
    }
}
