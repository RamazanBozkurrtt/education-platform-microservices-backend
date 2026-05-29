package com.edubase.course.service.abstracts.finalexam;

import com.edubase.course.entity.Course;

public interface CourseCompletionPolicy {

    boolean isCompleted(String studentId, Course course);
}
