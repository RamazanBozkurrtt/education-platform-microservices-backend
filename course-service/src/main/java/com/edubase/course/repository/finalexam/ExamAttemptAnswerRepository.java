package com.edubase.course.repository.finalexam;

import com.edubase.course.entity.finalexam.ExamAttemptAnswer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ExamAttemptAnswerRepository extends JpaRepository<ExamAttemptAnswer, Long> {

    List<ExamAttemptAnswer> findByAttemptId(Long attemptId);

    List<ExamAttemptAnswer> findByAttemptIdAndQuestionIdIn(Long attemptId, Collection<Long> questionIds);

    Optional<ExamAttemptAnswer> findByAttemptIdAndQuestionId(Long attemptId, Long questionId);
}
