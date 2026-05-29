package com.edubase.course.repository.finalexam;

import com.edubase.course.entity.finalexam.AttemptStatus;
import com.edubase.course.entity.finalexam.ExamAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ExamAttemptRepository extends JpaRepository<ExamAttempt, Long> {

    Optional<ExamAttempt> findByIdAndCourseId(Long id, String courseId);

    Optional<ExamAttempt> findFirstByFinalExamIdAndStudentIdAndAttemptStatusOrderByStartedAtDesc(
            Long finalExamId,
            String studentId,
            AttemptStatus attemptStatus
    );

    List<ExamAttempt> findByFinalExamIdAndStudentIdOrderByAttemptNumberDesc(Long finalExamId, String studentId);

    long countByFinalExamIdAndStudentId(Long finalExamId, String studentId);

    long countByFinalExamIdAndStudentIdAndAttemptStatusIn(
            Long finalExamId,
            String studentId,
            Collection<AttemptStatus> statuses
    );
}
