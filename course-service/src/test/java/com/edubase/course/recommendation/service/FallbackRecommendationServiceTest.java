package com.edubase.course.recommendation.service;

import com.edubase.course.recommendation.dto.response.RecommendationListResponse;
import com.edubase.course.recommendation.model.CandidateCourseData;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FallbackRecommendationServiceTest {

    private final FallbackRecommendationService fallbackRecommendationService = new FallbackRecommendationService();

    @Test
    void buildFallbackRecommendations_shouldReturnEmptyWhenNoCandidates() {
        RecommendationListResponse response = fallbackRecommendationService.buildFallbackRecommendations(List.of(), 10);

        assertEquals("POPULARITY_FALLBACK", response.getStrategy());
        assertTrue(response.getRecommendations().isEmpty());
    }

    @Test
    void buildFallbackRecommendations_shouldRespectLimitAndReason() {
        CandidateCourseData first = CandidateCourseData.builder()
                .courseId("course-1")
                .title("Java Basics")
                .category("Backend")
                .level("Beginner")
                .durationSeconds(3200L)
                .lessonCount(10)
                .createdAt(Instant.now())
                .thumbnailUrl("/courses/public/course-1/image")
                .build();
        CandidateCourseData second = CandidateCourseData.builder()
                .courseId("course-2")
                .title("Spring Security")
                .category("Backend")
                .level("Intermediate")
                .durationSeconds(5400L)
                .lessonCount(8)
                .createdAt(Instant.now().minusSeconds(86_400))
                .thumbnailUrl("/courses/public/course-2/image")
                .build();

        RecommendationListResponse response = fallbackRecommendationService.buildFallbackRecommendations(List.of(first, second), 1);

        assertEquals(1, response.getRecommendations().size());
        assertFalse(response.getRecommendations().get(0).getReason().isBlank());
    }
}
