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

    /**
     * Primary category id for backward compatibility with existing clients.
     */
    private String categoryId;

    /**
     * Primary category detail for backward compatibility with existing clients.
     */
    private CategoryResponse category;
    private String levelId;
    private CourseLevelResponse level;
    private List<String> categoryIds;
    private List<CategoryResponse> categories;
    private List<String> learningOutcomes;
    private List<String> tags;
    /**
     * @deprecated Use durationSeconds. Kept for backward compatibility.
     */
    @Deprecated
    private Integer duration;
    private Integer durationSeconds;
    private Integer totalDurationSeconds;
    private Instant createdAt;
    private Instant updatedAt;
    private List<LessonResponse> lessons;
}
