package com.edubase.user.controller;

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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


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

    @PutMapping("/{id}")
    @Operation(summary = "Update user profile", description = "Updates user profile by profile id.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User updated successfully"),
            @ApiResponse(responseCode = "400", description = "Validation failed", content = @Content),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content),
            @ApiResponse(responseCode = "404", description = "User not found", content = @Content)
    })
    public ResponseEntity<RestResponse<UserProfileResponse>> update(@PathVariable Long id, @RequestBody UserProfileRequest userProfileRequest) {
        return ok(userService.update(id, userProfileRequest));
    }

    @PostMapping
    @Operation(summary = "Create user profile", description = "Creates a new user profile.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User created successfully",
                    content = @Content(schema = @Schema(implementation = UserProfileResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation failed", content = @Content),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content)
    })
    public ResponseEntity<RestResponse<UserProfileResponse>> create(@RequestBody @Valid UserProfileRequest request){
        return ok(userService.create(request));
    }

}
