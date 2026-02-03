package com.edubase.auth.controller;


import com.edubase.auth.dto.*;
import com.edubase.auth.service.abstracts.AuthenticationService;
import com.edubase.auth.service.abstracts.RefreshTokenService;
import com.edubase.common.controller.RestBaseController;
import com.edubase.common.utils.RestResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController extends RestBaseController {

    private final AuthenticationService authenticationService;
    private final RefreshTokenService refreshTokenService;

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

    @PostMapping("/logout")
    @Operation(summary = "Log out",description = "Log out and blacklist AccesToken and RefreshToken.")
    public ResponseEntity<RestResponse<String>> logout(@RequestHeader("Authorization") String authHeader) {
        authenticationService.logout(authHeader);
        return ok("success");
    }

}
