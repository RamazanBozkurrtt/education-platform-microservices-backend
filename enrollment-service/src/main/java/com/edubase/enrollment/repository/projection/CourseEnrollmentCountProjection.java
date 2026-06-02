package com.edubase.enrollment.repository.projection;

public interface CourseEnrollmentCountProjection {

    String getCourseId();

    Long getEnrollmentCount();
}
