package com.edubase.review.service.abstracts;

import com.edubase.review.dto.request.CreateReviewRequest;
import com.edubase.review.dto.request.UpdateReviewRequest;
import com.edubase.review.dto.response.CustomPageResponse;
import com.edubase.review.dto.response.ReviewResponse;
import com.edubase.review.dto.response.ReviewSummaryResponse;
import com.edubase.review.security.AuthContext;
import org.springframework.data.domain.Pageable;

public interface ReviewService {

    ReviewResponse createReview(AuthContext authContext, String courseId, CreateReviewRequest request);

    CustomPageResponse<ReviewResponse> getCourseReviews(AuthContext authContext, String courseId, Integer rating, Pageable pageable);

    ReviewSummaryResponse getCourseSummary(String courseId);

    CustomPageResponse<ReviewResponse> getMyReviews(AuthContext authContext, Pageable pageable);

    ReviewResponse updateReview(AuthContext authContext, Long reviewId, UpdateReviewRequest request);

    void deleteReview(AuthContext authContext, Long reviewId);
}
