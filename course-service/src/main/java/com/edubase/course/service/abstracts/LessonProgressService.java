package com.edubase.course.service.abstracts;

import com.edubase.course.dto.request.LessonProgressUpdateRequest;
import com.edubase.course.dto.response.CourseProgressSummaryResponse;
import com.edubase.course.dto.response.LessonProgressResponse;
import com.edubase.course.security.AuthContext;

import java.util.List;

public interface LessonProgressService {

    LessonProgressResponse updateLessonProgress(
            AuthContext authContext,
            String courseId,
            String lessonId,
            LessonProgressUpdateRequest request
    );

    LessonProgressResponse getLessonProgress(
            AuthContext authContext,
            String courseId,
            String lessonId
    );

    List<LessonProgressResponse> getCourseLessonProgresses(
            AuthContext authContext,
            String courseId
    );

    CourseProgressSummaryResponse getCourseProgressSummary(
            AuthContext authContext,
            String courseId
    );
}
