package com.edubase.commonCore.exceptions;

import lombok.Getter;

@Getter
public enum ErrorCode {

    // Auth
    AUTH_LOGIN_FAILED(1001, "Email veya şifre hatalı", 401),
    AUTH_TOKEN_EXPIRED(1002, "Oturum süreniz dolmuş, lütfen tekrar giriş yapın.", 401),
    AUTH_INVALID_SIGNATURE(1003, "Geçersiz Token!", 403),
    AUTH_LOCKED_OR_INACTIVE(1004, "Hesabınız kilitlenmiş veya pasif durumda!", 403),
    AUTH_UNAUTHORIZED(1005, "Yetkisiz erişim!", 401),

    // User
    USER_NOT_FOUND(2001, "Kullanıcı bulunamadı", 404),
    USER_ALREADY_EXISTS(2002, "Bu email zaten kullanımda", 400),

    VALIDATION_ERROR(3001, "Hatalı değerler girildi", 400),

    // General
    INTERNAL_ERROR(5000, "Bilinmeyen bir hata oluştu", 500),

    // Db
    ROLE_NOT_FOUND(6001, "Rol bulunamadı", 404);

    private final int code;
    private final String message;
    private final int httpStatus; // <-- int yaptık

    ErrorCode(int code, String message, int httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }
}
