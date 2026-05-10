package com.edubase.course.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Lesson {

    private String id;
    private String title;
    private String summaryTitle;
    private String videoUrl;
    private Instant videoUpdatedAt;
    private Integer duration;
    private Integer orderIndex;
    private boolean completed;
}
