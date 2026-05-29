package com.edubase.course.repository.finalexam;

import com.edubase.course.entity.finalexam.ExamQuestion;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ExamQuestionRepository extends JpaRepository<ExamQuestion, Long> {

    @EntityGraph(attributePaths = {"options"})
    List<ExamQuestion> findByFinalExamIdOrderByOrderIndexAscIdAsc(Long finalExamId);

    @EntityGraph(attributePaths = {"options"})
    List<ExamQuestion> findByFinalExamIdAndActiveTrueOrderByOrderIndexAscIdAsc(Long finalExamId);

    Optional<ExamQuestion> findByIdAndFinalExamId(Long id, Long finalExamId);

    long countByFinalExamIdAndActiveTrue(Long finalExamId);
}
