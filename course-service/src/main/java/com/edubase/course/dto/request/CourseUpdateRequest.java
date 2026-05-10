package com.edubase.course.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CourseUpdateRequest {

    @NotBlank(message = "Title is required")
    @Size(max = 200, message = "Title must be at most 200 characters")
    private String title;

    @NotBlank(message = "Description is required")
    @Size(max = 2000, message = "Description must be at most 2000 characters")
    private String description;

    @NotNull(message = "Price is required")
    @PositiveOrZero(message = "Price must be zero or positive")
    private BigDecimal price;

    @NotBlank(message = "Category id is required")
    @Size(max = 100, message = "Category id must be at most 100 characters")
    private String categoryId;

    @NotNull(message = "Learning outcomes are required")
    @Size(min = 4, max = 4, message = "Learning outcomes must contain exactly 4 items")
    private List<
            @NotBlank(message = "Learning outcome cannot be blank")
            @Size(max = 160, message = "Learning outcome must be at most 160 characters")
                    String> learningOutcomes;

    @Size(max = 20, message = "Tags must contain at most 20 items")
    private List<
            @NotBlank(message = "Tag cannot be blank")
            @Size(max = 40, message = "Tag must be at most 40 characters")
                    String> tags;
}
