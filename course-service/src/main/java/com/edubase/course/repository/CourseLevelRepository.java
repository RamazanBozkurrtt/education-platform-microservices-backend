package com.edubase.course.repository;

import com.edubase.course.entity.CourseLevel;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface CourseLevelRepository extends MongoRepository<CourseLevel, String> {
    boolean existsByLevelName(String levelName);

    List<CourseLevel> findAllByOrderByDisplayOrderAscLevelNameAsc();
}
