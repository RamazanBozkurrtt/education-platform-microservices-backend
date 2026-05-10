package com.edubase.review.service.concretes;

import com.edubase.commonCore.exceptions.BusinessException;
import com.edubase.commonCore.exceptions.ErrorCode;
import com.edubase.review.configuration.mapper.ReviewMapper;
import com.edubase.review.dto.internal.CourseLookupResponse;
import com.edubase.review.dto.request.CreateReviewRequest;
import com.edubase.review.dto.request.UpdateReviewRequest;
import com.edubase.review.dto.response.CustomPageResponse;
import com.edubase.review.dto.response.ReviewResponse;
import com.edubase.review.dto.response.ReviewSummaryResponse;
import com.edubase.review.entity.Review;
import com.edubase.review.exception.OwnCourseReviewForbiddenException;
import com.edubase.review.exception.ReviewAlreadyExistsException;
import com.edubase.review.exception.ReviewNotFoundException;
import com.edubase.review.repository.ReviewRepository;
import com.edubase.review.repository.projection.RatingDistributionProjection;
import com.edubase.review.repository.projection.ReviewSummaryProjection;
import com.edubase.review.security.AuthContext;
import com.edubase.review.security.UserRole;
import com.edubase.review.service.abstracts.ReviewEligibilityService;
import com.edubase.review.service.abstracts.ReviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {

    private final ReviewRepository reviewRepository;
    private final ReviewMapper reviewMapper;
    private final CourseClient courseClient;
    private final ReviewEligibilityService reviewEligibilityService;

    @Override
    @Transactional
    @PreAuthorize("@reviewSecurity.isAuthenticatedUser(#authContext)")
    public ReviewResponse createReview(AuthContext authContext, String courseId, CreateReviewRequest request) {
        Long userId = requireUserId(authContext);
        String normalizedCourseId = normalizeCourseId(courseId);

        CourseLookupResponse course = courseClient.getPublishedCourseById(normalizedCourseId);
        if (course.getInstructorId() != null && course.getInstructorId().trim().equals(String.valueOf(userId))) {
            throw new OwnCourseReviewForbiddenException();
        }

        reviewEligibilityService.assertCanReview(userId, normalizedCourseId);

        if (reviewRepository.existsByCourseIdAndUserId(normalizedCourseId, userId)) {
            throw new ReviewAlreadyExistsException();
        }

        Review review = reviewMapper.toEntityFromRequest(request);
        review.setCourseId(normalizedCourseId);
        review.setUserId(userId);
        review.setComment(request.getComment().trim());

        try {
            Review saved = reviewRepository.save(review);
            return toResponse(saved, userId);
        } catch (DataIntegrityViolationException ex) {
            throw new ReviewAlreadyExistsException();
        }
    }

    @Override
    public CustomPageResponse<ReviewResponse> getCourseReviews(AuthContext authContext, String courseId, Integer rating, Pageable pageable) {
        String normalizedCourseId = normalizeCourseId(courseId);
        courseClient.getPublishedCourseById(normalizedCourseId);

        Page<Review> page = rating == null
                ? reviewRepository.findAllByCourseId(normalizedCourseId, pageable)
                : reviewRepository.findAllByCourseIdAndRating(normalizedCourseId, rating, pageable);

        Long currentUserId = tryResolveUserId(authContext);
        List<ReviewResponse> responses = page.getContent().stream()
                .map(review -> toResponse(review, currentUserId))
                .toList();

        return CustomPageResponse.of(page, responses);
    }

    @Override
    public ReviewSummaryResponse getCourseSummary(String courseId) {
        String normalizedCourseId = normalizeCourseId(courseId);
        courseClient.getPublishedCourseById(normalizedCourseId);

        ReviewSummaryProjection projection = reviewRepository.getSummaryByCourseId(normalizedCourseId);
        Map<String, Long> distribution = buildDistribution(reviewRepository.getRatingDistribution(normalizedCourseId));

        double average = projection == null || projection.getAverageRating() == null
                ? 0.0
                : BigDecimal.valueOf(projection.getAverageRating()).setScale(1, RoundingMode.HALF_UP).doubleValue();
        long total = projection == null || projection.getTotalReviews() == null
                ? 0L
                : projection.getTotalReviews();

        return ReviewSummaryResponse.builder()
                .courseId(normalizedCourseId)
                .averageRating(average)
                .totalReviews(total)
                .ratingDistribution(distribution)
                .build();
    }

    @Override
    @PreAuthorize("@reviewSecurity.isAuthenticatedUser(#authContext)")
    public CustomPageResponse<ReviewResponse> getMyReviews(AuthContext authContext, Pageable pageable) {
        Long userId = requireUserId(authContext);
        Page<Review> page = reviewRepository.findAllByUserId(userId, pageable);
        List<ReviewResponse> responses = page.getContent().stream()
                .map(review -> toResponse(review, userId))
                .toList();
        return CustomPageResponse.of(page, responses);
    }

    @Override
    @Transactional
    @PreAuthorize("@reviewSecurity.canUpdateReview(#authContext, #reviewId)")
    public ReviewResponse updateReview(AuthContext authContext, Long reviewId, UpdateReviewRequest request) {
        Long userId = requireUserId(authContext);
        Review review = reviewRepository.findById(reviewId).orElseThrow(ReviewNotFoundException::new);
        if (!review.getUserId().equals(userId)) {
            throw new AccessDeniedException("Only owner can update review");
        }

        reviewMapper.updateEntityFromRequest(request, review);
        review.setComment(request.getComment().trim());
        Review saved = reviewRepository.save(review);
        return toResponse(saved, userId);
    }

    @Override
    @Transactional
    @PreAuthorize("@reviewSecurity.canDeleteReview(#authContext, #reviewId)")
    public void deleteReview(AuthContext authContext, Long reviewId) {
        Review review = reviewRepository.findById(reviewId).orElseThrow(ReviewNotFoundException::new);
        Long userId = tryResolveUserId(authContext);
        boolean isAdmin = authContext != null && authContext.role() == UserRole.ADMIN;
        if (!isAdmin && (userId == null || !review.getUserId().equals(userId))) {
            throw new AccessDeniedException("Only owner or admin can delete review");
        }
        reviewRepository.delete(review);
    }

    private ReviewResponse toResponse(Review review, Long currentUserId) {
        ReviewResponse response = reviewMapper.toResponseFromEntity(review);
        if (currentUserId == null) {
            response.setOwnedByCurrentUser(null);
        } else {
            response.setOwnedByCurrentUser(review.getUserId().equals(currentUserId));
        }
        response.setUserDisplayName(null);
        response.setUserProfileImageUrl(null);
        return response;
    }

    private Map<String, Long> buildDistribution(List<RatingDistributionProjection> rows) {
        Map<String, Long> distribution = new LinkedHashMap<>();
        distribution.put("1", 0L);
        distribution.put("2", 0L);
        distribution.put("3", 0L);
        distribution.put("4", 0L);
        distribution.put("5", 0L);

        if (rows == null || rows.isEmpty()) {
            return distribution;
        }

        for (RatingDistributionProjection row : rows) {
            if (row.getRating() == null || row.getReviewCount() == null) {
                continue;
            }
            distribution.put(String.valueOf(row.getRating()), row.getReviewCount());
        }
        return distribution;
    }

    private Long requireUserId(AuthContext authContext) {
        Long userId = tryResolveUserId(authContext);
        if (userId == null) {
            throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED);
        }
        return userId;
    }

    private Long tryResolveUserId(AuthContext authContext) {
        if (authContext == null || authContext.userId() == null || authContext.userId().isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(authContext.userId().trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String normalizeCourseId(String courseId) {
        if (courseId == null || courseId.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        return courseId.trim();
    }
}
