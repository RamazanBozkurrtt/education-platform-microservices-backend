package com.edubase.course.controller;

import com.edubase.commonCore.utils.RestResponse;
import com.edubase.course.controller.base.RestBaseController;
import com.edubase.course.dto.request.LessonProgressUpdateRequest;
import com.edubase.course.dto.response.CourseProgressSummaryResponse;
import com.edubase.course.dto.response.LessonProgressResponse;
import com.edubase.course.security.AuthContext;
import com.edubase.course.security.AuthContextResolver;
import com.edubase.course.service.abstracts.LessonProgressService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping({"/courses", "/api/v1/courses"})
@Tag(name = "Lesson Progress", description = "Lesson and course watch progress endpoints")
@SecurityRequirement(name = "bearerAuth")
public class LessonProgressController extends RestBaseController {

    private final LessonProgressService lessonProgressService;
    private final AuthContextResolver authContextResolver;

    @PutMapping("/{courseId}/lessons/{lessonId}/progress")
    @Operation(summary = "Update lesson progress", description = "Creates or updates progress for the authenticated user.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Progress updated"),
            @ApiResponse(responseCode = "400", description = "Validation failed"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden"),
            @ApiResponse(responseCode = "404", description = "Course/Lesson not found")
    })
    public ResponseEntity<RestResponse<LessonProgressResponse>> updateLessonProgress(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String courseId,
            @PathVariable String lessonId,
            @RequestBody @Valid LessonProgressUpdateRequest request) {
        AuthContext authContext = authContextResolver.requireAuth(jwt);
        return ok(lessonProgressService.updateLessonProgress(authContext, courseId, lessonId, request));
    }

    @GetMapping("/{courseId}/lessons/{lessonId}/progress")
    @Operation(summary = "Get lesson progress", description = "Returns current user's progress for a lesson.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Progress fetched"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden"),
            @ApiResponse(responseCode = "404", description = "Course/Lesson not found")
    })
    public ResponseEntity<RestResponse<LessonProgressResponse>> getLessonProgress(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String courseId,
            @PathVariable String lessonId) {
        AuthContext authContext = authContextResolver.requireAuth(jwt);
        return ok(lessonProgressService.getLessonProgress(authContext, courseId, lessonId));
    }

    @GetMapping("/{courseId}/progress")
    @Operation(summary = "Get course progress summary", description = "Returns summary progress for all lessons in a course.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Summary fetched"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden"),
            @ApiResponse(responseCode = "404", description = "Course not found")
    })
    public ResponseEntity<RestResponse<CourseProgressSummaryResponse>> getCourseProgressSummary(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String courseId) {
        AuthContext authContext = authContextResolver.requireAuth(jwt);
        return ok(lessonProgressService.getCourseProgressSummary(authContext, courseId));
    }

    @GetMapping("/{courseId}/lessons/progress")
    @Operation(summary = "List lesson progresses in course", description = "Returns all progress records of current user in the course.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Progresses fetched"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden"),
            @ApiResponse(responseCode = "404", description = "Course not found")
    })
    public ResponseEntity<RestResponse<List<LessonProgressResponse>>> getCourseLessonProgresses(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String courseId) {
        AuthContext authContext = authContextResolver.requireAuth(jwt);
        return ok(lessonProgressService.getCourseLessonProgresses(authContext, courseId));
    }
}
