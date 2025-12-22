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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.UUID;

@Component
@Slf4j
@RequiredArgsConstructor
public class JwtAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException) throws IOException, ServletException {
        log.error("Erişim reddedildi (Yetki Yetersiz): {}", accessDeniedException.getMessage());

        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);

        final ApiErrorResponse apiErrorResponse = ApiErrorResponse.builder()
                .id(UUID.randomUUID())
                .path(request.getServletPath())
                .message("Access Denied: You do not have permission to access this resource") // 403 Mesajı
                .createTime(LocalDateTime.now())
                .hostName(NetworkUtils.getHostName())
                .build();

        objectMapper.writeValue(response.getOutputStream(), apiErrorResponse);
    }

}
