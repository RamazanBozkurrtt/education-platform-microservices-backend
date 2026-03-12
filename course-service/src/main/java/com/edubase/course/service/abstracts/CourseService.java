package com.edubase.course.service.abstracts;

import com.edubase.course.dto.request.CourseCreateRequest;
import com.edubase.course.dto.request.CourseUpdateRequest;
import com.edubase.course.dto.request.LessonCreateRequest;
import com.edubase.course.dto.request.LessonUpdateRequest;
import com.edubase.course.dto.response.CourseResponse;
import com.edubase.course.dto.response.CustomPageResponse;
import com.edubase.course.security.AuthContext;

public interface CourseService {

    CourseResponse createCourse(AuthContext authContext, CourseCreateRequest request);

    CourseResponse getCourseById(AuthContext authContext, String id);

    CourseResponse getPublicCourseById(String id);

    CustomPageResponse<CourseResponse> getCourses(AuthContext authContext, int pageNumber, int pageSize);

    CustomPageResponse<CourseResponse> getPublicCourses(int pageNumber, int pageSize);

    CustomPageResponse<CourseResponse> getMyCourses(AuthContext authContext, int pageNumber, int pageSize);

    CourseResponse updateCourse(AuthContext authContext, String id, CourseUpdateRequest request);

    void deleteCourse(AuthContext authContext, String id);

    CourseResponse addLesson(AuthContext authContext, String courseId, LessonCreateRequest request);

    CourseResponse updateLesson(AuthContext authContext, String courseId, String lessonId, LessonUpdateRequest request);

    void deleteLesson(AuthContext authContext, String courseId, String lessonId);

    CourseResponse publishCourse(AuthContext authContext, String id);
}
