package com.edubase.common.handling;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

    // Auth Hataları
    AUTH_LOGIN_FAILED(1001, "Email veya şifre hatalı", HttpStatus.UNAUTHORIZED),
    AUTH_TOKEN_EXPIRED(1002, "Oturum süreniz dolmuş, lütfen tekrar giriş yapın.", HttpStatus.UNAUTHORIZED),
    AUTH_INVALID_SIGNATURE(1003, "Geçersiz Token!", HttpStatus.FORBIDDEN),
    AUTH_LOCKED_OR_INACTIVE(1004,"Hesabınız kilitlenmiş veya pasif durumda!",HttpStatus.FORBIDDEN),
    AUTH_UNAUTHORIZED(1005,"Yetkisiz erişim!",HttpStatus.UNAUTHORIZED),

    // User Hataları
    USER_NOT_FOUND(2001, "Kullanıcı bulunamadı", HttpStatus.NOT_FOUND),
    USER_ALREADY_EXISTS(2002, "Bu email zaten kullanımda", HttpStatus.BAD_REQUEST),

    // Genel
    INTERNAL_ERROR(5000, "Bilinmeyen bir hata oluştu", HttpStatus.INTERNAL_SERVER_ERROR),

    // Db hataları
    ROLE_NOT_FOUND(6001,"Rol bulunamadı",HttpStatus.NOT_FOUND);

    private final int code;
    private final String message;
    private final HttpStatus httpStatus;

    ErrorCode(int code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }
}