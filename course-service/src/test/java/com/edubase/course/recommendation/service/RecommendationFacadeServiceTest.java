package com.edubase.course.recommendation.service;

import com.edubase.course.recommendation.client.RecommendationServiceClient;
import com.edubase.course.recommendation.dto.response.CourseRecommendationResponse;
import com.edubase.course.recommendation.dto.response.RecommendationListResponse;
import com.edubase.course.recommendation.dto.response.RecommendationServiceItemResponse;
import com.edubase.course.recommendation.dto.response.RecommendationServiceResponse;
import com.edubase.course.recommendation.model.CandidateCourseData;
import com.edubase.course.recommendation.model.UserRecommendationProfile;
import com.edubase.course.security.AuthContext;
import com.edubase.course.security.UserRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClientException;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecommendationFacadeServiceTest {

    @Mock
    private RecommendationServiceClient recommendationServiceClient;

    @Mock
    private RecommendationProfileService recommendationProfileService;

    @Mock
    private CandidateCourseService candidateCourseService;

    @Mock
    private FallbackRecommendationService fallbackRecommendationService;

    @Mock
    private RecommendationLogService recommendationLogService;

    @InjectMocks
    private RecommendationFacadeService recommendationFacadeService;

    @Test
    void getDashboardRecommendations_shouldUseRecommendationServiceAndEnrichResponse() {
        AuthContext authContext = new AuthContext("82", UserRole.USER);
        UserRecommendationProfile profile = UserRecommendationProfile.builder().coldStart(true).build();
        CandidateCourseData candidate = CandidateCourseData.builder()
                .courseId("course-1")
                .title("Spring Boot")
                .description("Spring Boot aciklama")
                .category("Backend")
                .level("Beginner")
                .durationSeconds(3600L)
                .lessonCount(12)
                .enrollmentCount(42L)
                .thumbnailUrl("/courses/public/course-1/image")
                .createdAt(Instant.parse("2026-05-29T09:00:00Z"))
                .build();

        RecommendationServiceResponse serviceResponse = RecommendationServiceResponse.builder()
                .recommendations(List.of(RecommendationServiceItemResponse.builder()
                        .courseId("course-1")
                        .score(0.91d)
                        .reason("Semantic match")
                        .badges(List.of("Semantic Match", "Backend"))
                        .build()))
                .strategy("SEMANTIC_SIMILARITY + PROGRESS_AWARE_RANKING")
                .build();

        when(recommendationProfileService.buildProfile("82")).thenReturn(profile);
        when(candidateCourseService.buildCandidates(profile, null)).thenReturn(List.of(candidate));
        when(recommendationServiceClient.getDashboardRecommendations(any())).thenReturn(serviceResponse);

        RecommendationListResponse response = recommendationFacadeService.getDashboardRecommendations(authContext, 10);

        assertEquals("SEMANTIC_SIMILARITY + PROGRESS_AWARE_RANKING", response.getStrategy());
        assertEquals(1, response.getRecommendations().size());

        CourseRecommendationResponse first = response.getRecommendations().get(0);
        assertEquals("course-1", first.getCourseId());
        assertEquals("Spring Boot", first.getTitle());
        assertEquals("Spring Boot aciklama", first.getDescription());
        assertEquals("Backend", first.getCategory());
        assertEquals("Beginner", first.getLevel());
        assertEquals(3600L, first.getDurationSeconds());
        assertEquals(12, first.getLessonCount());
        assertEquals(42L, first.getStudentsCount());
        assertEquals("/courses/public/course-1/image", first.getThumbnailUrl());
        assertEquals(0.91d, first.getScore());
        assertEquals("Semantic match", first.getReason());
        assertEquals(List.of("Semantic Match", "Backend"), first.getBadges());

        verify(fallbackRecommendationService, never()).buildFallbackRecommendations(any(), anyInt());
        verify(recommendationLogService).logRecommendations(eq("82"), eq("DASHBOARD"),
                eq("SEMANTIC_SIMILARITY + PROGRESS_AWARE_RANKING"), eq(response.getRecommendations()));
    }

    @Test
    void getDashboardRecommendations_shouldFallbackWhenRecommendationServiceFails() {
        AuthContext authContext = new AuthContext("82", UserRole.USER);
        UserRecommendationProfile profile = UserRecommendationProfile.builder().coldStart(false).build();
        List<CandidateCourseData> candidates = List.of(CandidateCourseData.builder().courseId("course-1").build());
        RecommendationListResponse fallbackResponse = RecommendationListResponse.builder()
                .recommendations(List.of())
                .strategy("POPULARITY_FALLBACK")
                .build();

        when(recommendationProfileService.buildProfile("82")).thenReturn(profile);
        when(candidateCourseService.buildCandidates(profile, null)).thenReturn(candidates);
        when(recommendationServiceClient.getDashboardRecommendations(any()))
                .thenThrow(new RestClientException("recommendation-service down"));
        when(fallbackRecommendationService.buildFallbackRecommendations(candidates, 10)).thenReturn(fallbackResponse);

        RecommendationListResponse response = recommendationFacadeService.getDashboardRecommendations(authContext, 10);

        assertSame(fallbackResponse, response);
        verify(fallbackRecommendationService).buildFallbackRecommendations(candidates, 10);
    }

    @Test
    void getDashboardRecommendations_shouldAllowInstructorRole() {
        AuthContext authContext = new AuthContext("82", UserRole.INSTRUCTOR);
        UserRecommendationProfile profile = UserRecommendationProfile.builder().coldStart(true).build();
        when(recommendationProfileService.buildProfile("82")).thenReturn(profile);
        when(candidateCourseService.buildCandidates(profile, null)).thenReturn(List.of());

        RecommendationListResponse response = recommendationFacadeService.getDashboardRecommendations(authContext, 10);

        assertEquals("NO_CANDIDATES", response.getStrategy());
    }
}
