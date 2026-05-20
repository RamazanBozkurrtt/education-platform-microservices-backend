package com.edubase.review.controller;

import com.edubase.commonCore.utils.RestResponse;
import com.edubase.review.controller.base.RestBaseController;
import com.edubase.review.dto.request.CreateReviewRequest;
import com.edubase.review.dto.request.UpdateReviewRequest;
import com.edubase.review.dto.response.CustomPageResponse;
import com.edubase.review.dto.response.ReviewResponse;
import com.edubase.review.dto.response.ReviewSummaryResponse;
import com.edubase.review.security.AuthContext;
import com.edubase.review.security.AuthContextResolver;
import com.edubase.review.service.abstracts.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.data.domain.Sort.Direction.DESC;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/reviews")
@Tag(name = "Reviews", description = "Course review and rating endpoints")
public class ReviewController extends RestBaseController {

    private final ReviewService reviewService;
    private final AuthContextResolver authContextResolver;

    @PostMapping("/courses/{courseId}")
    @Operation(summary = "Create review", description = "Creates one review for one user on a published course.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Review created"),
            @ApiResponse(responseCode = "400", description = "Validation failed"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Instructor cannot review own course"),
            @ApiResponse(responseCode = "404", description = "Course not found"),
            @ApiResponse(responseCode = "409", description = "Review already exists")
    })
    public ResponseEntity<RestResponse<ReviewResponse>> createReview(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String courseId,
            @RequestBody @Valid CreateReviewRequest request) {
        AuthContext authContext = authContextResolver.requireAuth(jwt);
        return created(reviewService.createReview(authContext, courseId, request));
    }

    @GetMapping("/courses/{courseId}")
    @Operation(summary = "List course reviews", description = "Lists reviews for a course with pagination.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Reviews fetched"),
            @ApiResponse(responseCode = "404", description = "Course not found")
    })
    public ResponseEntity<RestResponse<CustomPageResponse<ReviewResponse>>> getCourseReviews(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String courseId,
            @RequestParam(required = false) @Min(1) @Max(5) Integer rating,
            @PageableDefault(size = 10, sort = "createdAt", direction = DESC) Pageable pageable) {
        AuthContext authContext = authContextResolver.resolveOptional(jwt);
        return ok(reviewService.getCourseReviews(authContext, courseId, rating, pageable));
    }

    @GetMapping("/courses/{courseId}/summary")
    @Operation(summary = "Get course review summary", description = "Returns average rating, total review count and rating distribution.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Summary fetched"),
            @ApiResponse(responseCode = "404", description = "Course not found")
    })
    public ResponseEntity<RestResponse<ReviewSummaryResponse>> getCourseSummary(@PathVariable String courseId) {
        return ok(reviewService.getCourseSummary(courseId));
    }

    @GetMapping("/me")
    @Operation(summary = "List my reviews", description = "Lists authenticated user's reviews.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "My reviews fetched"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<RestResponse<CustomPageResponse<ReviewResponse>>> getMyReviews(
            @AuthenticationPrincipal Jwt jwt,
            @PageableDefault(size = 10, sort = "createdAt", direction = DESC) Pageable pageable) {
        AuthContext authContext = authContextResolver.requireAuth(jwt);
        return ok(reviewService.getMyReviews(authContext, pageable));
    }

    @PutMapping("/{reviewId}")
    @Operation(summary = "Update review", description = "Only review owner can update rating/comment.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Review updated"),
            @ApiResponse(responseCode = "400", description = "Validation failed"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden"),
            @ApiResponse(responseCode = "404", description = "Review not found")
    })
    public ResponseEntity<RestResponse<ReviewResponse>> updateReview(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long reviewId,
            @RequestBody @Valid UpdateReviewRequest request) {
        AuthContext authContext = authContextResolver.requireAuth(jwt);
        return ok(reviewService.updateReview(authContext, reviewId, request));
    }

    @DeleteMapping("/{reviewId}")
    @Operation(summary = "Delete review", description = "Review owner or admin can delete review.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Review deleted"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden"),
            @ApiResponse(responseCode = "404", description = "Review not found")
    })
    public ResponseEntity<RestResponse<Void>> deleteReview(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long reviewId) {
        AuthContext authContext = authContextResolver.requireAuth(jwt);
        reviewService.deleteReview(authContext, reviewId);
        return noContent();
    }
}
