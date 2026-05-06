package com.edubase.course.service.abstracts;

import com.edubase.course.security.AuthContext;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

public interface CourseMediaService {

    ResponseEntity<ResourceRegion> getLessonVideo(AuthContext authContext, String courseId, String lessonId, HttpHeaders headers);

    ResponseEntity<Resource> getPublicCourseImage(String courseId);

    ResponseEntity<Resource> getCourseImage(AuthContext authContext, String courseId);

    void uploadCourseImage(AuthContext authContext, String courseId, MultipartFile file);

    void deleteCourseImage(AuthContext authContext, String courseId);

    void uploadLessonVideo(AuthContext authContext, String courseId, String lessonId, MultipartFile file);

    void deleteLessonVideo(AuthContext authContext, String courseId, String lessonId);
}
