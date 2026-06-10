package com.edubase.course.recommendation.controller;

import com.edubase.commonCore.utils.RestResponse;
import com.edubase.course.controller.base.RestBaseController;
import com.edubase.course.recommendation.dto.response.RecommendationExplainResponse;
import com.edubase.course.recommendation.dto.response.RecommendationListResponse;
import com.edubase.course.recommendation.service.RecommendationFacadeService;
import com.edubase.course.security.AuthContext;
import com.edubase.course.security.AuthContextResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping({"/recommendations", "/api/v1/recommendations"})
@Tag(name = "Recommendations", description = "Personalized course recommendation endpoints")
@SecurityRequirement(name = "bearerAuth")
public class RecommendationController extends RestBaseController {

    private final RecommendationFacadeService recommendationFacadeService;
    private final AuthContextResolver authContextResolver;

    @GetMapping("/dashboard")
    @Operation(summary = "Dashboard recommendations", description = "Returns personalized course recommendations for the authenticated user dashboard.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Recommendations fetched"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "400", description = "Validation failed")
    })
    public ResponseEntity<RestResponse<RecommendationListResponse>> getDashboardRecommendations(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "10") Integer limit) {
        AuthContext authContext = authContextResolver.requireAuth(jwt);
        return ok(recommendationFacadeService.getDashboardRecommendations(authContext, limit));
    }

    @GetMapping("/search")
    @Operation(summary = "Search recommendations", description = "Returns personalized recommendations based on search query and user learning profile.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Recommendations fetched"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "400", description = "Validation failed")
    })
    public ResponseEntity<RestResponse<RecommendationListResponse>> getSearchRecommendations(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam String query,
            @RequestParam(defaultValue = "10") Integer limit) {
        AuthContext authContext = authContextResolver.requireAuth(jwt);
        return ok(recommendationFacadeService.getSearchRecommendations(authContext, query, limit));
    }

    @GetMapping("/explain/me")
    @Operation(summary = "Explain current user recommendation profile", description = "Returns computed recommendation profile details used by recommendation strategy.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Profile explanation fetched"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<RestResponse<RecommendationExplainResponse>> explainCurrentUserRecommendations(
            @AuthenticationPrincipal Jwt jwt) {
        AuthContext authContext = authContextResolver.requireAuth(jwt);
        return ok(recommendationFacadeService.explainCurrentUser(authContext));
    }
}
