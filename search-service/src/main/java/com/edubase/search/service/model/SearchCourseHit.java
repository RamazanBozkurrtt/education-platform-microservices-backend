package com.edubase.search.service.model;

import java.util.List;

public record SearchCourseHit(
        String courseId,
        String title,
        String description,
        String instructorId,
        String categoryId,
        Double price,
        String status,
        List<String> tags,
        List<String> learningOutcomes,
        Double averageRating,
        Long ratingCount,
        float score
) {
}
