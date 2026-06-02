package com.edubase.course.service.abstracts;

import com.edubase.course.security.AuthContext;
import com.edubase.course.dto.response.MediaDurationBackfillResponse;
import com.edubase.course.dto.response.VideoPlaybackUrlResponse;
import com.edubase.course.entity.Course;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

public interface CourseMediaService {

    ResponseEntity<Resource> getLessonVideo(AuthContext authContext, String courseId, String lessonId, HttpHeaders headers);

    ResponseEntity<Resource> getPublicLessonVideoBySignature(String courseId, String lessonId, String userId, long expiresAt, String signature, HttpHeaders headers);

    VideoPlaybackUrlResponse createLessonVideoPlaybackUrl(AuthContext authContext, String courseId, String lessonId);

    ResponseEntity<Resource> getPublicCourseImage(String courseId);

    ResponseEntity<Resource> getCourseImage(AuthContext authContext, String courseId);

    void uploadCourseImage(AuthContext authContext, String courseId, MultipartFile file);

    void deleteCourseImage(AuthContext authContext, String courseId);

    void uploadLessonVideo(AuthContext authContext, String courseId, String lessonId, MultipartFile file);

    void deleteLessonVideo(AuthContext authContext, String courseId, String lessonId);

    void deleteCourseMediaAssets(Course course);

    void deleteLessonVideoAsset(String courseId, String lessonId);

    MediaDurationBackfillResponse backfillLessonDurations(AuthContext authContext);
}
