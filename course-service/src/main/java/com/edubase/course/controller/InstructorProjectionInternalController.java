package com.edubase.course.controller;

import com.edubase.commonCore.utils.RestResponse;
import com.edubase.course.controller.base.RestBaseController;
import com.edubase.course.dto.internal.InstructorSummariesRequest;
import com.edubase.course.dto.response.InstructorSummaryResponse;
import com.edubase.course.service.concretes.InstructorProjectionReconciliationService;
import com.edubase.course.service.concretes.InstructorProjectionService;
import com.edubase.course.service.concretes.InternalApiKeyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/internal/instructor-projections")
public class InstructorProjectionInternalController extends RestBaseController {

    private static final String INTERNAL_API_KEY_HEADER = "X-Internal-Api-Key";

    private final InstructorProjectionService instructorProjectionService;
    private final InstructorProjectionReconciliationService reconciliationService;
    private final InternalApiKeyService internalApiKeyService;

    @GetMapping("/{id}/summary")
    public ResponseEntity<RestResponse<InstructorSummaryResponse>> getProjectionSummary(
            @RequestHeader(name = INTERNAL_API_KEY_HEADER, required = false) String apiKey,
            @PathVariable("id") String instructorId) {
        internalApiKeyService.validate(apiKey);
        InstructorSummaryResponse summary = instructorProjectionService.findSummaryByInstructorId(instructorId).orElse(null);
        return ok(summary);
    }

    @PostMapping("/reconcile/{id}")
    public ResponseEntity<RestResponse<InstructorSummaryResponse>> reconcileOne(
            @RequestHeader(name = INTERNAL_API_KEY_HEADER, required = false) String apiKey,
            @PathVariable("id") String instructorId) {
        internalApiKeyService.validate(apiKey);
        return ok(reconciliationService.reconcileOne(instructorId));
    }

    @PostMapping("/reconcile")
    public ResponseEntity<RestResponse<List<InstructorSummaryResponse>>> reconcileMany(
            @RequestHeader(name = INTERNAL_API_KEY_HEADER, required = false) String apiKey,
            @RequestBody @Valid InstructorSummariesRequest request) {
        internalApiKeyService.validate(apiKey);
        return ok(reconciliationService.reconcileMany(request.instructorIds()));
    }
}
