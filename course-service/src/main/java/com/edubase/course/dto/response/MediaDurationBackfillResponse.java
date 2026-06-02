package com.edubase.course.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MediaDurationBackfillResponse {

    private long scannedCount;
    private long updatedCount;
    private long failedCount;
    private long skippedCount;
}
