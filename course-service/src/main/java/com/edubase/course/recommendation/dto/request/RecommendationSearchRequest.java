package com.edubase.course.recommendation.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendationSearchRequest {

    private String userId;
    private String query;
    private Integer limit;
    private RecommendationContext context;
    private RecommendationUserProfileRequest userProfile;
    private List<CandidateCourseRequest> candidateCourses;
}
