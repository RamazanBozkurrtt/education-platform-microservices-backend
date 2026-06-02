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
public class CourseRecommendationResponse {

    private String courseId;
    private String title;
    private String description;
    private String category;
    private String level;
    private Long durationSeconds;
    private Integer lessonCount;
    private Double rating;
    private String thumbnailUrl;
    private Double score;
    private String reason;
    private List<String> badges;
}
