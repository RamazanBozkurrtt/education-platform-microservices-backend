package com.edubase.auth.service.abstracts;

import com.edubase.auth.dto.AuthenticationResponse;
import com.edubase.auth.dto.RefreshTokenRequest;
import com.edubase.auth.entity.RefreshToken;
import org.project.bestpractice.entities.RefreshToken;
import org.project.bestpractice.payload.AuthenticationResponse;
import org.project.bestpractice.payload.RefreshTokenRequest;

public interface RefreshTokenService {

    public RefreshToken saveRefreshToken(RefreshToken refreshToken);

    public AuthenticationResponse refreshToken(RefreshTokenRequest request);

}

