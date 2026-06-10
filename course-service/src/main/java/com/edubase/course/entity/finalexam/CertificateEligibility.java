package com.edubase.course.entity.finalexam;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "certificate_eligibility",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_certificate_eligibility_course_student",
                        columnNames = {"course_id", "student_id"}
                )
        }
)
public class CertificateEligibility {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "course_id", nullable = false, length = 64)
    private String courseId;

    @Column(name = "student_id", nullable = false, length = 64)
    private String studentId;

    @Column(name = "final_exam_id", nullable = false)
    private Long finalExamId;

    @Column(name = "attempt_id", nullable = false)
    private Long attemptId;

    @Column(name = "eligible", nullable = false)
    private boolean eligible;

    @Column(name = "earned_at", nullable = false)
    private Instant earnedAt;

    @Column(name = "created_by", nullable = false, length = 64, updatable = false)
    private String createdBy;

    @Column(name = "updated_by", nullable = false, length = 64)
    private String updatedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
