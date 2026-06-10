package com.edubase.course.repository.finalexam;

import com.edubase.course.entity.finalexam.CertificateEligibility;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CertificateEligibilityRepository extends JpaRepository<CertificateEligibility, Long> {

    Optional<CertificateEligibility> findByCourseIdAndStudentId(String courseId, String studentId);

    boolean existsByCourseIdAndStudentIdAndEligibleTrue(String courseId, String studentId);
}
