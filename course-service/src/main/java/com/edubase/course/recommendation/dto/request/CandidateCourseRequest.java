package com.edubase.course.recommendation.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CandidateCourseRequest {

    private String courseId;
    private String title;
    private String description;
    private String category;
    private String level;
    private List<String> tags;
    private Long durationSeconds;
    private Integer lessonCount;
    private Double rating;
    private Long enrollmentCount;
    private Instant createdAt;
}
