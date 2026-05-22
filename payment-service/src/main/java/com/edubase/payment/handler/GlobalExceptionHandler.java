package com.edubase.payment.handler;

import com.edubase.commonCore.exceptions.BusinessException;
import com.edubase.commonCore.exceptions.ErrorCode;
import com.edubase.commonCore.utils.RestResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static HttpStatus statusOf(ErrorCode errorCode) {
        try {
            return HttpStatus.valueOf(errorCode.getHttpStatus());
        } catch (Exception ignore) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
    }

    private static String requestMeta(HttpServletRequest request) {
        String query = request.getQueryString();
        String path = request.getRequestURI();
        String method = request.getMethod();
        String remote = request.getRemoteAddr();
        String userAgent = request.getHeader("User-Agent");

        StringBuilder meta = new StringBuilder(128);
        meta.append("method=").append(method);
        meta.append(" path=").append(path);
        if (query != null && !query.isBlank()) {
            meta.append(" query=").append(query);
        }
        if (remote != null && !remote.isBlank()) {
            meta.append(" remote=").append(remote);
        }
        if (userAgent != null && !userAgent.isBlank()) {
            meta.append(" ua=").append(userAgent);
        }
        return meta.toString();
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<RestResponse<Object>> handleBusinessException(BusinessException ex, HttpServletRequest request) {
        ErrorCode errorCode = ex.getErrorCode();
        HttpStatus status = statusOf(errorCode);

        log.warn("BUSINESS_EXCEPTION | status={} code={} {} msg={}",
                status, errorCode.getCode(), requestMeta(request), ex.getMessage());

        return ResponseEntity
                .status(status)
                .body(RestResponse.error(status.value(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<RestResponse<Map<String, List<String>>>> handleValidationException(MethodArgumentNotValidException ex, HttpServletRequest request) {
        ErrorCode errorCode = ErrorCode.VALIDATION_ERROR;
        HttpStatus status = statusOf(errorCode);

        log.warn("VALIDATION_ERROR | status={} code={} {} msg={}",
                status, errorCode.getCode(), requestMeta(request), ex.getMessage());

        Map<String, List<String>> errors = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.computeIfAbsent(error.getField(), k -> new ArrayList<>()).add(error.getDefaultMessage());
        }

        return ResponseEntity
                .status(status)
                .body(RestResponse.error(errors, status.value(), "Validation failed"));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<RestResponse<Object>> handleAccessDeniedException(AccessDeniedException ex, HttpServletRequest request) {
        HttpStatus status = HttpStatus.FORBIDDEN;

        log.warn("ACCESS_DENIED | status={} {} msg={}",
                status, requestMeta(request), ex.getMessage());

        return ResponseEntity
                .status(status)
                .body(RestResponse.error(status.value(), "Access denied"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<RestResponse<Object>> handleAllExceptions(Exception ex, HttpServletRequest request) {
        ErrorCode errorCode = ErrorCode.INTERNAL_ERROR;
        HttpStatus status = statusOf(errorCode);

        log.error("INTERNAL_ERROR | status={} code={} {} msg={}",
                status, errorCode.getCode(), requestMeta(request), ex.getMessage(), ex);

        return ResponseEntity
                .status(status)
                .body(RestResponse.error(status.value(), "Unexpected server error"));
    }
}
