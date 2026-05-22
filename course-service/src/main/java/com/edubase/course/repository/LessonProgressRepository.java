package com.edubase.course.repository;

import com.edubase.course.entity.LessonProgress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LessonProgressRepository extends JpaRepository<LessonProgress, Long> {

    Optional<LessonProgress> findByUserIdAndCourseIdAndLessonId(String userId, String courseId, String lessonId);

    List<LessonProgress> findByUserIdAndCourseId(String userId, String courseId);

    List<LessonProgress> findByUserIdAndCourseIdOrderByUpdatedAtDesc(String userId, String courseId);

    boolean existsByUserIdAndCourseIdAndLessonId(String userId, String courseId, String lessonId);

    long countByUserIdAndCourseIdAndCompletedTrue(String userId, String courseId);
}
