package com.edubase.course.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "courses")
public class Course {

    @Id
    private String id;
    private String title;
    private String description;
    private BigDecimal price;
    private CourseStatus status;
    private String instructorId;

    @Builder.Default
    private List<String> categoryIds = new ArrayList<>();

    /**
     * @deprecated Backward compatibility field for older documents/clients.
     */
    @Deprecated
    private String categoryId;

    private String levelId;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    private Instant deletedAt;

    @Builder.Default
    private List<Lesson> lessons = new ArrayList<>();

    @Builder.Default
    private List<String> learningOutcomes = new ArrayList<>();

    @Builder.Default
    private List<String> tags = new ArrayList<>();
}
