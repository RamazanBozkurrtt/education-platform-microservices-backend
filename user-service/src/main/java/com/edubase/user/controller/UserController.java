package com.edubase.user.controller;

import com.edubase.commonCore.exceptions.BusinessException;
import com.edubase.commonCore.exceptions.ErrorCode;
import com.edubase.commonCore.utils.RestResponse;
import com.edubase.user.controller.base.RestBaseController;
import com.edubase.user.dto.request.UserProfileRequest;
import com.edubase.user.dto.response.CustomPageResponse;
import com.edubase.user.dto.response.UserProfileResponse;
import com.edubase.user.service.abstracts.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;


@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users")
@Tag(name = "Users", description = "User profile management endpoints")
@SecurityRequirement(name = "bearerAuth")
public class UserController extends RestBaseController {

    private final UserService userService;

    @GetMapping
    @Operation(summary = "List users", description = "Returns paginated user profiles.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Users fetched successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content)
    })
    public ResponseEntity<RestResponse<CustomPageResponse<UserProfileResponse>>> getAll(
            @Parameter(description = "Zero-based page number", example = "0")
            @RequestParam(defaultValue = "0") int pageNumber,
            @Parameter(description = "Page size", example = "10")
            @RequestParam(defaultValue = "10") int pageSize) {

        return ok(userService.getAll(pageNumber,pageSize));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get user by id", description = "Returns user profile by profile id.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User fetched successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content),
            @ApiResponse(responseCode = "404", description = "User not found", content = @Content)
    })
    public ResponseEntity<RestResponse<UserProfileResponse>> getById(@PathVariable Long id) {
        return ok(userService.getById(id));
    }

    @GetMapping("/me")
    @Operation(summary = "Get my profile", description = "Returns current authenticated user's profile.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Profile fetched successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content),
            @ApiResponse(responseCode = "404", description = "User not found", content = @Content)
    })
    public ResponseEntity<RestResponse<UserProfileResponse>> getMe(@AuthenticationPrincipal Jwt jwt) {
        return ok(userService.getMe(extractAuthUserId(jwt), extractAuthEmail(jwt)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update user profile", description = "Updates user profile by profile id.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User updated successfully"),
            @ApiResponse(responseCode = "400", description = "Validation failed", content = @Content),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content),
            @ApiResponse(responseCode = "404", description = "User not found", content = @Content)
    })
    public ResponseEntity<RestResponse<UserProfileResponse>> update(@PathVariable Long id, @RequestBody @Valid UserProfileRequest userProfileRequest) {
        return ok(userService.update(id, userProfileRequest));
    }

    @PutMapping("/me")
    @Operation(summary = "Update my profile", description = "Updates current authenticated user's profile.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Profile updated successfully"),
            @ApiResponse(responseCode = "400", description = "Validation failed", content = @Content),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content),
            @ApiResponse(responseCode = "404", description = "User not found", content = @Content)
    })
    public ResponseEntity<RestResponse<UserProfileResponse>> updateMe(@AuthenticationPrincipal Jwt jwt, @RequestBody @Valid UserProfileRequest userProfileRequest) {
        return ok(userService.updateMe(extractAuthUserId(jwt), extractAuthEmail(jwt), userProfileRequest));
    }

    @PutMapping(value = "/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload my avatar", description = "Uploads current authenticated user's avatar image.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Avatar uploaded successfully"),
            @ApiResponse(responseCode = "400", description = "Validation failed", content = @Content),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content),
            @ApiResponse(responseCode = "404", description = "User not found", content = @Content)
    })
    public ResponseEntity<RestResponse<UserProfileResponse>> uploadMyAvatar(
            @AuthenticationPrincipal Jwt jwt,
            @RequestPart("file") MultipartFile file) {
        return ok(userService.uploadMyAvatar(extractAuthUserId(jwt), extractAuthEmail(jwt), file));
    }

    @GetMapping("/public/avatar/{profileId}")
    @Operation(summary = "Get public avatar", description = "Returns profile avatar image for given profile id.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Avatar fetched successfully"),
            @ApiResponse(responseCode = "404", description = "Avatar not found", content = @Content)
    })
    public ResponseEntity<Resource> getPublicAvatar(@PathVariable Long profileId) {
        return userService.getPublicAvatar(profileId);
    }

    @PostMapping
    @Operation(summary = "Create user profile", description = "Creates a new user profile.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "User created successfully",
                    content = @Content(schema = @Schema(implementation = UserProfileResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation failed", content = @Content),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content)
    })
    public ResponseEntity<RestResponse<UserProfileResponse>> create(@RequestBody @Valid UserProfileRequest request){
        return created(userService.create(request));
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

        // Backward compatibility for legacy tokens where jti carried user id.
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

}
