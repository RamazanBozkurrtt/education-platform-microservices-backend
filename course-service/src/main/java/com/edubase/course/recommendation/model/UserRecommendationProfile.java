package com.edubase.course.recommendation.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserRecommendationProfile {

    private List<String> favoriteCategories;
    private Double averageCompletionRate;
    private Double dropoutRate;
    private Long preferredDurationSeconds;
    private List<String> completedCourseIds;
    private List<String> inProgressCourseIds;
    private List<String> recentlyWatchedCourseIds;
    private List<String> preferredLevels;
    private boolean coldStart;
}
