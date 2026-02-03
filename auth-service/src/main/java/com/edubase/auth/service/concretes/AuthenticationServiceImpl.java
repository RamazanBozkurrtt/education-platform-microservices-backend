package com.edubase.auth.service.concretes;


import com.edubase.auth.configuration.mapper.UserMapper;
import com.edubase.auth.dto.AuthenticationRequest;
import com.edubase.auth.dto.AuthenticationResponse;
import com.edubase.auth.dto.RegisterRequest;
import com.edubase.auth.dto.UserResponse;
import com.edubase.auth.entity.Role;
import com.edubase.auth.entity.User;
import com.edubase.auth.jwt.JwtService;
import com.edubase.auth.repository.RefreshTokenRepository;
import com.edubase.auth.repository.RoleRepository;
import com.edubase.auth.repository.UserRepository;
import com.edubase.auth.service.abstracts.AuthenticationService;
import com.edubase.auth.service.abstracts.RedisTokenService;
import com.edubase.common.exceptions.BusinessException;
import com.edubase.common.handling.ErrorCode;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.HashSet;
import java.util.Set;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuthenticationServiceImpl implements AuthenticationService {

    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final RefreshTokenRepository  refreshTokenRepository;
    private final UserMapper userMapper;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;
    private final RedisTokenService redisTokenService;

    @Transactional
    public UserResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())){
            throw new BusinessException(ErrorCode.USER_ALREADY_EXISTS);
        }

        Role userRole = roleRepository.findByName(("ROLE_USER"))
                .orElseThrow(()-> new BusinessException(ErrorCode.ROLE_NOT_FOUND));

        var user = User.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .isActive(true)
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

    @Transactional
    public AuthenticationResponse authenticate(AuthenticationRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.email(),
                        request.password()
                )
        );
        var user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new UsernameNotFoundException(request.email()));

        var token = jwtService.generateToken(user);
        revokeAllUserRefreshTokens(user);
        var refreshToken = jwtService.generateRefreshToken(user);
        refreshTokenRepository.save(refreshToken);
        return new AuthenticationResponse(token,refreshToken.getRefreshToken());
    }

    @Override
    @Transactional
    public void logout(String token) {
        if (token == null || !token.startsWith("Bearer ")) {
            return;
        }
        try{
            String accessToken = token.substring(7);
            String userEmail = jwtService.extractUsername(accessToken);
            Date expirationDate = jwtService.extractExpiration(accessToken);

            redisTokenService.blacklistToken(accessToken, expirationDate.getTime());

            refreshTokenRepository.deleteByUserEmail(userEmail);

            SecurityContextHolder.clearContext();
        }catch (Exception e){
            log.error("Logout sırasında hata: {}", e.getMessage());
        }finally {
            SecurityContextHolder.clearContext();
        }
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
