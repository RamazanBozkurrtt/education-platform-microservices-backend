package com.edubase.auth.service.concretes;

import com.edubase.auth.dto.AuthenticationResponse;
import com.edubase.auth.dto.RefreshTokenRequest;
import com.edubase.auth.entity.RefreshToken;
import com.edubase.auth.entity.User;
import com.edubase.auth.jwt.JwtService;
import com.edubase.auth.repository.RefreshTokenRepository;
import com.edubase.auth.service.abstracts.RefreshTokenService;
import com.edubase.commonCore.exceptions.BusinessException;
import com.edubase.commonCore.exceptions.ErrorCode;
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
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_INVALID_SIGNATURE));
        if (refreshToken.isRevoked() || isRefreshTokenExpired(refreshToken.getExpiryDate())) {
            refreshTokenRepository.delete(refreshToken);
            throw new BusinessException(ErrorCode.AUTH_TOKEN_EXPIRED);
        }

        User user = refreshToken.getUser();
        String token = jwtService.generateToken(user);
        RefreshToken newRefreshToken = jwtService.generateRefreshToken(user);

        refreshTokenRepository.delete(refreshToken);
        refreshTokenRepository.save(newRefreshToken);

        return new AuthenticationResponse(token, newRefreshToken.getRefreshToken());
    }

    private boolean isRefreshTokenExpired(Instant expiryDate) {
        return Instant.now().isAfter(expiryDate);
    }
}
