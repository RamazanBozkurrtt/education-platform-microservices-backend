package com.edubase.review.entity;

import com.edubase.commonJpa.entity.BaseEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(
        name = "reviews",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_reviews_course_user", columnNames = {"course_id", "user_id"})
        },
        indexes = {
                @Index(name = "idx_reviews_course_id", columnList = "course_id"),
                @Index(name = "idx_reviews_user_id", columnList = "user_id"),
                @Index(name = "idx_reviews_course_created_at", columnList = "course_id, created_at")
        }
)
@AttributeOverride(
        name = "id",
        column = @Column(name = "review_id")
)
public class Review extends BaseEntity {

    @Column(name = "course_id", nullable = false, length = 64)
    private String courseId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "rating", nullable = false)
    private Integer rating;

    @Column(name = "comment", nullable = false, length = 1000)
    private String comment;
}
