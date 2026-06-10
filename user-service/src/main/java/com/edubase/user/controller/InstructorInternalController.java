package com.edubase.user.controller;

import com.edubase.commonCore.utils.RestResponse;
import com.edubase.user.controller.base.RestBaseController;
import com.edubase.user.dto.internal.InstructorStatusUpdateRequest;
import com.edubase.user.dto.internal.InstructorSummariesRequest;
import com.edubase.user.dto.internal.InstructorSummaryResponse;
import com.edubase.user.service.concretes.InstructorInternalCommandServiceImpl;
import com.edubase.user.service.concretes.InstructorInternalQueryServiceImpl;
import com.edubase.user.service.concretes.InternalApiKeyServiceImpl;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/internal/instructors")
public class InstructorInternalController extends RestBaseController {

    private static final String INTERNAL_API_KEY_HEADER = "X-Internal-Api-Key";

    private final InstructorInternalQueryServiceImpl instructorInternalQueryServiceImpl;
    private final InstructorInternalCommandServiceImpl instructorInternalCommandServiceImpl;
    private final InternalApiKeyServiceImpl internalApiKeyServiceImpl;

    @GetMapping("/{id}/summary")
    public ResponseEntity<RestResponse<InstructorSummaryResponse>> getInstructorSummary(
            @RequestHeader(name = INTERNAL_API_KEY_HEADER, required = false) String apiKey,
            @PathVariable("id") String instructorId) {
        internalApiKeyServiceImpl.validate(apiKey);
        return ok(instructorInternalQueryServiceImpl.getByInstructorId(instructorId));
    }

    @PostMapping("/summaries")
    public ResponseEntity<RestResponse<List<InstructorSummaryResponse>>> getInstructorSummaries(
            @RequestHeader(name = INTERNAL_API_KEY_HEADER, required = false) String apiKey,
            @RequestBody @Valid InstructorSummariesRequest request) {
        internalApiKeyServiceImpl.validate(apiKey);
        return ok(instructorInternalQueryServiceImpl.getByInstructorIds(request.instructorIds()));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<RestResponse<InstructorSummaryResponse>> updateInstructorStatus(
            @RequestHeader(name = INTERNAL_API_KEY_HEADER, required = false) String apiKey,
            @PathVariable("id") String instructorId,
            @RequestBody @Valid InstructorStatusUpdateRequest request) {
        internalApiKeyServiceImpl.validate(apiKey);
        return ok(instructorInternalCommandServiceImpl.changeStatus(instructorId, request.status()));
    }
}
