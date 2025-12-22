package com.edubase.auth.controller;


import com.edubase.auth.dto.RegisterRequest;
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

    private final AuthenticationServiceImpl authenticationServiceImpl;

    @PostMapping("/register")
    public ResponseEntity<RestResponse<Long>> register(@Valid @RequestBody RegisterRequest registerRequest) {
        return created(authenticationServiceImpl.register(registerRequest));
    }

}
