package com.edubase.auth.jwt;

import com.edubase.auth.configuration.JwtProperties;
import com.edubase.auth.entity.Role;
import com.edubase.auth.entity.User;
import com.edubase.commonCore.security.JwtSecretKeyProvider;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceCompatibilityTest {

    private static final String ISSUER = "edubase-auth";
    private static final String AUDIENCE = "api-gateway";

    @ParameterizedTest
    @MethodSource("secrets")
    void generatedAccessTokenIsAcceptedByGatewayStyleDecoder(String secret) {
        JwtService jwtService = new JwtService(jwtProperties(secret));
        User user = user();

        String token = jwtService.generateToken(user);

        Jwt decoded = gatewayStyleDecoder(secret).decode(token);
        assertThat(decoded.getSubject()).isEqualTo(user.getEmail());
        assertThat(decoded.getClaimAsString("username")).isEqualTo(user.getEmail());
        assertThat(decoded.getClaimAsString("iss")).isEqualTo(ISSUER);
        assertThat(decoded.getAudience()).contains(AUDIENCE);
        assertThat(decoded.getClaimAsStringList("roles")).containsExactly("ROLE_USER");
        assertThat(decoded.getClaim("user_id").toString()).isEqualTo(user.getId().toString());
        assertThat(decoded.getIssuedAt()).isNotNull();
        assertThat(decoded.getExpiresAt()).isNotNull();
        assertThat(decoded.getId()).isNotBlank();
    }

    private static Stream<String> secrets() {
        String base64Secret = Base64.getEncoder()
                .encodeToString("01234567890123456789012345678901".getBytes(StandardCharsets.UTF_8));
        return Stream.of(
                "plain-jwt-secret-that-is-long-enough-32",
                base64Secret
        );
    }

    private JwtDecoder gatewayStyleDecoder(String secret) {
        return NimbusJwtDecoder.withSecretKey(JwtSecretKeyProvider.hmacSha256Key(secret))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    private JwtProperties jwtProperties(String secret) {
        JwtProperties properties = new JwtProperties();
        properties.setSecret(secret);
        properties.setIssuer(ISSUER);
        properties.setAudience(AUDIENCE);
        properties.setExpiration(Duration.ofHours(2));
        properties.setRefreshTokenExpiration(Duration.ofHours(4));
        return properties;
    }

    private User user() {
        Role role = Role.builder()
                .name("ROLE_USER")
                .build();

        User user = User.builder()
                .email("student@example.com")
                .passwordHash("password")
                .roles(Set.of(role))
                .build();
        user.setId(123L);
        return user;
    }
}
