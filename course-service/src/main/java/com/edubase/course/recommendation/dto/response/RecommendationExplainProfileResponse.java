package com.edubase.course.recommendation.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendationExplainProfileResponse {

    private List<String> favoriteCategories;
    private Double averageCompletionRate;
    private Double dropoutRate;
    private Long preferredDurationSeconds;
    private String dropoutRisk;
    private String preferredDurationLabel;
    private Integer completedCourseCount;
    private Integer inProgressCourseCount;
}
