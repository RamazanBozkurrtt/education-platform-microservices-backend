package com.edubase.gateway.config;

import com.edubase.commonCore.security.JwtSecretKeyProvider;
import com.edubase.gateway.security.JwtAccessDeniedHandler;
import com.edubase.gateway.security.JwtAuthenticationEntryPoint;
import com.edubase.gateway.service.abstracts.RedisTokenService;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigJwtDecoderTest {

    private static final String ISSUER = "edubase-auth";
    private static final String AUDIENCE = "api-gateway";

    @ParameterizedTest
    @MethodSource("secrets")
    void reactiveJwtDecoderAcceptsAuthServiceCompatibleToken(String secret) {
        String token = createAccessToken(secret);
        ReactiveJwtDecoder decoder = securityConfig().reactiveJwtDecoder(secret, ISSUER, AUDIENCE);

        var jwt = decoder.decode(token).block();

        assertThat(jwt).isNotNull();
        assertThat(jwt.getSubject()).isEqualTo("student@example.com");
        assertThat(jwt.getClaimAsString("username")).isEqualTo("student@example.com");
        assertThat(jwt.getClaim("user_id").toString()).isEqualTo("123");
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("ROLE_USER");
        assertThat(jwt.getClaimAsString("iss")).isEqualTo(ISSUER);
        assertThat(jwt.getAudience()).contains(AUDIENCE);
        assertThat(jwt.getId()).isEqualTo("test-jti");
    }

    private static Stream<String> secrets() {
        String base64Secret = Base64.getEncoder()
                .encodeToString("01234567890123456789012345678901".getBytes(StandardCharsets.UTF_8));
        return Stream.of(
                "plain-jwt-secret-that-is-long-enough-32",
                base64Secret
        );
    }

    private SecurityConfig securityConfig() {
        RedisTokenService redisTokenService = new RedisTokenService() {
            @Override
            public Mono<Void> blacklistToken(String tokenId, long expiresAtMillis) {
                return Mono.empty();
            }

            @Override
            public Mono<Boolean> isTokenBlacklisted(String tokenId) {
                return Mono.just(false);
            }
        };

        return new SecurityConfig(
                new JwtAuthenticationEntryPoint(),
                new JwtAccessDeniedHandler(),
                redisTokenService
        );
    }

    private String createAccessToken(String secret) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .audience(List.of(AUDIENCE))
                .subject("student@example.com")
                .claim("username", "student@example.com")
                .claim("user_id", 123L)
                .claim("roles", List.of("ROLE_USER"))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600))
                .id("test-jti")
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();

        NimbusJwtEncoder encoder = new NimbusJwtEncoder(
                new ImmutableSecret<>(JwtSecretKeyProvider.hmacSha256Key(secret))
        );
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
