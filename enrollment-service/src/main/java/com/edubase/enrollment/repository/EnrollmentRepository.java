package com.edubase.enrollment.repository;

import com.edubase.enrollment.entity.Enrollment;
import com.edubase.enrollment.entity.EnrollmentStatus;
import com.edubase.enrollment.repository.projection.CourseEnrollmentCountProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {

    Optional<Enrollment> findByUserIdAndCourseId(Long userId, String courseId);

    Page<Enrollment> findAllByUserId(Long userId, Pageable pageable);

    Page<Enrollment> findAllByCourseId(String courseId, Pageable pageable);

    boolean existsByIdAndUserId(Long id, Long userId);

    boolean existsByUserIdAndCourseIdAndStatus(Long userId, String courseId, EnrollmentStatus status);

    @Query("""
            select e.courseId as courseId, count(e.id) as enrollmentCount
            from Enrollment e
            where e.courseId in :courseIds
              and e.status in :statuses
            group by e.courseId
            """)
    List<CourseEnrollmentCountProjection> countByCourseIdsAndStatuses(
            @Param("courseIds") Collection<String> courseIds,
            @Param("statuses") Collection<EnrollmentStatus> statuses);
}
