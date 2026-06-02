package com.edubase.course.recommendation.client;

import com.edubase.course.recommendation.dto.request.RecommendationContext;
import com.edubase.course.recommendation.dto.request.RecommendationDashboardRequest;
import com.edubase.course.recommendation.dto.request.RecommendationSearchRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class RecommendationServiceClientTest {

    @Test
    void getDashboardRecommendations_shouldCallVersionedFastApiEndpoint() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://recommendation-service:8000");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RecommendationServiceClient client = new RecommendationServiceClient(builder.build());

        server.expect(requestTo("http://recommendation-service:8000/api/v1/recommendations/dashboard"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"recommendations\":[],\"strategy\":\"SEMANTIC_SIMILARITY + PROGRESS_AWARE_RANKING\"}",
                        MediaType.APPLICATION_JSON));

        client.getDashboardRecommendations(RecommendationDashboardRequest.builder()
                .userId("82")
                .limit(5)
                .context(RecommendationContext.DASHBOARD)
                .build());

        server.verify();
    }

    @Test
    void getSearchRecommendations_shouldCallVersionedFastApiEndpoint() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://recommendation-service:8000");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RecommendationServiceClient client = new RecommendationServiceClient(builder.build());

        server.expect(requestTo("http://recommendation-service:8000/api/v1/recommendations/search"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"recommendations\":[],\"strategy\":\"SEMANTIC_SIMILARITY + PROGRESS_AWARE_RANKING\"}",
                        MediaType.APPLICATION_JSON));

        client.getSearchRecommendations(RecommendationSearchRequest.builder()
                .userId("82")
                .query("spring")
                .limit(5)
                .context(RecommendationContext.SEARCH)
                .build());

        server.verify();
    }
}
