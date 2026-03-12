package com.edubase.course.controller;

import com.edubase.course.security.AuthContext;
import com.edubase.course.security.AuthContextResolver;
import com.edubase.course.service.abstracts.CourseMediaService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ResourceRegion;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/courses")
public class CourseMediaController {

    private final CourseMediaService courseMediaService;
    private final AuthContextResolver authContextResolver;

    @GetMapping(value = "/{courseId}/lessons/{lessonId}/video", produces = "video/mp4")
    public ResponseEntity<ResourceRegion> getLessonVideo(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String courseId,
            @PathVariable String lessonId,
            @RequestHeader HttpHeaders headers) {
        AuthContext authContext = authContextResolver.requireAuth(jwt);
        return courseMediaService.getLessonVideo(authContext, courseId, lessonId, headers);
    }
}
