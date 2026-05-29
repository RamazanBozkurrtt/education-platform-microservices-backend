package com.edubase.course.service.abstracts.finalexam;

import com.edubase.course.dto.request.finalexam.ExamQuestionCreateRequest;
import com.edubase.course.dto.request.finalexam.ExamQuestionUpdateRequest;
import com.edubase.course.dto.request.finalexam.FinalExamCreateRequest;
import com.edubase.course.dto.request.finalexam.FinalExamUpdateRequest;
import com.edubase.course.dto.response.finalexam.FinalExamManageResponse;
import com.edubase.course.security.AuthContext;
import org.springframework.web.multipart.MultipartFile;

public interface FinalExamInstructorService {

    FinalExamManageResponse createFinalExam(AuthContext authContext, String courseId, FinalExamCreateRequest request);

    FinalExamManageResponse updateFinalExam(AuthContext authContext, String courseId, FinalExamUpdateRequest request);

    FinalExamManageResponse getFinalExamForManage(AuthContext authContext, String courseId);

    void deleteFinalExam(AuthContext authContext, String courseId);

    FinalExamManageResponse addQuestion(AuthContext authContext, String courseId, ExamQuestionCreateRequest request);

    FinalExamManageResponse updateQuestion(
            AuthContext authContext,
            String courseId,
            Long questionId,
            ExamQuestionUpdateRequest request
    );

    FinalExamManageResponse deleteQuestion(AuthContext authContext, String courseId, Long questionId);

    void uploadQuestionImage(AuthContext authContext, String courseId, Long questionId, MultipartFile file);

    void deleteQuestionImage(AuthContext authContext, String courseId, Long questionId);
}
