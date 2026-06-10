package com.edubase.course.repository.finalexam;

import com.edubase.course.entity.finalexam.FinalExam;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FinalExamRepository extends JpaRepository<FinalExam, Long> {

    Optional<FinalExam> findByCourseIdAndActiveTrue(String courseId);

    boolean existsByCourseIdAndActiveTrue(String courseId);

    Optional<FinalExam> findByIdAndCourseId(Long id, String courseId);
}
