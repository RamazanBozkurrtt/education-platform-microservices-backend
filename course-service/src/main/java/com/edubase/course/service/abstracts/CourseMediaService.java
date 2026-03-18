package com.edubase.course.service.abstracts;

import com.edubase.course.security.AuthContext;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

public interface CourseMediaService {

    ResponseEntity<ResourceRegion> getLessonVideo(AuthContext authContext, String courseId, String lessonId, HttpHeaders headers);
}
