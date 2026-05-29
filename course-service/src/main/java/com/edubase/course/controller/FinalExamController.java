package com.edubase.course.controller;

import com.edubase.commonCore.utils.RestResponse;
import com.edubase.course.controller.base.RestBaseController;
import com.edubase.course.dto.request.finalexam.ExamQuestionCreateRequest;
import com.edubase.course.dto.request.finalexam.ExamQuestionUpdateRequest;
import com.edubase.course.dto.request.finalexam.FinalExamCreateRequest;
import com.edubase.course.dto.request.finalexam.FinalExamUpdateRequest;
import com.edubase.course.dto.request.finalexam.SaveExamAnswerRequest;
import com.edubase.course.dto.request.finalexam.SubmitExamRequest;
import com.edubase.course.dto.response.finalexam.FinalExamManageResponse;
import com.edubase.course.dto.response.finalexam.StartExamAttemptResponse;
import com.edubase.course.dto.response.finalexam.StudentExamAttemptResponse;
import com.edubase.course.dto.response.finalexam.StudentExamOverviewResponse;
import com.edubase.course.dto.response.finalexam.SubmitExamResponse;
import com.edubase.course.security.AuthContext;
import com.edubase.course.security.AuthContextResolver;
import com.edubase.course.service.abstracts.finalexam.FinalExamInstructorService;
import com.edubase.course.service.abstracts.finalexam.FinalExamStudentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping({"/courses", "/api/v1/courses"})
@Tag(name = "Final Exam", description = "Course final exam management and student attempt endpoints")
@SecurityRequirement(name = "bearerAuth")
public class FinalExamController extends RestBaseController {

    private final AuthContextResolver authContextResolver;
    private final FinalExamInstructorService finalExamInstructorService;
    private final FinalExamStudentService finalExamStudentService;

    @PostMapping("/{courseId}/final-exam")
    @Operation(summary = "Create final exam for course")
    public ResponseEntity<RestResponse<FinalExamManageResponse>> createFinalExam(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String courseId,
            @RequestBody @Valid FinalExamCreateRequest request
    ) {
        AuthContext authContext = authContextResolver.requireAuth(jwt);
        return created(finalExamInstructorService.createFinalExam(authContext, courseId, request));
    }

    @PutMapping("/{courseId}/final-exam")
    @Operation(summary = "Update final exam for course")
    public ResponseEntity<RestResponse<FinalExamManageResponse>> updateFinalExam(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String courseId,
            @RequestBody @Valid FinalExamUpdateRequest request
    ) {
        AuthContext authContext = authContextResolver.requireAuth(jwt);
        return ok(finalExamInstructorService.updateFinalExam(authContext, courseId, request));
    }

    @GetMapping("/{courseId}/final-exam/manage")
    @Operation(summary = "Get final exam for instructor/admin manage view")
    public ResponseEntity<RestResponse<FinalExamManageResponse>> getFinalExamManage(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String courseId
    ) {
        AuthContext authContext = authContextResolver.requireAuth(jwt);
        return ok(finalExamInstructorService.getFinalExamForManage(authContext, courseId));
    }

    @DeleteMapping("/{courseId}/final-exam")
    @Operation(summary = "Delete/deactivate course final exam")
    public ResponseEntity<RestResponse<Void>> deleteFinalExam(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String courseId
    ) {
        AuthContext authContext = authContextResolver.requireAuth(jwt);
        finalExamInstructorService.deleteFinalExam(authContext, courseId);
        return noContent();
    }

    @PostMapping("/{courseId}/final-exam/questions")
    @Operation(summary = "Add question to final exam")
    public ResponseEntity<RestResponse<FinalExamManageResponse>> addQuestion(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String courseId,
            @RequestBody @Valid ExamQuestionCreateRequest request
    ) {
        AuthContext authContext = authContextResolver.requireAuth(jwt);
        return created(finalExamInstructorService.addQuestion(authContext, courseId, request));
    }

    @PutMapping("/{courseId}/final-exam/questions/{questionId}")
    @Operation(summary = "Update question in final exam")
    public ResponseEntity<RestResponse<FinalExamManageResponse>> updateQuestion(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String courseId,
            @PathVariable Long questionId,
            @RequestBody @Valid ExamQuestionUpdateRequest request
    ) {
        AuthContext authContext = authContextResolver.requireAuth(jwt);
        return ok(finalExamInstructorService.updateQuestion(authContext, courseId, questionId, request));
    }

    @DeleteMapping("/{courseId}/final-exam/questions/{questionId}")
    @Operation(summary = "Delete question from final exam")
    public ResponseEntity<RestResponse<FinalExamManageResponse>> deleteQuestion(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String courseId,
            @PathVariable Long questionId
    ) {
        AuthContext authContext = authContextResolver.requireAuth(jwt);
        return ok(finalExamInstructorService.deleteQuestion(authContext, courseId, questionId));
    }

    @PutMapping(value = "/{courseId}/final-exam/questions/{questionId}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload/update question image")
    public ResponseEntity<RestResponse<Void>> uploadQuestionImage(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String courseId,
            @PathVariable Long questionId,
            @RequestPart("file") MultipartFile file
    ) {
        AuthContext authContext = authContextResolver.requireAuth(jwt);
        finalExamInstructorService.uploadQuestionImage(authContext, courseId, questionId, file);
        return noContent();
    }

    @PostMapping(value = "/{courseId}/final-exam/questions/{questionId}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload question image")
    public ResponseEntity<RestResponse<Void>> uploadQuestionImageViaPost(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String courseId,
            @PathVariable Long questionId,
            @RequestPart("file") MultipartFile file
    ) {
        AuthContext authContext = authContextResolver.requireAuth(jwt);
        finalExamInstructorService.uploadQuestionImage(authContext, courseId, questionId, file);
        return noContent();
    }

    @DeleteMapping("/{courseId}/final-exam/questions/{questionId}/image")
    @Operation(summary = "Delete question image")
    public ResponseEntity<RestResponse<Void>> deleteQuestionImage(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String courseId,
            @PathVariable Long questionId
    ) {
        AuthContext authContext = authContextResolver.requireAuth(jwt);
        finalExamInstructorService.deleteQuestionImage(authContext, courseId, questionId);
        return noContent();
    }

    @GetMapping("/{courseId}/final-exam/overview")
    @Operation(summary = "Student final exam overview")
    public ResponseEntity<RestResponse<StudentExamOverviewResponse>> getOverview(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String courseId
    ) {
        AuthContext authContext = authContextResolver.requireAuth(jwt);
        return ok(finalExamStudentService.getOverview(authContext, courseId));
    }

    @PostMapping("/{courseId}/final-exam/attempts")
    @Operation(summary = "Start student final exam attempt")
    public ResponseEntity<RestResponse<StartExamAttemptResponse>> startAttempt(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String courseId
    ) {
        AuthContext authContext = authContextResolver.requireAuth(jwt);
        return created(finalExamStudentService.startAttempt(authContext, courseId));
    }

    @GetMapping("/{courseId}/final-exam/attempts/{attemptId}")
    @Operation(summary = "Get student final exam attempt details")
    public ResponseEntity<RestResponse<StudentExamAttemptResponse>> getAttempt(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String courseId,
            @PathVariable Long attemptId
    ) {
        AuthContext authContext = authContextResolver.requireAuth(jwt);
        return ok(finalExamStudentService.getAttempt(authContext, courseId, attemptId));
    }

    @PutMapping("/{courseId}/final-exam/attempts/{attemptId}/answers")
    @Operation(summary = "Autosave student final exam answers")
    public ResponseEntity<RestResponse<StudentExamAttemptResponse>> saveAnswers(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String courseId,
            @PathVariable Long attemptId,
            @RequestBody @Valid SaveExamAnswerRequest request
    ) {
        AuthContext authContext = authContextResolver.requireAuth(jwt);
        return ok(finalExamStudentService.saveAnswers(authContext, courseId, attemptId, request));
    }

    @PostMapping("/{courseId}/final-exam/attempts/{attemptId}/submit")
    @Operation(summary = "Submit student final exam attempt")
    public ResponseEntity<RestResponse<SubmitExamResponse>> submitAttempt(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String courseId,
            @PathVariable Long attemptId,
            @RequestBody(required = false) SubmitExamRequest request
    ) {
        AuthContext authContext = authContextResolver.requireAuth(jwt);
        return ok(finalExamStudentService.submitAttempt(
                authContext,
                courseId,
                attemptId,
                request == null ? new SubmitExamRequest() : request
        ));
    }

    @PostMapping("/{courseId}/final-exam/attempts/{attemptId}/terminate")
    @Operation(summary = "Terminate student final exam attempt")
    public ResponseEntity<RestResponse<SubmitExamResponse>> terminateAttempt(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String courseId,
            @PathVariable Long attemptId,
            @RequestParam(value = "reason", required = false) String reason
    ) {
        AuthContext authContext = authContextResolver.requireAuth(jwt);
        return ok(finalExamStudentService.terminateAttempt(authContext, courseId, attemptId, reason));
    }
}
