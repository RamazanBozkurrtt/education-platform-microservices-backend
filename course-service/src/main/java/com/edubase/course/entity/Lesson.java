package com.edubase.course.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Lesson {

    private String id;
    private String title;
    private String summaryTitle;
    private String videoUrl;
    private Integer duration;
    private Integer orderIndex;
    private boolean completed;
}
