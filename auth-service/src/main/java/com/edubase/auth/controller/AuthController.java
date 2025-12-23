package com.edubase.auth.controller;


import com.edubase.auth.dto.*;
import com.edubase.auth.service.abstracts.AuthenticationService;
import com.edubase.auth.service.abstracts.RefreshTokenService;
import com.edubase.auth.service.concretes.AuthenticationServiceImpl;
import com.edubase.common.controller.RestBaseController;
import com.edubase.common.utils.RestResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController extends RestBaseController {

    private final AuthenticationService authenticationServiceImpl;
    private final RefreshTokenService refreshTokenService;

    @PostMapping("/register")
    public ResponseEntity<RestResponse<UserResponse>> register(@Valid @RequestBody RegisterRequest registerRequest) {
        return created(authenticationServiceImpl.register(registerRequest));
    }

    @PostMapping("/authenticate")
    public ResponseEntity<RestResponse<AuthenticationResponse>> authenticate(@Valid @RequestBody AuthenticationRequest authenticationRequest) {
        return ok(authenticationServiceImpl.authenticate(authenticationRequest));
    }

    @PostMapping("/refresh-token")
    public ResponseEntity<RestResponse<AuthenticationResponse>> refreshToken(@RequestBody @Valid RefreshTokenRequest request) {
        return ok(refreshTokenService.refreshToken(request));
    }

}
