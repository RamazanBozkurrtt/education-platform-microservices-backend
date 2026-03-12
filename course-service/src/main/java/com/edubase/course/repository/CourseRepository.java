package com.edubase.course.repository;

import com.edubase.course.entity.Course;
import com.edubase.course.entity.CourseStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface CourseRepository extends MongoRepository<Course, String> {
    Optional<Course> findByIdAndStatus(String id, CourseStatus status);

    Page<Course> findAllByStatus(CourseStatus status, Pageable pageable);

    Page<Course> findAllByInstructorId(String instructorId, Pageable pageable);

    boolean existsByIdAndInstructorId(String id, String instructorId);
}
