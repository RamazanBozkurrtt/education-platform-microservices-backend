package com.edubase.user.service.abstracts;

import com.edubase.user.dto.internal.InstructorSummaryResponse;

import java.util.Collection;
import java.util.List;

public interface InstructorInternalQueryService {

    public InstructorSummaryResponse getByInstructorId(String instructorId);

    public List<InstructorSummaryResponse> getByInstructorIds(Collection<String> instructorIds);

}
