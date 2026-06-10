package com.edubase.course.repository.finalexam;

import com.edubase.course.entity.finalexam.ExamOption;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ExamOptionRepository extends JpaRepository<ExamOption, Long> {

    List<ExamOption> findByQuestionIdIn(Collection<Long> questionIds);
}
