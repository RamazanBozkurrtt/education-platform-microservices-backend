package com.edubase.course.service.concretes;

import com.edubase.course.dto.response.InstructorSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;

@Service
@RequiredArgsConstructor
public class InstructorProjectionReconciliationService {

    private final UserInstructorSummaryClient userInstructorSummaryClient;
    private final InstructorProjectionService instructorProjectionService;

    public InstructorSummaryResponse reconcileOne(String instructorId) {
        return userInstructorSummaryClient.getById(instructorId)
                .map(summary -> {
                    instructorProjectionService.upsertFromSummary(summary);
                    return summary;
                })
                .orElse(null);
    }

    public List<InstructorSummaryResponse> reconcileMany(Collection<String> instructorIds) {
        if (instructorIds == null || instructorIds.isEmpty()) {
            return List.of();
        }
        List<InstructorSummaryResponse> summaries = userInstructorSummaryClient.getByIds(List.copyOf(instructorIds));
        summaries.forEach(instructorProjectionService::upsertFromSummary);
        return summaries;
    }
}
