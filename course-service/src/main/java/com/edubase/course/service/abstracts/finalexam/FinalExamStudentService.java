package com.edubase.course.service.abstracts.finalexam;

import com.edubase.course.dto.request.finalexam.SaveExamAnswerRequest;
import com.edubase.course.dto.request.finalexam.SubmitExamRequest;
import com.edubase.course.dto.response.finalexam.StartExamAttemptResponse;
import com.edubase.course.dto.response.finalexam.StudentExamAttemptResponse;
import com.edubase.course.dto.response.finalexam.StudentExamOverviewResponse;
import com.edubase.course.dto.response.finalexam.SubmitExamResponse;
import com.edubase.course.security.AuthContext;

public interface FinalExamStudentService {

    StudentExamOverviewResponse getOverview(AuthContext authContext, String courseId);

    StartExamAttemptResponse startAttempt(AuthContext authContext, String courseId);

    StudentExamAttemptResponse getAttempt(AuthContext authContext, String courseId, Long attemptId);

    StudentExamAttemptResponse saveAnswers(
            AuthContext authContext,
            String courseId,
            Long attemptId,
            SaveExamAnswerRequest request
    );

    SubmitExamResponse submitAttempt(
            AuthContext authContext,
            String courseId,
            Long attemptId,
            SubmitExamRequest request
    );

    SubmitExamResponse terminateAttempt(AuthContext authContext, String courseId, Long attemptId, String reason);
}
