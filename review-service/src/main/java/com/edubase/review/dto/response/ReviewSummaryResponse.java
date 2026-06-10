package com.edubase.review.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewSummaryResponse {

    private String courseId;
    private Double averageRating;
    private Long totalReviews;
    private Map<String, Long> ratingDistribution;
}
