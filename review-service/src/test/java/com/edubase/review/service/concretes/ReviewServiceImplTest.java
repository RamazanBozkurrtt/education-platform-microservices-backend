package com.edubase.review.service.concretes;

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
import com.edubase.review.repository.ReviewRepository;
import com.edubase.review.repository.projection.RatingDistributionProjection;
import com.edubase.review.repository.projection.ReviewSummaryProjection;
import com.edubase.review.security.AuthContext;
import com.edubase.review.security.UserRole;
import com.edubase.review.service.abstracts.ReviewEligibilityService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewServiceImplTest {

    @Mock
    private ReviewRepository reviewRepository;
    @Mock
    private ReviewMapper reviewMapper;
    @Mock
    private CourseClient courseClient;
    @Mock
    private ReviewEligibilityService reviewEligibilityService;

    @InjectMocks
    private ReviewServiceImpl reviewService;

    @Test
    void createReview_shouldSave_whenPublishedCourseAndNotDuplicate() {
        AuthContext auth = new AuthContext("15", UserRole.USER);
        CreateReviewRequest request = new CreateReviewRequest(5, "Great course");
        CourseLookupResponse course = course("course-1", "99");
        Review entity = new Review();
        Review saved = review("course-1", 15L, 5, "Great course");
        saved.setId(1L);
        ReviewResponse mappedResponse = response(saved);

        when(courseClient.getPublishedCourseById("course-1")).thenReturn(course);
        doNothing().when(reviewEligibilityService).assertCanReview(15L, "course-1");
        when(reviewRepository.existsByCourseIdAndUserId("course-1", 15L)).thenReturn(false);
        when(reviewMapper.toEntityFromRequest(request)).thenReturn(entity);
        when(reviewRepository.save(entity)).thenReturn(saved);
        when(reviewMapper.toResponseFromEntity(saved)).thenReturn(mappedResponse);

        ReviewResponse result = reviewService.createReview(auth, "course-1", request);

        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals(true, result.getOwnedByCurrentUser());
        verify(reviewRepository).save(entity);
    }

    @Test
    void createReview_shouldThrow_whenDuplicateExists() {
        AuthContext auth = new AuthContext("15", UserRole.USER);
        CreateReviewRequest request = new CreateReviewRequest(5, "Great course");

        when(courseClient.getPublishedCourseById("course-1")).thenReturn(course("course-1", "99"));
        doNothing().when(reviewEligibilityService).assertCanReview(15L, "course-1");
        when(reviewRepository.existsByCourseIdAndUserId("course-1", 15L)).thenReturn(true);

        assertThrows(ReviewAlreadyExistsException.class, () -> reviewService.createReview(auth, "course-1", request));
        verify(reviewRepository, never()).save(any());
    }

    @Test
    void createReview_shouldThrow_whenInstructorReviewsOwnCourse() {
        AuthContext auth = new AuthContext("15", UserRole.INSTRUCTOR);
        CreateReviewRequest request = new CreateReviewRequest(4, "Nice");
        when(courseClient.getPublishedCourseById("course-1")).thenReturn(course("course-1", "15"));

        assertThrows(OwnCourseReviewForbiddenException.class, () -> reviewService.createReview(auth, "course-1", request));
        verify(reviewRepository, never()).save(any());
    }

    @Test
    void updateReview_shouldAllowOwner() {
        AuthContext auth = new AuthContext("15", UserRole.USER);
        UpdateReviewRequest request = new UpdateReviewRequest(3, "updated comment");
        Review review = review("course-1", 15L, 5, "old");
        review.setId(9L);
        ReviewResponse mapped = response(review);

        when(reviewRepository.findById(9L)).thenReturn(Optional.of(review));
        doAnswer(invocation -> {
            UpdateReviewRequest incoming = invocation.getArgument(0);
            Review target = invocation.getArgument(1);
            target.setRating(incoming.getRating());
            target.setComment(incoming.getComment());
            return null;
        }).when(reviewMapper).updateEntityFromRequest(eq(request), eq(review));
        when(reviewRepository.save(review)).thenReturn(review);
        when(reviewMapper.toResponseFromEntity(review)).thenReturn(mapped);

        ReviewResponse result = reviewService.updateReview(auth, 9L, request);

        assertEquals(3, review.getRating());
        assertEquals("updated comment", review.getComment());
        assertEquals(true, result.getOwnedByCurrentUser());
    }

    @Test
    void deleteReview_shouldRejectNonOwnerAndNonAdmin() {
        AuthContext auth = new AuthContext("20", UserRole.USER);
        Review review = review("course-1", 15L, 5, "old");
        review.setId(9L);
        when(reviewRepository.findById(9L)).thenReturn(Optional.of(review));

        assertThrows(AccessDeniedException.class, () -> reviewService.deleteReview(auth, 9L));
        verify(reviewRepository, never()).delete(any());
    }

    @Test
    void getCourseSummary_shouldReturnAverageAndDistribution() {
        ReviewSummaryProjection projection = new ReviewSummaryProjection() {
            @Override
            public Double getAverageRating() {
                return 4.26;
            }

            @Override
            public Long getTotalReviews() {
                return 12L;
            }
        };
        RatingDistributionProjection fiveStar = new RatingDistributionProjection() {
            @Override
            public Integer getRating() {
                return 5;
            }

            @Override
            public Long getReviewCount() {
                return 7L;
            }
        };

        when(courseClient.getPublishedCourseById("course-1")).thenReturn(course("course-1", "99"));
        when(reviewRepository.getSummaryByCourseId("course-1")).thenReturn(projection);
        when(reviewRepository.getRatingDistribution("course-1")).thenReturn(List.of(fiveStar));

        ReviewSummaryResponse result = reviewService.getCourseSummary("course-1");

        assertEquals(4.3, result.getAverageRating());
        assertEquals(12L, result.getTotalReviews());
        assertEquals(0L, result.getRatingDistribution().get("1"));
        assertEquals(7L, result.getRatingDistribution().get("5"));
    }

    @Test
    void getMyReviews_shouldReturnPagedData() {
        AuthContext auth = new AuthContext("15", UserRole.USER);
        Review review = review("course-1", 15L, 5, "Great");
        review.setId(7L);
        PageRequest pageRequest = PageRequest.of(0, 10);
        Page<Review> page = new PageImpl<>(List.of(review), pageRequest, 1);
        ReviewResponse mapped = response(review);

        when(reviewRepository.findAllByUserId(15L, pageRequest)).thenReturn(page);
        when(reviewMapper.toResponseFromEntity(review)).thenReturn(mapped);

        CustomPageResponse<ReviewResponse> result = reviewService.getMyReviews(auth, pageRequest);

        assertEquals(1, result.getContent().size());
        assertEquals(0, result.getPageNumber());
    }

    private CourseLookupResponse course(String id, String instructorId) {
        CourseLookupResponse response = new CourseLookupResponse();
        response.setId(id);
        response.setInstructorId(instructorId);
        response.setStatus("PUBLISHED");
        return response;
    }

    private Review review(String courseId, Long userId, Integer rating, String comment) {
        Review review = new Review();
        review.setCourseId(courseId);
        review.setUserId(userId);
        review.setRating(rating);
        review.setComment(comment);
        review.setCreatedAt(LocalDateTime.now());
        review.setUpdatedAt(LocalDateTime.now());
        return review;
    }

    private ReviewResponse response(Review review) {
        return ReviewResponse.builder()
                .id(review.getId())
                .courseId(review.getCourseId())
                .userId(review.getUserId())
                .rating(review.getRating())
                .comment(review.getComment())
                .createdAt(review.getCreatedAt())
                .updatedAt(review.getUpdatedAt())
                .build();
    }
}
