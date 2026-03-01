package com.edubase.auth.exceptions.handling;

import com.edubase.commonCore.exceptions.BusinessException;
import com.edubase.commonCore.exceptions.ErrorCode;
import com.edubase.commonCore.utils.RestResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

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

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<RestResponse<Object>> handleBusinessException(BusinessException ex, HttpServletRequest request) {
        ErrorCode errorCode = ex.getErrorCode();
        HttpStatus status = statusOf(errorCode);

        log.warn("BUSINESS_EXCEPTION | Status: {} | Code: {} | Path: {} | Msg: {}",
                status, errorCode.getCode(), request.getRequestURI(), ex.getMessage());

        return ResponseEntity
                .status(status)
                .body(RestResponse.error(status.value(), ex.getMessage()));
    }

    @ExceptionHandler(SignatureException.class)
    public ResponseEntity<RestResponse<Object>> handleSignatureException(SignatureException ex, HttpServletRequest request) {
        ErrorCode errorCode = ErrorCode.AUTH_INVALID_SIGNATURE;
        HttpStatus status = statusOf(errorCode);

        log.warn("SIGNATURE_EXCEPTION | Status: {} | Code: {} | Path: {} | Msg: {}",
                status, errorCode.getCode(), request.getRequestURI(), ex.getMessage());

        return ResponseEntity
                .status(status)
                .body(RestResponse.error(status.value(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<RestResponse<Map<String, List<String>>>> handleValidationException(MethodArgumentNotValidException ex, HttpServletRequest request) {
        ErrorCode errorCode = ErrorCode.VALIDATION_ERROR;
        HttpStatus status = statusOf(errorCode);

        log.warn("VALIDATION_ERROR | Status: {} | Code: {} | Path: {} | Msg: {}",
                status, errorCode.getCode(), request.getRequestURI(), ex.getMessage());

        Map<String, List<String>> errors = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.computeIfAbsent(error.getField(), k -> new ArrayList<>()).add(error.getDefaultMessage());
        }

        return ResponseEntity
                .status(status)
                .body(RestResponse.error(errors, status.value(), "Validation error"));
    }

    @ExceptionHandler({BadCredentialsException.class, UsernameNotFoundException.class})
    public ResponseEntity<RestResponse<Object>> handleAuthException(Exception ex, HttpServletRequest request) {
        ErrorCode errorCode = ErrorCode.AUTH_LOGIN_FAILED;
        HttpStatus status = statusOf(errorCode);

        log.warn("AUTH_ERROR | Status: {} | Code: {} | Path: {} | Msg: {}",
                status, errorCode.getCode(), request.getRequestURI(), ex.getMessage());

        return ResponseEntity
                .status(status)
                .body(RestResponse.error(status.value(), "Kullanici adi veya sifre hatali"));
    }

    @ExceptionHandler({LockedException.class, DisabledException.class})
    public ResponseEntity<RestResponse<Object>> handleAccountStatusException(Exception ex, HttpServletRequest request) {
        ErrorCode errorCode = ErrorCode.AUTH_LOCKED_OR_INACTIVE;
        HttpStatus status = statusOf(errorCode);

        log.warn("LOCKED_OR_DISABLED | Status: {} | Code: {} | Path: {} | Msg: {}",
                status, errorCode.getCode(), request.getRequestURI(), ex.getMessage());

        return ResponseEntity
                .status(status)
                .body(RestResponse.error(status.value(), "Hesap kilitli veya pasif"));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<RestResponse<Object>> handleAccessDeniedException(AccessDeniedException ex, HttpServletRequest request) {
        ErrorCode errorCode = ErrorCode.AUTH_UNAUTHORIZED;
        HttpStatus status = statusOf(errorCode);

        log.warn("ACCESS_DENIED | Status: {} | Code: {} | Path: {} | Msg: {}",
                status, errorCode.getCode(), request.getRequestURI(), ex.getMessage());

        return ResponseEntity
                .status(status)
                .body(RestResponse.error(status.value(), "Bu islem icin yetkiniz yok"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<RestResponse<Object>> handleAllExceptions(Exception ex, HttpServletRequest request) {
        ErrorCode errorCode = ErrorCode.INTERNAL_ERROR;
        HttpStatus status = statusOf(errorCode);

        log.error("INTERNAL_ERROR | Status: {} | Code: {} | Path: {} | Message: {}",
                status, errorCode.getCode(), request.getRequestURI(), ex.getMessage(), ex);

        return ResponseEntity
                .status(status)
                .body(RestResponse.error(status.value(), "Sunucu tarafli beklenmeyen bir hata olustu."));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public void handleNoResource() {
    }
}
