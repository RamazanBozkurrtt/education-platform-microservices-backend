package com.edubase.course.security;

import com.edubase.course.repository.CourseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CourseSecurity {

    private final CourseRepository courseRepository;

    public boolean isAdmin(AuthContext authContext) {
        return authContext != null && authContext.role() == UserRole.ADMIN;
    }

    public boolean isAdminOrInstructor(AuthContext authContext) {
        if (authContext == null) {
            return false;
        }
        return authContext.role() == UserRole.ADMIN || authContext.role() == UserRole.INSTRUCTOR;
    }

    public boolean isAuthenticatedUser(AuthContext authContext) {
        return authContext != null
                && authContext.role() != UserRole.UNKNOWN
                && authContext.userId() != null
                && !authContext.userId().isBlank();
    }

    public boolean canManageCourse(AuthContext authContext, String courseId) {
        if (authContext == null || courseId == null || courseId.isBlank()) {
            return false;
        }
        if (authContext.role() == UserRole.ADMIN) {
            return true;
        }
        if (authContext.role() != UserRole.INSTRUCTOR) {
            return false;
        }
        return courseRepository.existsByIdAndInstructorId(courseId, authContext.userId());
    }
}
