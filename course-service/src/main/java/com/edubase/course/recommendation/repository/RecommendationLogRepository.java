package com.edubase.course.recommendation.repository;

import com.edubase.course.recommendation.entity.RecommendationLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecommendationLogRepository extends JpaRepository<RecommendationLog, Long> {
}
