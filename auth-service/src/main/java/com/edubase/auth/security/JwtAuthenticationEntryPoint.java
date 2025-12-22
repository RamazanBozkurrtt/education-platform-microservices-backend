package com.edubase.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.project.bestpractice.exceptions.ApiErrorResponse;
import org.project.bestpractice.utils.NetworkUtils;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.UUID;

@Component
@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException) throws IOException, ServletException {
        log.error("Yetkisiz erişim denemesi: {}", authException.getMessage());

        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);

        final ApiErrorResponse apiErrorResponse = ApiErrorResponse.builder()
                .id(UUID.randomUUID())
                .path(request.getServletPath())
                .message("Access Denied: No valid authentication information was found")
                .createTime(LocalDateTime.now())
                .hostName(NetworkUtils.getHostName())
                .build();

        objectMapper.writeValue(response.getOutputStream(), apiErrorResponse);
    }

}
