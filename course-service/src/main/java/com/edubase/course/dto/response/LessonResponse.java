package com.edubase.course.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LessonResponse {

    private String id;
    private String title;
    private String summaryTitle;
    private String videoUrl;
    /**
     * @deprecated Use durationSeconds. Kept for backward compatibility.
     */
    @Deprecated
    private Integer duration;
    private Integer durationSeconds;
    private Integer orderIndex;
    private boolean completed;
}
