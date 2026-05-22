package com.edubase.auth.service.concretes;

import com.edubase.auth.configuration.FrontendProperties;
import com.edubase.auth.configuration.PasswordResetProperties;
import com.edubase.auth.entity.PasswordResetToken;
import com.edubase.auth.entity.User;
import com.edubase.auth.entity.UserStatus;
import com.edubase.auth.repository.PasswordResetTokenRepository;
import com.edubase.auth.repository.RefreshTokenRepository;
import com.edubase.auth.repository.UserRepository;
import com.edubase.auth.service.abstracts.GmailMailService;
import com.edubase.auth.service.abstracts.PasswordResetService;
import com.edubase.commonCore.exceptions.BusinessException;
import com.edubase.commonCore.exceptions.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class PasswordResetServiceImpl implements PasswordResetService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Base64.Encoder BASE64_URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final GmailMailService gmailMailService;
    private final PasswordResetProperties passwordResetProperties;
    private final FrontendProperties frontendProperties;

    @Override
    @Transactional
    public void forgotPassword(String email) {
        String normalizedEmail = normalizeEmail(email);
        if (normalizedEmail.isBlank()) {
            return;
        }

        userRepository.findByEmail(normalizedEmail).ifPresent(this::createAndSendResetToken);
    }

    @Override
    @Transactional
    public void resetPassword(String rawToken, String newPassword, String confirmPassword) {
        if (!Objects.equals(newPassword, confirmPassword)) {
            throw new BusinessException(ErrorCode.USER_PASSWORD_MISMATCH);
        }

        String tokenHash = hashToken(rawToken);
        PasswordResetToken token = passwordResetTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_PASSWORD_RESET_TOKEN_INVALID));

        Instant now = Instant.now();
        if (token.getUsedAt() != null) {
            throw new BusinessException(ErrorCode.USER_PASSWORD_RESET_TOKEN_INVALID);
        }

        if (token.getExpiresAt().isBefore(now)) {
            throw new BusinessException(ErrorCode.AUTH_TOKEN_EXPIRED);
        }

        User user = token.getUser();
        if (user.getUserStatus() == UserStatus.DEACTIVATED) {
            throw new BusinessException(ErrorCode.USER_REACTIVATION_REQUIRED_FOR_PASSWORD_RESET);
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        refreshTokenRepository.deleteByUser(user);

        token.setUsedAt(now);
        passwordResetTokenRepository.save(token);
    }

    private void createAndSendResetToken(User user) {
        if (user.getUserStatus() == UserStatus.DEACTIVATED) {
            return;
        }

        Instant now = Instant.now();
        passwordResetTokenRepository.markAllUnusedAsUsedByUser(user, now);

        String rawToken = generateSecureToken();
        PasswordResetToken passwordResetToken = PasswordResetToken.builder()
                .tokenHash(hashToken(rawToken))
                .user(user)
                .expiresAt(now.plus(passwordResetProperties.getTokenExpiration()))
                .createdAt(now)
                .build();
        passwordResetTokenRepository.save(passwordResetToken);

        String resetLink = buildResetLink(rawToken);
        gmailMailService.sendPasswordResetEmail(user.getEmail(), resetLink);
    }

    private String buildResetLink(String rawToken) {
        String baseUrl = frontendProperties.getBaseUrl();
        String normalizedBaseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;

        return UriComponentsBuilder.fromUriString(normalizedBaseUrl)
                .path("/reset-password")
                .queryParam("token", rawToken)
                .build()
                .toUriString();
    }

    private String generateSecureToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return BASE64_URL_ENCODER.encodeToString(bytes);
    }

    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return BASE64_URL_ENCODER.encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm is unavailable.", e);
        }
    }

    private String normalizeEmail(String email) {
        if (email == null) {
            return "";
        }
        return email.trim().toLowerCase();
    }
}
