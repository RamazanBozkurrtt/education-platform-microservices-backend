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
public class RecommendationServiceItemResponse {

    private String courseId;
    private Double score;
    private String reason;
    private List<String> badges;
}
