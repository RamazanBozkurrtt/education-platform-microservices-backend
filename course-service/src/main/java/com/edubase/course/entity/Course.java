package com.edubase.course.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
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
    private String categoryId;
    private Instant createdAt;
    private Instant updatedAt;
    @Builder.Default
    private List<Lesson> lessons = new ArrayList<>();
}
