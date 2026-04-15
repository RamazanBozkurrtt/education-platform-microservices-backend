package com.edubase.enrollment.repository;

import com.edubase.enrollment.entity.Enrollment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {

    Optional<Enrollment> findByUserIdAndCourseId(Long userId, String courseId);

    Page<Enrollment> findAllByUserId(Long userId, Pageable pageable);

    Page<Enrollment> findAllByCourseId(String courseId, Pageable pageable);

    boolean existsByIdAndUserId(Long id, Long userId);
}
