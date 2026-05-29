package com.edubase.course.service.concretes.finalexam;

import com.edubase.course.entity.finalexam.ExamOption;
import com.edubase.course.entity.finalexam.ExamQuestion;
import com.edubase.course.entity.finalexam.FinalExam;
import com.edubase.course.exception.PublishValidationException;
import com.edubase.course.repository.finalexam.ExamQuestionRepository;
import com.edubase.course.repository.finalexam.FinalExamRepository;
import com.edubase.course.service.abstracts.finalexam.FinalExamPublishValidationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FinalExamPublishValidationServiceImpl implements FinalExamPublishValidationService {

    private final FinalExamRepository finalExamRepository;
    private final ExamQuestionRepository examQuestionRepository;

    @Override
    public void validateForPublishIfPresent(String courseId) {
        FinalExam finalExam = finalExamRepository.findByCourseIdAndActiveTrue(courseId).orElse(null);
        if (finalExam == null) {
            return;
        }

        List<ExamQuestion> activeQuestions = examQuestionRepository
                .findByFinalExamIdAndActiveTrueOrderByOrderIndexAscIdAsc(finalExam.getId());
        if (activeQuestions.size() != finalExam.getQuestionCount()) {
            throw new PublishValidationException();
        }

        for (ExamQuestion question : activeQuestions) {
            List<ExamOption> options = question.getOptions();
            long correctCount = options.stream().filter(ExamOption::isCorrect).count();
            if (options.size() < 2 || correctCount != 1) {
                throw new PublishValidationException();
            }
        }
    }
}
