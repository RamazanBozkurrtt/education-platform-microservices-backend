package com.edubase.auth.service.abstracts;

import com.edubase.auth.dto.AuthenticationResponse;
import com.edubase.auth.dto.RefreshTokenRequest;
import com.edubase.auth.entity.RefreshToken;

public interface RefreshTokenService {

    public RefreshToken saveRefreshToken(RefreshToken refreshToken);

    public AuthenticationResponse refreshToken(RefreshTokenRequest request);


}

