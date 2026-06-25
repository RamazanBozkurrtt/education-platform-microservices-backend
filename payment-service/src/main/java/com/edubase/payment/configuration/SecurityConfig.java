package com.edubase.payment.configuration;

import com.edubase.commonCore.security.JwtSecretKeyProvider;
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
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.SecretKey;
import java.util.ArrayList;
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
        SecretKey key = JwtSecretKeyProvider.hmacSha256Key(secret);
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();

        return token -> {
            try {
                Jwt jwt = decoder.decode(token);
                validateIssuerAndAudience(jwt, issuer, audience);
                return jwt;
            } catch (JwtException ex) {
                log.debug("JWT validation failed: {}", jwtFailureReason(ex));
                throw ex;
            }
        };
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

    private String jwtFailureReason(Throwable ex) {
        String message = ex.getMessage();
        if (message == null) {
            return ex.getClass().getSimpleName();
        }
        String normalized = message.toLowerCase(Locale.ROOT);
        if (normalized.contains("expired")) {
            return "expired token";
        }
        if (normalized.contains("issuer")) {
            return "invalid issuer";
        }
        if (normalized.contains("audience")) {
            return "invalid audience";
        }
        if (normalized.contains("signature") || normalized.contains("mac")) {
            return "invalid signature";
        }
        return message;
    }
}
