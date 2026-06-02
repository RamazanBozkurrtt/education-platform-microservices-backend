package com.edubase.course.recommendation.controller;

import com.edubase.commonCore.utils.RestResponse;
import com.edubase.course.recommendation.dto.response.RecommendationExplainResponse;
import com.edubase.course.recommendation.dto.response.RecommendationListResponse;
import com.edubase.course.recommendation.service.RecommendationFacadeService;
import com.edubase.course.security.AuthContext;
import com.edubase.course.security.AuthContextResolver;
import com.edubase.course.security.UserRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecommendationControllerTest {

    @Mock
    private RecommendationFacadeService recommendationFacadeService;

    @Mock
    private AuthContextResolver authContextResolver;

    @InjectMocks
    private RecommendationController recommendationController;

    @Test
    void getDashboardRecommendations_shouldResolveAuthAndReturnData() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .claim("user_id", "42")
                .claim("role", "STUDENT")
                .build();
        AuthContext authContext = new AuthContext("42", UserRole.STUDENT);
        RecommendationListResponse payload = RecommendationListResponse.builder()
                .strategy("POPULARITY_FALLBACK")
                .build();

        when(authContextResolver.requireAuth(jwt)).thenReturn(authContext);
        when(recommendationFacadeService.getDashboardRecommendations(authContext, 10)).thenReturn(payload);

        ResponseEntity<RestResponse<RecommendationListResponse>> response =
                recommendationController.getDashboardRecommendations(jwt, 10);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(payload, response.getBody().getData());
        verify(authContextResolver).requireAuth(jwt);
        verify(recommendationFacadeService).getDashboardRecommendations(authContext, 10);
    }

    @Test
    void explainCurrentUserRecommendations_shouldReturnExplainData() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .claim("user_id", "42")
                .claim("role", "STUDENT")
                .build();
        AuthContext authContext = new AuthContext("42", UserRole.STUDENT);
        RecommendationExplainResponse payload = RecommendationExplainResponse.builder()
                .recommendationStrategy("SEMANTIC_SIMILARITY + PROGRESS_AWARE_RANKING")
                .build();

        when(authContextResolver.requireAuth(jwt)).thenReturn(authContext);
        when(recommendationFacadeService.explainCurrentUser(authContext)).thenReturn(payload);

        ResponseEntity<RestResponse<RecommendationExplainResponse>> response =
                recommendationController.explainCurrentUserRecommendations(jwt);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(payload, response.getBody().getData());
    }
}
