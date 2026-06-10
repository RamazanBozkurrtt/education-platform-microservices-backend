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
import org.springframework.dao.DataIntegrityViolationException;
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
        String providedToken = normalizeToken(request);

        RefreshToken refreshToken = refreshTokenRepository.findByRefreshToken(providedToken)
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_INVALID_SIGNATURE));
        if (refreshToken.isRevoked() || isRefreshTokenExpired(refreshToken.getExpiryDate())) {
            refreshTokenRepository.delete(refreshToken);
            throw new BusinessException(ErrorCode.AUTH_TOKEN_EXPIRED);
        }

        User user = refreshToken.getUser();
        String token = jwtService.generateToken(user);
        RefreshToken newRefreshToken = jwtService.generateRefreshToken(user);

        refreshTokenRepository.delete(refreshToken);
        refreshTokenRepository.flush();
        try {
            refreshTokenRepository.saveAndFlush(newRefreshToken);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(ErrorCode.AUTH_REFRESH_TOKEN_CONFLICT);
        }

        return AuthenticationResponse.forTokens(
                token,
                newRefreshToken.getRefreshToken(),
                user.getId(),
                user.getEmail(),
                user.getRoles().stream().map(role -> role.getName()).sorted().toList()
        );
    }

    private boolean isRefreshTokenExpired(Instant expiryDate) {
        return Instant.now().isAfter(expiryDate);
    }

    private String normalizeToken(RefreshTokenRequest request) {
        if (request == null || request.getRefreshToken() == null) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_SIGNATURE);
        }

        String token = request.getRefreshToken().trim();
        if (token.isEmpty()) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_SIGNATURE);
        }

        if (token.regionMatches(true, 0, "Bearer ", 0, 7)) {
            token = token.substring(7).trim();
        }

        if (token.isEmpty()) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_SIGNATURE);
        }

        return token;
    }
}
