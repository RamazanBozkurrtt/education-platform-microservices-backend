package com.edubase.course.dto.response;

import com.edubase.course.entity.CourseStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseResponse {

    private String id;
    private String imageUrl;
    private String title;
    private String description;
    private BigDecimal price;
    private CourseStatus status;
    private String instructorId;
    private InstructorSummaryResponse instructor;
    private String categoryId;
    private CategoryResponse category;
    private List<String> learningOutcomes;
    private List<String> tags;
    private Instant createdAt;
    private Instant updatedAt;
    private List<LessonResponse> lessons;
}
