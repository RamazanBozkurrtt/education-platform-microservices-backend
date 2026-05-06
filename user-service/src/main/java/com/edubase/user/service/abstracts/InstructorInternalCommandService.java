package com.edubase.user.service.abstracts;

import com.edubase.user.dto.internal.InstructorSummaryResponse;
import com.edubase.user.entity.UserStatus;

public interface InstructorInternalCommandService {
    public InstructorSummaryResponse changeStatus(String instructorId, UserStatus targetStatus);
}
