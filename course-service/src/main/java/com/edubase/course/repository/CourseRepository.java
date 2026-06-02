package com.edubase.course.repository;

import com.edubase.course.entity.Course;
import com.edubase.course.entity.CourseStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CourseRepository extends MongoRepository<Course, String> {
    Optional<Course> findByIdAndDeletedAtIsNull(String id);

    Optional<Course> findByIdAndStatusAndDeletedAtIsNull(String id, CourseStatus status);

    Page<Course> findAllByDeletedAtIsNull(Pageable pageable);

    Page<Course> findAllByStatusAndDeletedAtIsNull(CourseStatus status, Pageable pageable);

    Page<Course> findAllByInstructorIdAndDeletedAtIsNull(String instructorId, Pageable pageable);

    boolean existsByIdAndInstructorIdAndDeletedAtIsNull(String id, String instructorId);

    List<Course> findAllByIdInAndDeletedAtIsNull(Collection<String> ids);

    List<Course> findAllByIdInAndStatusAndDeletedAtIsNull(Collection<String> ids, CourseStatus status);
}
