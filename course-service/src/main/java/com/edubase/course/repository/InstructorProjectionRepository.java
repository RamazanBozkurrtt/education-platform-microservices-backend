package com.edubase.course.repository;

import com.edubase.course.entity.InstructorProjection;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InstructorProjectionRepository extends JpaRepository<InstructorProjection, String> {
}
