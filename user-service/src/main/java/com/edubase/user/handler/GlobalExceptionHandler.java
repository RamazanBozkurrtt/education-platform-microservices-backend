package com.edubase.user.handler;


import com.edubase.commonCore.exceptions.BusinessException;
import com.edubase.commonCore.exceptions.ErrorCode;
import com.edubase.commonCore.utils.RestResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.security.SignatureException;
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

    // BusinessException -> ErrorCode uzerinden yonet
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

    // Ornek: JWT signature
    @ExceptionHandler(SignatureException.class)
    public ResponseEntity<RestResponse<Object>> handleSignatureException(SignatureException ex, HttpServletRequest request) {
        ErrorCode errorCode = ErrorCode.AUTH_INVALID_SIGNATURE;
        HttpStatus status = statusOf(errorCode);

        log.warn("SIGNATURE_EXCEPTION | status={} code={} {} msg={}",
                status, errorCode.getCode(), requestMeta(request), ex.getMessage());

        return ResponseEntity
                .status(status)
                .body(RestResponse.error(status.value(), ex.getMessage()));
    }

    // Validation errors
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
                .body(RestResponse.error(errors, status.value(), "Validasyon hatasi"));
    }

    @ExceptionHandler({LockedException.class, DisabledException.class})
    public ResponseEntity<RestResponse<Object>> handleAccountStatusException(Exception ex, HttpServletRequest request) {
        ErrorCode errorCode = ErrorCode.AUTH_LOCKED_OR_INACTIVE;
        HttpStatus status = statusOf(errorCode);

        log.warn("LOCKED_OR_DISABLED | status={} code={} {} msg={}",
                status, errorCode.getCode(), requestMeta(request), ex.getMessage());

        return ResponseEntity
                .status(status)
                .body(RestResponse.error(status.value(), "Hesap kilitli veya pasif"));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<RestResponse<Object>> handleAccessDeniedException(AccessDeniedException ex, HttpServletRequest request) {
        ErrorCode errorCode = ErrorCode.AUTH_UNAUTHORIZED;
        HttpStatus status = statusOf(errorCode);

        log.warn("ACCESS_DENIED | status={} code={} {} msg={}",
                status, errorCode.getCode(), requestMeta(request), ex.getMessage());

        return ResponseEntity
                .status(status)
                .body(RestResponse.error(status.value(), "Bu islem icin yetkiniz yok"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<RestResponse<Object>> handleAllExceptions(Exception ex, HttpServletRequest request) {
        ErrorCode errorCode = ErrorCode.INTERNAL_ERROR;
        HttpStatus status = statusOf(errorCode);

        log.error("INTERNAL_ERROR | status={} code={} {} msg={}",
                status, errorCode.getCode(), requestMeta(request), ex.getMessage(), ex);

        return ResponseEntity
                .status(status)
                .body(RestResponse.error(status.value(), "Sunucu tarafli beklenmeyen bir hata olustu."));
    }

}
