package com.edubase.review.repository;

import com.edubase.review.entity.Review;
import com.edubase.review.repository.projection.RatingDistributionProjection;
import com.edubase.review.repository.projection.ReviewSummaryProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    boolean existsByCourseIdAndUserId(String courseId, Long userId);

    boolean existsByIdAndUserId(Long reviewId, Long userId);

    Page<Review> findAllByCourseId(String courseId, Pageable pageable);

    Page<Review> findAllByCourseIdAndRating(String courseId, Integer rating, Pageable pageable);

    Page<Review> findAllByUserId(Long userId, Pageable pageable);

    @Query("""
            select coalesce(avg(r.rating), 0) as averageRating,
                   count(r) as totalReviews
            from Review r
            where r.courseId = :courseId
            """)
    ReviewSummaryProjection getSummaryByCourseId(@Param("courseId") String courseId);

    @Query("""
            select r.rating as rating, count(r) as reviewCount
            from Review r
            where r.courseId = :courseId
            group by r.rating
            """)
    List<RatingDistributionProjection> getRatingDistribution(@Param("courseId") String courseId);
}
