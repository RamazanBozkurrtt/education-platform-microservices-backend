package com.edubase.auth.jwt;

import com.edubase.auth.configuration.JwtProperties;
import com.edubase.auth.entity.RefreshToken;
import com.edubase.auth.entity.Role;
import com.edubase.auth.entity.User;
import com.edubase.auth.security.UserPrincipal;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Collection;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
public class JwtService {

    private static final String USER_ID_CLAIM = "user_id";

    private final JwtProperties jwtProperties;
    private volatile SecretKey signInKey;


    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    public String generateToken(User user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("roles", user.getRoles().stream().map(Role::getName).toList());
        claims.put(USER_ID_CLAIM, user.getId());
        var authorities = user.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority(role.getName()))
                .toList();
        return buildToken(claims, new UserPrincipal(user, authorities), UUID.randomUUID().toString(), jwtProperties.getExpiration().toMillis());
    }

    public String generateToken(
            Map<String, Object> extraClaims,
            UserDetails userDetails
    ) {
        String tokenId = UUID.randomUUID().toString();
        Map<String, Object> claims = new HashMap<>();
        if (extraClaims != null) {
            claims.putAll(extraClaims);
        }
        if (userDetails instanceof UserPrincipal principal && principal.user() != null && principal.user().getId() != null) {
            claims.putIfAbsent(USER_ID_CLAIM, principal.user().getId());
        }
        return buildToken(claims, userDetails, tokenId, jwtProperties.getExpiration().toMillis());
    }

    private String buildToken(
            Map<String, Object> extraClaims,
            UserDetails userDetails,
            String tokenId,
            long expiration
    ) {
        Map<String, Object> claims = new HashMap<>();
        if (extraClaims != null) {
            claims.putAll(extraClaims);
        }
        if (jwtProperties.getIssuer() != null && !jwtProperties.getIssuer().isBlank()) {
            claims.put("iss", jwtProperties.getIssuer());
        }
        if (jwtProperties.getAudience() != null && !jwtProperties.getAudience().isBlank()) {
            claims.put("aud", java.util.List.of(jwtProperties.getAudience()));
        }
        if (userDetails != null && userDetails.getUsername() != null && !userDetails.getUsername().isBlank()) {
            claims.putIfAbsent("username", userDetails.getUsername());
        }

        Instant now = Instant.now();
        var builder = Jwts
                .builder()
                .claims(claims)
                .subject(userDetails.getUsername())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(expiration)))
                .signWith(getSignInKey(), Jwts.SIG.HS256);

        if (tokenId != null && !tokenId.isBlank()) {
            builder.id(tokenId);
        }

        return builder.compact();
    }


    public RefreshToken generateRefreshToken(User user) {
        var authorities = user.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority(role.getName()))
                .toList();
        Map<String, Object> extraClaims = new HashMap<>();
        String refreshTokenId = UUID.randomUUID().toString();
        String token = buildToken(
                extraClaims,
                new UserPrincipal(user, authorities),
                refreshTokenId,
                jwtProperties.getRefreshTokenExpiration().toMillis()
        );
        return RefreshToken.builder()
                .refreshToken(token)
                .revoked(false)
                .user(user)
                .expiryDate(Instant.now().plusMillis(jwtProperties.getRefreshTokenExpiration().toMillis()))
                .build();
    }


    public boolean isTokenValid(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return (username.equals(userDetails.getUsername())) && !isTokenExpired(token);
    }

    public boolean isTokenStructurallyValid(String token) {
        try {
            extractUsername(token);
            return !isTokenExpired(token);
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    //+
    private Claims extractAllClaims(String token) {
        Claims claims = Jwts
                .parser()
                .verifyWith(getSignInKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();

        validateIssuerAndAudience(claims);
        return claims;
    }

    private void validateIssuerAndAudience(Claims claims) {
        String expectedIssuer = jwtProperties.getIssuer();
        if (expectedIssuer != null && !expectedIssuer.isBlank()) {
            String actualIssuer = claims.getIssuer();
            if (!expectedIssuer.equals(actualIssuer)) {
                throw new JwtException("Invalid issuer");
            }
        }

        String expectedAudience = jwtProperties.getAudience();
        if (expectedAudience == null || expectedAudience.isBlank()) {
            return;
        }

        Object audienceClaim = claims.get("aud");
        if (audienceClaim instanceof String value) {
            if (!expectedAudience.equals(value)) {
                throw new JwtException("Invalid audience");
            }
            return;
        }

        if (audienceClaim instanceof Collection<?> values) {
            boolean match = values.stream()
                    .anyMatch(item -> expectedAudience.equals(String.valueOf(item)));
            if (!match) {
                throw new JwtException("Invalid audience");
            }
            return;
        }

        throw new JwtException("Invalid audience");
    }

    private SecretKey getSignInKey() {
        SecretKey cached = signInKey;
        if (cached != null) {
            return cached;
        }

        synchronized (this) {
            if (signInKey != null) {
                return signInKey;
            }
            signInKey = createSignInKey();
            return signInKey;
        }
    }

    private SecretKey createSignInKey() {
        String secret = jwtProperties.getSecret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("JWT secret is missing.");
        }

        String trimmed = secret.trim();
        byte[] rawBytes = trimmed.getBytes(StandardCharsets.UTF_8);

        try {
            byte[] decoded = Base64.getDecoder().decode(trimmed);
            if (decoded.length >= 32) {
                return Keys.hmacShaKeyFor(decoded);
            }
        } catch (IllegalArgumentException ignored) {
        }

        try {
            byte[] decodedUrl = Base64.getUrlDecoder().decode(trimmed);
            if (decodedUrl.length >= 32) {
                return Keys.hmacShaKeyFor(decodedUrl);
            }
        } catch (IllegalArgumentException ignored) {
        }

        if (rawBytes.length < 32) {
            throw new IllegalStateException("JWT secret must be at least 32 bytes (or base64/base64url value of 32+ bytes).");
        }

        return Keys.hmacShaKeyFor(rawBytes);
    }

}
