package com.edubase.auth.controller;


import com.edubase.auth.controller.base.RestBaseController;
import com.edubase.auth.dto.*;
import com.edubase.auth.security.UserPrincipal;
import com.edubase.auth.service.abstracts.AuthenticationService;
import com.edubase.auth.service.abstracts.PasswordResetService;
import com.edubase.auth.service.abstracts.RefreshTokenService;
import com.edubase.commonCore.exceptions.BusinessException;
import com.edubase.commonCore.exceptions.ErrorCode;
import com.edubase.commonCore.utils.RestResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController extends RestBaseController {

    private final AuthenticationService authenticationService;
    private final RefreshTokenService refreshTokenService;
    private final PasswordResetService passwordResetService;

    @PostMapping("/register")
    @Operation(summary = "User Register", description = "Creates a new user account.")
    public ResponseEntity<RestResponse<UserResponse>> register(@Valid @RequestBody RegisterRequest registerRequest) {
        return created(authenticationService.register(registerRequest));
    }

    @Operation(summary = "Login", description = "Returns Access and Refresh tokens.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Başarılı Giriş"),
            @ApiResponse(responseCode = "401", description = "Hatalı Kimlik Bilgileri")
    })
    @PostMapping("/login")
    public ResponseEntity<RestResponse<AuthenticationResponse>> authenticate(@Valid @RequestBody AuthenticationRequest authenticationRequest) {
        return ok(authenticationService.authenticate(authenticationRequest));
    }

    @PostMapping("/refresh-token")
    @Operation(summary = "Refresh Token", description = "Generates a new Access Token using a Refresh Token.")
    public ResponseEntity<RestResponse<AuthenticationResponse>> refreshToken(@RequestBody @Valid RefreshTokenRequest request) {
        return ok(refreshTokenService.refreshToken(request));
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Forgot password", description = "Sends password reset email when account exists.")
    public ResponseEntity<RestResponse<MessageResponse>> forgotPassword(@RequestBody @Valid ForgotPasswordRequest request) {
        passwordResetService.forgotPassword(request.email());
        return ok(new MessageResponse("If an account exists with this email, a password reset link has been sent."));
    }

    @PostMapping("/reactivate-account/request")
    @Operation(summary = "Request account reactivation", description = "Sends account reactivation email when account exists and is eligible.")
    public ResponseEntity<RestResponse<MessageResponse>> requestReactivation(@RequestBody @Valid ReactivateAccountRequest request) {
        authenticationService.requestAccountReactivation(request.email());
        return ok(new MessageResponse("If an account exists and is eligible for reactivation, a reactivation link has been sent."));
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Reset password", description = "Resets password using one-time reset token.")
    public ResponseEntity<RestResponse<MessageResponse>> resetPassword(@RequestBody @Valid ResetPasswordRequest request) {
        passwordResetService.resetPassword(request.token(), request.newPassword(), request.confirmPassword());
        return ok(new MessageResponse("Password has been reset successfully."));
    }

    @GetMapping("/reset-password/validate")
    @Operation(summary = "Validate password reset token", description = "Validates a one-time reset token without changing the password.")
    public ResponseEntity<RestResponse<MessageResponse>> validateResetPasswordToken(@RequestParam("token") String token) {
        passwordResetService.validateResetToken(token);
        return ok(new MessageResponse("Password reset token is valid."));
    }

    @GetMapping("/reactivate-account")
    @Operation(summary = "Reactivate account", description = "Reactivates account using one-time token.")
    public ResponseEntity<RestResponse<String>> reactivateAccountGet(
            @RequestParam Map<String, String> params
    ) {
        return ok(authenticationService.reactivateAccount(extractReactivationToken(params, null)));
    }

    @PostMapping(value = "/reactivate-account", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Reactivate account (JSON)", description = "Reactivates account using one-time token from JSON body or query parameter.")
    public ResponseEntity<RestResponse<String>> reactivateAccountPostJson(
            @RequestParam Map<String, String> params,
            @RequestBody(required = false) Map<String, Object> body
    ) {
        return ok(authenticationService.reactivateAccount(extractReactivationToken(params, body)));
    }

    @PostMapping(value = "/reactivate-account", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    @Operation(summary = "Reactivate account (Form)", description = "Reactivates account using one-time token from form fields or query parameter.")
    public ResponseEntity<RestResponse<String>> reactivateAccountPostForm(
            @RequestParam Map<String, String> params
    ) {
        return ok(authenticationService.reactivateAccount(extractReactivationToken(params, null)));
    }

    @PostMapping(value = "/reactivate-account")
    @Operation(summary = "Reactivate account (Fallback)", description = "Reactivates account using one-time token from query parameter.")
    public ResponseEntity<RestResponse<String>> reactivateAccountPostFallback(
            @RequestParam Map<String, String> params
    ) {
        return ok(authenticationService.reactivateAccount(extractReactivationToken(params, null)));
    }

    private String extractReactivationToken(Map<String, String> params, Map<String, Object> body) {
        Map<String, String> candidates = new LinkedHashMap<>();
        if (params != null) {
            candidates.putAll(params);
        }
        if (body != null) {
            putIfPresent(candidates, "token", body.get("token"));
            putIfPresent(candidates, "reactivationToken", body.get("reactivationToken"));
            putIfPresent(candidates, "reactivation_token", body.get("reactivation_token"));
            putIfPresent(candidates, "value", body.get("value"));
        }

        String token = firstNonBlank(
                candidates.get("token"),
                candidates.get("reactivationToken"),
                candidates.get("reactivation_token"),
                candidates.get("value")
        );

        if (token != null) {
            return token;
        }

        if (!candidates.isEmpty()) {
            return candidates.values().stream()
                    .filter(value -> value != null && !value.isBlank())
                    .findFirst()
                    .orElse(null);
        }

        return null;
    }

    private void putIfPresent(Map<String, String> target, String key, Object rawValue) {
        if (rawValue == null) {
            return;
        }
        target.put(key, String.valueOf(rawValue));
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    @PostMapping("/logout")
    @Operation(summary = "Log out",description = "Log out and blacklist AccesToken and RefreshToken.")
    public ResponseEntity<RestResponse<String>> logout(@RequestHeader("Authorization") String authHeader,
                                                       @AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) {
            throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED);
        }
        authenticationService.logout(authHeader, principal.getUsername());
        return ok("success");
    }

    @DeleteMapping("/me")
    public ResponseEntity<?> deactivateUser(@RequestHeader("Authorization") String authHeader,
                                            @AuthenticationPrincipal UserPrincipal principal) {

        if (principal == null) {
            throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED);
        }

        String email = principal.getUsername();

        authenticationService.deactivate(email, authHeader);

        return noContent();
    }

    @PutMapping("/change-password")
    public ResponseEntity<RestResponse<String>> changePassword(@Valid @RequestBody ChangePasswordRequest request,
                                                               @AuthenticationPrincipal UserPrincipal principal) {

        if (principal == null) {
            throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED);
        }

        String email = principal.getUsername();
        authenticationService.changePassword(request.oldPassword(), request.newPassword(), email);

        return ok("Sifre basariyla degistirildi.");
    }

    @PostMapping("/me/roles/instructor")
    @Operation(summary = "Become instructor", description = "Adds ROLE_INSTRUCTOR to current authenticated user and returns refreshed tokens.")
    public ResponseEntity<RestResponse<AuthenticationResponse>> becomeInstructor(
            @AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) {
            throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED);
        }
        return ok(authenticationService.grantInstructorRoleForCurrentUser(principal.getUsername()));
    }

}
