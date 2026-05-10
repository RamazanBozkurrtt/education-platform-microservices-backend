package com.edubase.user.controller;

import com.edubase.commonCore.exceptions.BusinessException;
import com.edubase.commonCore.exceptions.ErrorCode;
import com.edubase.commonCore.utils.RestResponse;
import com.edubase.user.controller.base.RestBaseController;
import com.edubase.user.dto.request.InstructorApplyRequest;
import com.edubase.user.dto.response.InstructorProfileResponse;
import com.edubase.user.service.abstracts.InstructorService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/instructors")
@Tag(name = "Instructors", description = "Instructor onboarding and profile management endpoints")
@SecurityRequirement(name = "bearerAuth")
public class InstructorController extends RestBaseController {

    private final InstructorService instructorService;

    @PostMapping({"/me/apply", "/me"})
    public ResponseEntity<RestResponse<InstructorProfileResponse>> apply(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody @Valid InstructorApplyRequest request) {
        String bearerToken = buildBearerToken(jwt);
        return ok(instructorService.apply(
                extractAuthUserId(jwt),
                extractAuthEmail(jwt),
                bearerToken,
                request
        ));
    }

    @GetMapping("/me")
    public ResponseEntity<RestResponse<InstructorProfileResponse>> getMe(@AuthenticationPrincipal Jwt jwt) {
        return ok(instructorService.getMe(
                extractAuthUserId(jwt),
                extractAuthEmail(jwt),
                extractRoles(jwt)
        ));
    }

    @PutMapping("/me")
    public ResponseEntity<RestResponse<InstructorProfileResponse>> updateMe(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody @Valid InstructorApplyRequest request) {
        return ok(instructorService.updateMe(
                extractAuthUserId(jwt),
                extractAuthEmail(jwt),
                extractRoles(jwt),
                request
        ));
    }

    private String extractAuthEmail(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null || jwt.getSubject().isBlank()) {
            throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED);
        }
        return jwt.getSubject().trim().toLowerCase();
    }

    private Long extractAuthUserId(Jwt jwt) {
        if (jwt == null) {
            throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED);
        }

        Object userIdClaim = jwt.getClaim("user_id");
        if (userIdClaim instanceof Number numberValue) {
            return numberValue.longValue();
        }
        if (userIdClaim instanceof String stringValue && !stringValue.isBlank()) {
            try {
                return Long.parseLong(stringValue.trim());
            } catch (NumberFormatException ignore) {
                return null;
            }
        }

        String tokenId = jwt.getId();
        if (tokenId == null || tokenId.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(tokenId.trim());
        } catch (NumberFormatException ignore) {
            return null;
        }
    }

    private String buildBearerToken(Jwt jwt) {
        if (jwt == null || jwt.getTokenValue() == null || jwt.getTokenValue().isBlank()) {
            throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED);
        }
        return "Bearer " + jwt.getTokenValue();
    }

    private List<String> extractRoles(Jwt jwt) {
        if (jwt == null) {
            return List.of();
        }
        List<String> roles = jwt.getClaimAsStringList("roles");
        return roles == null ? List.of() : roles;
    }
}
