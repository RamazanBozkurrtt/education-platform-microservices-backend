package com.edubase.course.recommendation.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendationServiceResponse {

    @Builder.Default
    private List<RecommendationServiceItemResponse> recommendations = new ArrayList<>();
    private String strategy;
}
