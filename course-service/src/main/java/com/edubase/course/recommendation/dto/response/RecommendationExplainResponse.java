package com.edubase.course.recommendation.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendationExplainResponse {

    private RecommendationExplainProfileResponse userProfile;
    private String recommendationStrategy;
    private String explanation;
}
