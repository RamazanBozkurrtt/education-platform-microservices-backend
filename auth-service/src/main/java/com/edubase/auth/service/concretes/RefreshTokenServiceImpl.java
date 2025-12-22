package com.edubase.auth.service.concretes;

import com.edubase.auth.dto.AuthenticationResponse;
import com.edubase.auth.dto.RefreshTokenRequest;
import com.edubase.auth.entity.RefreshToken;
import com.edubase.auth.jwt.JwtService;
import com.edubase.auth.repository.RefreshTokenRepository;
import com.edubase.auth.service.abstracts.RefreshTokenService;
import com.edubase.common.exceptions.BusinessException;
import com.edubase.common.handling.ErrorCode;
import io.jsonwebtoken.security.SignatureException;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class RefreshTokenServiceImpl implements RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;

    public RefreshTokenServiceImpl(RefreshTokenRepository refreshTokenRepository, JwtService jwtService) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtService = jwtService;
    }

    @Override
    public RefreshToken saveRefreshToken(RefreshToken refreshToken) {
        return refreshTokenRepository.save(refreshToken);
    }

    @Override
    @Transactional
    public AuthenticationResponse refreshToken(RefreshTokenRequest request) {
        RefreshToken refreshToken = refreshTokenRepository.findByRefreshToken(request.getRefreshToken())
                .orElseThrow(() -> new SignatureException(ErrorCode.AUTH_INVALID_SIGNATURE.getMessage()));
        if(isRefreshTokenExpired(refreshToken.getExpiryDate())){
            refreshTokenRepository.delete(refreshToken);
            throw new BusinessException(ErrorCode.AUTH_TOKEN_EXPIRED);
        }
        if (refreshToken.isRevoked()) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_SIGNATURE);
        }
        String token = jwtService.generateToken(refreshToken.getUser());
        RefreshToken refreshTokenDB = saveRefreshToken(jwtService.generateRefreshToken(refreshToken.getUser()));
        refreshTokenRepository.delete(refreshToken);
        return new AuthenticationResponse(token,refreshTokenDB.getRefreshToken());
    }

    private boolean isRefreshTokenExpired(Instant expiryDate) {
        return Instant.now().isAfter(expiryDate);
    }

}
