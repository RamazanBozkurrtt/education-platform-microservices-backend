package com.edubase.payment.configuration;

import com.edubase.payment.security.JwtAccessDeniedHandler;
import com.edubase.payment.security.JwtAuthenticationEntryPoint;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
@Slf4j
public class SecurityConfig {

    private final JwtAuthenticationEntryPoint authenticationEntryPoint;
    private final JwtAccessDeniedHandler accessDeniedHandler;

    @Value("${auth.debug:false}")
    private boolean authDebug;

    private static final String[] PUBLIC_URLS = {
            "/error",
            "/favicon.ico",
            "/actuator/health",
            "/actuator/info"
    };

    private static final String[] SWAGGER_URLS = {
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_URLS).permitAll()
                        .requestMatchers(SWAGGER_URLS).permitAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .oauth2ResourceServer(oauth2 ->
                        oauth2.jwt(jwt ->
                                jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())
                        )
                );

        return http.build();
    }

    @Bean
    public Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();

        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            List<String> roles = new ArrayList<>();
            List<String> roleList = jwt.getClaimAsStringList("roles");
            if (roleList != null) {
                roles.addAll(roleList);
            }

            String singleRole = jwt.getClaimAsString("role");
            if (singleRole != null && !singleRole.isBlank()) {
                roles.add(singleRole);
            }

            List<GrantedAuthority> authorities = roles.stream()
                    .map(role -> role == null ? "" : role.trim().toUpperCase(Locale.ROOT))
                    .filter(role -> !role.isBlank())
                    .map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role)
                    .map(SimpleGrantedAuthority::new)
                    .collect(Collectors.toList());

            if (authDebug) {
                log.info("JWT subject={} roles={} authorities={}", jwt.getSubject(), roles, authorities);
            }

            return authorities;
        });

        return converter;
    }

    @Bean
    public JwtDecoder jwtDecoder(@Value("${jwt.secret}") String secret,
                                 @Value("${jwt.issuer}") String issuer,
                                 @Value("${jwt.audience}") String audience) {
        SecretKey key = buildSecretKey(secret);
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();

        return token -> {
            Jwt jwt = decoder.decode(token);
            validateIssuerAndAudience(jwt, issuer, audience);
            return jwt;
        };
    }

    private SecretKey buildSecretKey(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("jwt.secret is missing. Set JWT_SECRET environment variable.");
        }

        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(secret.trim());
        } catch (IllegalArgumentException ex) {
            keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        }

        if (keyBytes.length < 32) {
            throw new IllegalStateException("jwt.secret must be at least 32 bytes (or base64 value of 32+ bytes).");
        }

        return new SecretKeySpec(keyBytes, "HmacSHA256");
    }

    private void validateIssuerAndAudience(Jwt jwt, String issuer, String audience) {
        if (issuer != null && !issuer.isBlank()) {
            String actualIssuer = jwt.getClaimAsString("iss");
            if (!issuer.equals(actualIssuer)) {
                throw new BadJwtException("Invalid issuer");
            }
        }

        if (audience != null && !audience.isBlank()) {
            List<String> audiences = jwt.getAudience();
            if (audiences == null || audiences.stream().noneMatch(audience::equals)) {
                throw new BadJwtException("Invalid audience");
            }
        }
    }
}
