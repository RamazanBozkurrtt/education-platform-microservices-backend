package com.edubase.course.controller;

import com.edubase.course.dto.response.MediaDurationBackfillResponse;
import com.edubase.course.dto.response.VideoPlaybackUrlResponse;
import com.edubase.course.security.AuthContext;
import com.edubase.course.security.AuthContextResolver;
import com.edubase.course.service.abstracts.CourseMediaService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping({"/courses", "/api/v1/courses"})
public class CourseMediaController {

    private final CourseMediaService courseMediaService;
    private final AuthContextResolver authContextResolver;

    @GetMapping(value = {
            "/{courseId}/lessons/{lessonId}/video",
            "/{courseId}/lessons/{lessonId}/video/stream"
    }, produces = "video/mp4")
    public ResponseEntity<Resource> getLessonVideo(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String courseId,
            @PathVariable String lessonId,
            @RequestHeader HttpHeaders headers) {
        AuthContext authContext = authContextResolver.requireAuth(jwt);
        return courseMediaService.getLessonVideo(authContext, courseId, lessonId, headers);
    }

    @GetMapping(value = {
            "/public/{courseId}/lessons/{lessonId}/video",
            "/public/{courseId}/lessons/{lessonId}/video/stream"
    }, produces = "video/mp4")
    public ResponseEntity<Resource> getPublicLessonVideoBySignature(
            @PathVariable String courseId,
            @PathVariable String lessonId,
            @RequestParam("uid") String userId,
            @RequestParam("exp") long expiresAt,
            @RequestParam("sig") String signature,
            @RequestHeader HttpHeaders headers) {
        return courseMediaService.getPublicLessonVideoBySignature(courseId, lessonId, userId, expiresAt, signature, headers);
    }

    @PostMapping("/{courseId}/lessons/{lessonId}/video/playback-url")
    public ResponseEntity<VideoPlaybackUrlResponse> createLessonVideoPlaybackUrl(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String courseId,
            @PathVariable String lessonId) {
        AuthContext authContext = authContextResolver.requireAuth(jwt);
        return ResponseEntity.ok(courseMediaService.createLessonVideoPlaybackUrl(authContext, courseId, lessonId));
    }

    @GetMapping("/public/{courseId}/image")
    public ResponseEntity<Resource> getPublicCourseImage(@PathVariable String courseId) {
        return courseMediaService.getPublicCourseImage(courseId);
    }

    @GetMapping("/{courseId}/image")
    public ResponseEntity<Resource> getCourseImage(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String courseId) {
        AuthContext authContext = authContextResolver.requireAuth(jwt);
        return courseMediaService.getCourseImage(authContext, courseId);
    }

    @PutMapping(value = "/{courseId}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> uploadCourseImage(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String courseId,
            @RequestPart("file") MultipartFile file) {
        AuthContext authContext = authContextResolver.requireAuth(jwt);
        courseMediaService.uploadCourseImage(authContext, courseId, file);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{courseId}/image")
    public ResponseEntity<Void> deleteCourseImage(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String courseId) {
        AuthContext authContext = authContextResolver.requireAuth(jwt);
        courseMediaService.deleteCourseImage(authContext, courseId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping(value = "/{courseId}/lessons/{lessonId}/video", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> uploadLessonVideo(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String courseId,
            @PathVariable String lessonId,
            @RequestPart("file") MultipartFile file) {
        AuthContext authContext = authContextResolver.requireAuth(jwt);
        courseMediaService.uploadLessonVideo(authContext, courseId, lessonId, file);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{courseId}/lessons/{lessonId}/video")
    public ResponseEntity<Void> deleteLessonVideo(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String courseId,
            @PathVariable String lessonId) {
        AuthContext authContext = authContextResolver.requireAuth(jwt);
        courseMediaService.deleteLessonVideo(authContext, courseId, lessonId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/admin/media/backfill-durations")
    public ResponseEntity<MediaDurationBackfillResponse> backfillLessonDurations(
            @AuthenticationPrincipal Jwt jwt) {
        AuthContext authContext = authContextResolver.requireAuth(jwt);
        return ResponseEntity.ok(courseMediaService.backfillLessonDurations(authContext));
    }
}
