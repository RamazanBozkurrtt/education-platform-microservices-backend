package com.edubase.course.controller;

import com.edubase.commonCore.utils.RestResponse;
import com.edubase.course.controller.base.RestBaseController;
import com.edubase.course.dto.request.CourseCreateRequest;
import com.edubase.course.dto.request.CourseUpdateRequest;
import com.edubase.course.dto.request.LessonCreateRequest;
import com.edubase.course.dto.request.LessonUpdateRequest;
import com.edubase.course.dto.response.CourseResponse;
import com.edubase.course.dto.response.CustomPageResponse;
import com.edubase.course.security.AuthContext;
import com.edubase.course.security.AuthContextResolver;
import com.edubase.course.service.abstracts.CourseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/courses")
public class CourseController extends RestBaseController {

    private final CourseService courseService;
    private final AuthContextResolver authContextResolver;

    @PostMapping
    public ResponseEntity<RestResponse<CourseResponse>> createCourse(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody @Valid CourseCreateRequest request) {
        AuthContext authContext = authContextResolver.requireAuth(jwt);
        return created(courseService.createCourse(authContext, request));
    }

    @GetMapping("/public/{id}")
    public ResponseEntity<RestResponse<CourseResponse>> getPublicCourseById(@PathVariable String id) {
        return ok(courseService.getPublicCourseById(id));
    }

    @GetMapping("/public")
    public ResponseEntity<RestResponse<CustomPageResponse<CourseResponse>>> getPublicCourses(
            @RequestParam(defaultValue = "0") int pageNumber,
            @RequestParam(defaultValue = "10") int pageSize) {
        return ok(courseService.getPublicCourses(pageNumber, pageSize));
    }

    @GetMapping("/{id}")
    public ResponseEntity<RestResponse<CourseResponse>> getCourseById(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String id) {
        AuthContext authContext = authContextResolver.requireAuth(jwt);
        return ok(courseService.getCourseById(authContext, id));
    }

    @GetMapping
    public ResponseEntity<RestResponse<CustomPageResponse<CourseResponse>>> getCourses(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int pageNumber,
            @RequestParam(defaultValue = "10") int pageSize) {
        AuthContext authContext = authContextResolver.requireAuth(jwt);
        return ok(courseService.getCourses(authContext, pageNumber, pageSize));
    }

    @GetMapping("/me")
    public ResponseEntity<RestResponse<CustomPageResponse<CourseResponse>>> getMyCourses(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int pageNumber,
            @RequestParam(defaultValue = "10") int pageSize) {
        AuthContext authContext = authContextResolver.requireAuth(jwt);
        return ok(courseService.getMyCourses(authContext, pageNumber, pageSize));
    }

    @PutMapping("/{id}")
    public ResponseEntity<RestResponse<CourseResponse>> updateCourse(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String id,
            @RequestBody @Valid CourseUpdateRequest request) {
        AuthContext authContext = authContextResolver.requireAuth(jwt);
        return ok(courseService.updateCourse(authContext, id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<RestResponse<Void>> deleteCourse(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String id) {
        AuthContext authContext = authContextResolver.requireAuth(jwt);
        courseService.deleteCourse(authContext, id);
        return noContent();
    }

    @PostMapping("/{id}/lessons")
    public ResponseEntity<RestResponse<CourseResponse>> addLesson(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String id,
            @RequestBody @Valid LessonCreateRequest request) {
        AuthContext authContext = authContextResolver.requireAuth(jwt);
        return created(courseService.addLesson(authContext, id, request));
    }

    @PutMapping("/{courseId}/lessons/{lessonId}")
    public ResponseEntity<RestResponse<CourseResponse>> updateLesson(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String courseId,
            @PathVariable String lessonId,
            @RequestBody @Valid LessonUpdateRequest request) {
        AuthContext authContext = authContextResolver.requireAuth(jwt);
        return ok(courseService.updateLesson(authContext, courseId, lessonId, request));
    }

    @DeleteMapping("/{courseId}/lessons/{lessonId}")
    public ResponseEntity<RestResponse<Void>> deleteLesson(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String courseId,
            @PathVariable String lessonId) {
        AuthContext authContext = authContextResolver.requireAuth(jwt);
        courseService.deleteLesson(authContext, courseId, lessonId);
        return noContent();
    }

    @PostMapping("/{id}/publish")
    public ResponseEntity<RestResponse<CourseResponse>> publishCourse(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String id) {
        AuthContext authContext = authContextResolver.requireAuth(jwt);
        return ok(courseService.publishCourse(authContext, id));
    }
}
