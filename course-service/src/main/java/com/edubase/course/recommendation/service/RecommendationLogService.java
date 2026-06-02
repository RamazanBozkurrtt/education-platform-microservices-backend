package com.edubase.course.recommendation.service;

import com.edubase.course.recommendation.dto.response.CourseRecommendationResponse;
import com.edubase.course.recommendation.entity.RecommendationLog;
import com.edubase.course.recommendation.repository.RecommendationLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationLogService {

    private final RecommendationLogRepository recommendationLogRepository;

    public void logRecommendations(String userId, String context, String strategy, List<CourseRecommendationResponse> recommendations) {
        if (userId == null || userId.isBlank() || recommendations == null || recommendations.isEmpty()) {
            return;
        }

        try {
            List<RecommendationLog> logs = new ArrayList<>();
            for (CourseRecommendationResponse recommendation : recommendations) {
                if (recommendation == null || recommendation.getCourseId() == null || recommendation.getCourseId().isBlank()) {
                    continue;
                }
                logs.add(RecommendationLog.builder()
                        .userId(userId.trim())
                        .courseId(recommendation.getCourseId().trim())
                        .context(context)
                        .score(recommendation.getScore() == null ? null : BigDecimal.valueOf(recommendation.getScore()))
                        .reason(recommendation.getReason())
                        .strategy(strategy)
                        .build());
            }
            if (!logs.isEmpty()) {
                recommendationLogRepository.saveAll(logs);
            }
        } catch (Exception ex) {
            log.warn("Recommendation log persistence failed. userId={} context={} strategy={}", userId, context, strategy, ex);
        }
    }
}
