package com.edubase.course.entity;

import com.edubase.commonCore.events.InstructorStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "instructor_projection")
public class InstructorProjection {

    @Id
    @Column(name = "instructor_id", nullable = false, updatable = false, length = 64)
    private String instructorId;

    @Column(name = "full_name", nullable = false, length = 255)
    private String fullName;

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Column(name = "profile_image_url", length = 500)
    private String profileImageUrl;

    @Column(name = "headline", length = 255)
    private String headline;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private InstructorStatus status;

    @Column(name = "source_updated_at", nullable = false)
    private Instant sourceUpdatedAt;

    @Column(name = "last_event_id", nullable = false, length = 64)
    private String lastEventId;

    @Column(name = "last_event_version", nullable = false)
    private Long lastEventVersion;

    @Column(name = "projection_updated_at", nullable = false)
    private Instant projectionUpdatedAt;
}
