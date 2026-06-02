package com.edubase.gateway.config;

import com.edubase.gateway.security.JwtAccessDeniedHandler;
import com.edubase.gateway.security.JwtAuthenticationEntryPoint;
import com.edubase.gateway.service.abstracts.RedisTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Configuration
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
@RequiredArgsConstructor
@Slf4j
public class SecurityConfig {

    private final JwtAuthenticationEntryPoint authenticationEntryPoint;
    private final JwtAccessDeniedHandler accessDeniedHandler;
    private final RedisTokenService redisTokenService;

    @Value("${auth.debug:false}")
    private boolean authDebug;

    private static final String[] SWAGGER_PATHS = {
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui",
            "/swagger-ui/index.html",
            "/swagger-ui.html",
            "/webjars/**",
            "/swagger-resources/**"
    };

    private static final String[] PUBLIC_PATHS = {
            "/api/v1/auth/register",
            "/api/v1/auth/login",
            "/api/v1/auth/refresh-token",
            "/api/v1/auth/forgot-password",
            "/api/v1/auth/reset-password",
            "/api/v1/auth/reset-password/**",
            "/api/v1/auth/password-reset/**",
            "/api/v1/auth/reactivate-account",
            "/api/v1/auth/reactivate-account/request",
            "/api/v1/users/public/**",
            "/courses/public/**",
            "/api/v1/courses/public/**",
            "/api/v1/reviews/courses/**",
            "/actuator/health",
            "/actuator/info",
            "/favicon.ico",
            "/error"
    };

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public SecurityWebFilterChain swaggerWebFilterChain(ServerHttpSecurity http) {
        return http
                .securityMatcher(ServerWebExchangeMatchers.pathMatchers(SWAGGER_PATHS))
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .cors(Customizer.withDefaults())
                .authorizeExchange(exchanges -> exchanges.anyExchange().permitAll())
                .build();
    }

    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE)
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .cors(Customizer.withDefaults())
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers(PUBLIC_PATHS).permitAll()
                        .pathMatchers(HttpMethod.OPTIONS).permitAll()
                        .pathMatchers("/api/v1/users/**").hasAnyRole("USER", "ADMIN")
                        .pathMatchers("/api/v1/instructors/**").hasAnyRole("USER", "INSTRUCTOR", "ADMIN")
                        .anyExchange().authenticated()
                )
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                )
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));

        return http.build();
    }

    @Bean
    public ReactiveJwtDecoder reactiveJwtDecoder(@Value("${jwt.secret}") String secret,
                                                 @Value("${jwt.issuer}") String issuer,
                                                 @Value("${jwt.audience}") String audience) {
        SecretKey key = buildSecretKey(secret);
        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();

        return token -> decoder.decode(token)
                .flatMap(jwt -> {
                    validateIssuerAndAudience(jwt, issuer, audience);
                    return redisTokenService.isTokenBlacklisted(jwt.getId())
                            .flatMap(blacklisted -> {
                                if (blacklisted) {
                                    return Mono.error(new BadJwtException("Token is blacklisted"));
                                }
                                return Mono.just(jwt);
                            });
                });
    }

    @Bean
    public Converter<Jwt, Mono<AbstractAuthenticationToken>> jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            List<String> roles = jwt.getClaimAsStringList("roles");
            if (roles == null) {
                return List.of();
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

        return new ReactiveJwtAuthenticationConverterAdapter(converter);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${cors.allowed-origins:}") String allowedOrigins,
            @Value("${cors.allowed-methods:}") String allowedMethods,
            @Value("${cors.allowed-headers:}") String allowedHeaders,
            @Value("${cors.exposed-headers:}") String exposedHeaders,
            @Value("${cors.allow-credentials}") boolean allowCredentials
    ) {
        List<String> originList = splitCsv(allowedOrigins);
        List<String> methodList = splitCsv(allowedMethods);
        List<String> headerList = splitCsv(allowedHeaders);
        List<String> exposedHeaderList = splitCsv(exposedHeaders);

        CorsConfiguration configuration = new CorsConfiguration();
        if (!originList.isEmpty()) {
            configuration.setAllowedOrigins(originList);
        }
        if (!methodList.isEmpty()) {
            configuration.setAllowedMethods(methodList);
        }
        if (!headerList.isEmpty()) {
            configuration.setAllowedHeaders(headerList);
        }
        if (!exposedHeaderList.isEmpty()) {
            configuration.setExposedHeaders(exposedHeaderList);
        }
        configuration.setAllowCredentials(allowCredentials);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    private List<String> splitCsv(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .collect(Collectors.toList());
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
