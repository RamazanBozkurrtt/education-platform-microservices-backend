package com.edubase.auth.service.concretes;


import com.edubase.auth.configuration.AccountReactivationProperties;
import com.edubase.auth.configuration.mapper.UserMapper;
import com.edubase.auth.dto.AuthenticationRequest;
import com.edubase.auth.dto.AuthenticationResponse;
import com.edubase.auth.dto.RegisterRequest;
import com.edubase.auth.dto.UserResponse;
import com.edubase.auth.entity.AccountReactivationToken;
import com.edubase.auth.entity.Role;
import com.edubase.auth.entity.User;
import com.edubase.auth.entity.UserStatus;
import com.edubase.auth.jwt.JwtService;
import com.edubase.auth.repository.AccountReactivationTokenRepository;
import com.edubase.auth.repository.RefreshTokenRepository;
import com.edubase.auth.repository.RoleRepository;
import com.edubase.auth.repository.UserRepository;
import com.edubase.auth.service.abstracts.AuthenticationService;
import com.edubase.auth.service.abstracts.EmailService;
import com.edubase.auth.service.abstracts.RedisTokenService;
import com.edubase.commonCore.exceptions.BusinessException;
import com.edubase.commonCore.exceptions.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.Duration;
import java.util.Base64;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuthenticationServiceImpl implements AuthenticationService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Base64.Encoder BASE64_URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final int MAX_LOGIN_ATTEMPTS = 10;
    private static final Duration LOGIN_BLOCK_DURATION = Duration.ofMinutes(2);
    private static final String LOGIN_FAIL_PREFIX = "login:fail:";
    private static final String LOGIN_BLOCK_PREFIX = "login:block:";

    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final RefreshTokenRepository  refreshTokenRepository;
    private final AccountReactivationTokenRepository accountReactivationTokenRepository;
    private final UserMapper userMapper;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;
    private final RedisTokenService redisTokenService;
    private final EmailService emailService;
    private final AccountReactivationProperties accountReactivationProperties;
    private final RedisTemplate<String, String> redisTemplate;

    @Transactional
    public UserResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException(ErrorCode.USER_ALREADY_EXISTS);
        }

        Role userRole = roleRepository.findByName(("ROLE_USER"))
                .orElseThrow(()-> new BusinessException(ErrorCode.ROLE_NOT_FOUND));

        var user = User.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .userStatus(UserStatus.ACTUAL)
                .locked(false)
                .roles(new HashSet<>(Set.of(userRole)))
                .build();
        var userDB = userRepository.save(user);
        return userMapper.toResponseFromEntity(userDB);
    }

    public void revokeAllUserRefreshTokens(User user) {
        var validUserTokens = refreshTokenRepository.findAllValidRefreshTokenByUser(user.getId());
        if (validUserTokens.isEmpty())
            return;
        validUserTokens.forEach(token -> {
            token.setRevoked(true);
        });
        refreshTokenRepository.saveAll(validUserTokens);
    }


    //LOGIN
    @Transactional
    public AuthenticationResponse authenticate(AuthenticationRequest request) {
        String email = normalizeEmail(request.email());

        if (isLoginBlocked(email)) {
            throw new BusinessException(ErrorCode.AUTH_TOO_MANY_ATTEMPTS);
        }

        var user = userRepository.findByEmail(email)
                .orElseThrow(() -> {
                    boolean blocked = recordLoginFailure(email);
                    if (blocked) {
                        return new BusinessException(ErrorCode.AUTH_TOO_MANY_ATTEMPTS);
                    }
                    return new BusinessException(ErrorCode.AUTH_LOGIN_FAILED);
                });

        if (user.getUserStatus() == UserStatus.DEACTIVATED) {
            if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
                boolean blocked = recordLoginFailure(email);
                if (blocked) {
                    throw new BusinessException(ErrorCode.AUTH_TOO_MANY_ATTEMPTS);
                }
                throw new BadCredentialsException("Kullanici adi veya sifre hatali");
            }
            String reactivationLink = createReactivationLink(user);
            emailService.sendAccountReactivationEmail(user.getEmail(), reactivationLink);
            clearLoginFailures(email);
            return AuthenticationResponse.forReactivation(reactivationLink);
        }

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            email,
                            request.password()
                    )
            );
        } catch (BadCredentialsException ex) {
            boolean blocked = recordLoginFailure(email);
            if (blocked) {
                throw new BusinessException(ErrorCode.AUTH_TOO_MANY_ATTEMPTS);
            }
            throw new BusinessException(ErrorCode.AUTH_LOGIN_FAILED);
        }

        var token = jwtService.generateToken(user);
        revokeAllUserRefreshTokens(user);
        var refreshToken = jwtService.generateRefreshToken(user);
        refreshTokenRepository.save(refreshToken);
        clearLoginFailures(email);
        return AuthenticationResponse.forTokens(token, refreshToken.getRefreshToken());
    }

    @Override
    @Transactional
    public String reactivateAccount(String token) {
        if (token == null || token.isBlank()) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_SIGNATURE);
        }

        var tokenHash = hashToken(token);
        var reactivationToken = accountReactivationTokenRepository.findByTokenHashAndUsedFalse(tokenHash)
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_INVALID_SIGNATURE));

        if (reactivationToken.getExpiresAt().isBefore(Instant.now())) {
            throw new BusinessException(ErrorCode.AUTH_TOKEN_EXPIRED);
        }

        User user = reactivationToken.getUser();
        user.setUserStatus(UserStatus.ACTUAL);
        userRepository.save(user);

        reactivationToken.setUsed(true);
        reactivationToken.setUsedAt(Instant.now());
        accountReactivationTokenRepository.save(reactivationToken);

        return "Hesabiniz tekrar aktif edildi. Simdi giris yapabilirsiniz.";
    }

    @Override
    @Transactional
    public void logout(String token, String authenticatedEmail) {

        if (authenticatedEmail == null || authenticatedEmail.isBlank()) {
            throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED);
        }

        if (token == null || !token.startsWith("Bearer ")) {
            throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED);
        }

        String accessToken = token.substring(7);
        if (!jwtService.isTokenStructurallyValid(accessToken)) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_SIGNATURE);
        }

        String userEmail = jwtService.extractUsername(accessToken);
        if (userEmail == null || !userEmail.equalsIgnoreCase(authenticatedEmail.trim())) {
            throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED);
        }

        Date expirationDate = jwtService.extractExpiration(accessToken);
        redisTokenService.blacklistToken(accessToken, expirationDate.getTime());
        refreshTokenRepository.deleteByUserEmail(userEmail);
        SecurityContextHolder.clearContext();
    }

    @Override
    @Transactional
    public void deactivate(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if(user.getUserStatus().equals(UserStatus.DEACTIVATED)){
            throw new BusinessException(ErrorCode.USER_ALREADY_DEACTIVATED);
        }

        user.setUserStatus(UserStatus.DEACTIVATED);
        userRepository.save(user);
        refreshTokenRepository.deleteByUser(user);
    }

    private String createReactivationLink(User user) {
        Instant now = Instant.now();
        accountReactivationTokenRepository.deleteByExpiresAtBefore(now);
        accountReactivationTokenRepository.deleteByUser(user);

        String rawToken = generateSecureToken();
        AccountReactivationToken token = AccountReactivationToken.builder()
                .tokenHash(hashToken(rawToken))
                .expiresAt(now.plus(accountReactivationProperties.getTokenExpiration()))
                .used(false)
                .user(user)
                .build();
        accountReactivationTokenRepository.save(token);

        return buildActivationLink(rawToken);
    }

    private String buildActivationLink(String rawToken) {
        String baseUrl = accountReactivationProperties.getActivationBaseUrl();
        String separator = baseUrl.contains("?") ? "&" : "?";
        return baseUrl + separator + "token=" + URLEncoder.encode(rawToken, StandardCharsets.UTF_8);
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
            throw new IllegalStateException("SHA-256 algoritmasi bulunamadi", e);
        }
    }

    private boolean isLoginBlocked(String email) {
        if (!StringUtils.hasText(email)) {
            return false;
        }
        return redisTemplate.hasKey(blockKey(email));
    }

    private boolean recordLoginFailure(String email) {
        if (!StringUtils.hasText(email)) {
            return false;
        }
        String key = failKey(email);
        Long attempts = redisTemplate.opsForValue().increment(key);
        if (attempts != null && attempts == 1L) {
            redisTemplate.expire(key, LOGIN_BLOCK_DURATION);
        }
        if (attempts != null && attempts >= MAX_LOGIN_ATTEMPTS) {
            redisTemplate.opsForValue().set(blockKey(email), "1", LOGIN_BLOCK_DURATION);
            return true;
        }
        return false;
    }

    private void clearLoginFailures(String email) {
        if (!StringUtils.hasText(email)) {
            return;
        }
        redisTemplate.delete(List.of(failKey(email), blockKey(email)));
    }

    private String failKey(String email) {
        return LOGIN_FAIL_PREFIX + email;
    }

    private String blockKey(String email) {
        return LOGIN_BLOCK_PREFIX + email;
    }

    private String normalizeEmail(String email) {
        if (email == null) {
            return "";
        }
        return email.trim().toLowerCase();
    }

    /*
    public Page<User> searchUsers(String name, Boolean active, Pageable pageable) {
    Specification<User> spec = Specification.where(UserSpecification.hasFirstName(name))
                                           .and(UserSpecification.isActive(active))
                                           .and(UserSpecification.isNotDeleted());

    // Hem dinamik filtreleme yapar hem de sayfalama desteği sunar
    return userRepository.findAll(spec, pageable);
    }
     */

}

