package com.edubase.course.recommendation.service;

import com.edubase.course.recommendation.dto.response.CourseRecommendationResponse;
import com.edubase.course.recommendation.dto.response.RecommendationListResponse;
import com.edubase.course.recommendation.model.CandidateCourseData;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
public class FallbackRecommendationService {

    private static final String FALLBACK_REASON = "Populer ve yeni baslayanlar icin uygun oldugu icin onerildi.";
    private static final String FALLBACK_STRATEGY = "POPULARITY_FALLBACK";

    public RecommendationListResponse buildFallbackRecommendations(List<CandidateCourseData> candidates, int limit) {
        if (candidates == null || candidates.isEmpty() || limit <= 0) {
            return RecommendationListResponse.builder()
                    .recommendations(List.of())
                    .strategy(FALLBACK_STRATEGY)
                    .build();
        }

        List<CourseRecommendationResponse> recommendations = candidates.stream()
                .sorted(Comparator.comparingDouble(this::fallbackScore).reversed())
                .limit(limit)
                .map(candidate -> CourseRecommendationResponse.builder()
                        .courseId(candidate.getCourseId())
                        .title(candidate.getTitle())
                        .description(candidate.getDescription())
                        .category(candidate.getCategory())
                        .level(candidate.getLevel())
                        .durationSeconds(candidate.getDurationSeconds())
                        .lessonCount(candidate.getLessonCount())
                        .rating(candidate.getRating())
                        .studentsCount(safeLong(candidate.getEnrollmentCount()))
                        .thumbnailUrl(candidate.getThumbnailUrl())
                        .score(normalizeScore(candidate))
                        .reason(FALLBACK_REASON)
                        .badges(buildBadges(candidate))
                        .build())
                .toList();

        return RecommendationListResponse.builder()
                .recommendations(recommendations)
                .strategy(FALLBACK_STRATEGY)
                .build();
    }

    private double fallbackScore(CandidateCourseData candidate) {
        double score = 0.0d;
        if (candidate.getCreatedAt() != null) {
            score += 10.0d;
        }
        if (candidate.getDurationSeconds() != null && candidate.getDurationSeconds() > 0 && candidate.getDurationSeconds() <= 3600L) {
            score += 8.0d;
        }
        if (candidate.getLessonCount() != null && candidate.getLessonCount() >= 4 && candidate.getLessonCount() <= 20) {
            score += 6.0d;
        }
        if (candidate.getLevel() != null) {
            String level = candidate.getLevel().toLowerCase(Locale.ROOT);
            if (level.contains("beginner") || level.contains("basic")) {
                score += 12.0d;
            }
        }
        return score;
    }

    private double normalizeScore(CandidateCourseData candidate) {
        double score = 0.55d + (fallbackScore(candidate) / 100.0d);
        return Math.min(0.95d, Math.max(0.55d, score));
    }

    private List<String> buildBadges(CandidateCourseData candidate) {
        List<String> badges = new ArrayList<>();
        if (candidate.getCategory() != null && !candidate.getCategory().isBlank()) {
            badges.add(candidate.getCategory());
        }
        if (candidate.getDurationSeconds() != null && candidate.getDurationSeconds() > 0 && candidate.getDurationSeconds() <= 3600L) {
            badges.add("Short Course");
        }
        badges.add("Recommended");
        return badges.stream().distinct().limit(3).toList();
    }

    private long safeLong(Long value) {
        return value == null || value < 0L ? 0L : value;
    }
}
