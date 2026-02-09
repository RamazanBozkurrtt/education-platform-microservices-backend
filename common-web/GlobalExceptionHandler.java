package com.edubase.common.handling;

import com.edubase.common.exceptions.BusinessException;
import com.edubase.common.utils.RestResponse;
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
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.security.SignatureException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<RestResponse<Object>> handleBusinessException(BusinessException ex, HttpServletRequest request) {
        log.warn("BUSINESS_EXCEPTION | Status: {} | Code: {} | Path: {} | Msg: {}",
                HttpStatus.BAD_REQUEST, ex.getErrorCode().getCode(), request.getRequestURI(), ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(RestResponse.error(HttpStatus.BAD_REQUEST, ex.getMessage()));
    }


    @ExceptionHandler(SignatureException.class)
    public  ResponseEntity<RestResponse<Object>> handleSignatureException(SignatureException ex, HttpServletRequest request) {
        log.warn("SIGNATURE_EXCEPTION | Status: {} | Code: {} | Path: {} | Msg: {}",
                HttpStatus.BAD_REQUEST, ErrorCode.AUTH_INVALID_SIGNATURE, request.getRequestURI(), ex.getMessage());
        return  ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(RestResponse.error(HttpStatus.UNAUTHORIZED, ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<RestResponse<Map<String, List<String>>>> handleValidationException(MethodArgumentNotValidException ex, HttpServletRequest request) {
        log.warn("VALIDATION_ERROR | Status: {} | Code: {} | Path: {} | Msg: {}",
                HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR, request.getRequestURI(), ex.getMessage());
        Map<String, List<String>> errors = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.computeIfAbsent(error.getField(), k -> new ArrayList<>()).add(error.getDefaultMessage());
        }
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(RestResponse.error(errors, HttpStatus.BAD_REQUEST, "Validasyon hatası"));
    }

    @ExceptionHandler({BadCredentialsException.class, UsernameNotFoundException.class})
    public ResponseEntity<RestResponse<Object>> handleAuthException(Exception ex, HttpServletRequest request) {
        log.warn("AUTH_ERROR | Status: {} | Code: {} | Path: {} | Msg: {}",
                HttpStatus.BAD_REQUEST, ErrorCode.USER_NOT_FOUND, request.getRequestURI(), ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(RestResponse.error(HttpStatus.UNAUTHORIZED, "Kullanıcı adı veya şifre hatalı"));
    }

    @ExceptionHandler({LockedException.class, DisabledException.class})
    public ResponseEntity<RestResponse<Object>> handleAccountStatusException(Exception ex, HttpServletRequest request) {
        log.warn("LOCKED_EXCEPTION-DISABLED_EXCEPTION | Status: {} | Code: {} | Path: {} | Msg: {}",
                HttpStatus.BAD_REQUEST,ErrorCode.AUTH_LOCKED_OR_INACTIVE, request.getRequestURI(), ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(RestResponse.error(HttpStatus.FORBIDDEN, "Hesap kilitli veya pasif"));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<RestResponse<Object>> handleAccessDeniedException(AccessDeniedException ex, HttpServletRequest request) {
        log.warn("ACCESS_DENIED_EXCEPTION | Status: {} | Code: {} | Path: {} | Msg: {}",
                HttpStatus.BAD_REQUEST,ErrorCode.AUTH_UNAUTHORIZED, request.getRequestURI(), ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(RestResponse.error(HttpStatus.FORBIDDEN, "Bu işlem için yetkiniz yok"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<RestResponse<Object>> handleAllExceptions(Exception ex, HttpServletRequest request) {
        log.error("INTERNAL SERVER ERROR | Path: {} | Message: {}", request.getRequestURI(), ex.getMessage(), ex);

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(RestResponse.error(HttpStatus.INTERNAL_SERVER_ERROR, "Sunucu taraflı beklenmeyen bir hata oluştu."));
    }
}
