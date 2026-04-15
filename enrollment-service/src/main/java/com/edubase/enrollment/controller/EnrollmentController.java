package com.edubase.enrollment.controller;

import com.edubase.commonCore.utils.RestResponse;
import com.edubase.enrollment.controller.base.RestBaseController;
import com.edubase.enrollment.dto.request.EnrollmentCreateRequest;
import com.edubase.enrollment.dto.response.CustomPageResponse;
import com.edubase.enrollment.dto.response.EnrollmentResponse;
import com.edubase.enrollment.security.AuthContext;
import com.edubase.enrollment.security.AuthContextResolver;
import com.edubase.enrollment.service.abstracts.EnrollmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/enrollments")
@Tag(name = "Enrollments", description = "Enrollment management endpoints")
@SecurityRequirement(name = "bearerAuth")
public class EnrollmentController extends RestBaseController {

    private final EnrollmentService enrollmentService;
    private final AuthContextResolver authContextResolver;

    @PostMapping
    @Operation(summary = "Enroll to a course", description = "Creates a new enrollment for the authenticated user.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Enrollment created"),
            @ApiResponse(responseCode = "400", description = "Validation failed"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "409", description = "Enrollment already exists")
    })
    public ResponseEntity<RestResponse<EnrollmentResponse>> createEnrollment(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody @Valid EnrollmentCreateRequest request) {
        AuthContext authContext = authContextResolver.requireAuth(jwt);
        return created(enrollmentService.createEnrollment(authContext, request));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get enrollment by id", description = "Returns enrollment details by id.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Enrollment fetched"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "404", description = "Enrollment not found")
    })
    public ResponseEntity<RestResponse<EnrollmentResponse>> getEnrollmentById(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id) {
        AuthContext authContext = authContextResolver.requireAuth(jwt);
        return ok(enrollmentService.getEnrollmentById(authContext, id));
    }

    @GetMapping("/me")
    @Operation(summary = "List my enrollments", description = "Returns paginated enrollments for the authenticated user.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Enrollments fetched"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<RestResponse<CustomPageResponse<EnrollmentResponse>>> getMyEnrollments(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int pageNumber,
            @RequestParam(defaultValue = "10") int pageSize) {
        AuthContext authContext = authContextResolver.requireAuth(jwt);
        return ok(enrollmentService.getMyEnrollments(authContext, pageNumber, pageSize));
    }

    @GetMapping("/by-course/{courseId}")
    @Operation(summary = "List enrollments by course", description = "Returns paginated enrollments for a course (admin only).")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Enrollments fetched"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden")
    })
    public ResponseEntity<RestResponse<CustomPageResponse<EnrollmentResponse>>> getEnrollmentsByCourse(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String courseId,
            @RequestParam(defaultValue = "0") int pageNumber,
            @RequestParam(defaultValue = "10") int pageSize) {
        AuthContext authContext = authContextResolver.requireAuth(jwt);
        return ok(enrollmentService.getEnrollmentsByCourse(authContext, courseId, pageNumber, pageSize));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Cancel enrollment", description = "Cancels an enrollment by id.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Enrollment cancelled"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "404", description = "Enrollment not found")
    })
    public ResponseEntity<RestResponse<Void>> cancelEnrollment(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id) {
        AuthContext authContext = authContextResolver.requireAuth(jwt);
        enrollmentService.cancelEnrollment(authContext, id);
        return noContent();
    }
}
