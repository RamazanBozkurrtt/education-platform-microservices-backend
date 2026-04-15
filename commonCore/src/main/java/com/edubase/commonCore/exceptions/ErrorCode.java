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
    AUTH_TOKEN_IS_BLACKLISTED(1006,"Token Blacklist'e Alınmış",403),
    AUTH_REFRESH_TOKEN_CONFLICT(1007, "Refresh token zaten kullanilmis", 409),
    AUTH_TOO_MANY_ATTEMPTS(1008, "Too many failed login attempts. Try again later.", 429),

    // User
    USER_NOT_FOUND(2001, "Kullanıcı bulunamadı", 404),
    USER_ALREADY_EXISTS(2002, "Bu email zaten kullanımda", 400),
    USER_ALREADY_DEACTIVATED(2003,"Kullanıcı zaten deaktif durumda",400),

    VALIDATION_ERROR(3001, "Hatalı değerler girildi", 400),
    // Course
    COURSE_NOT_FOUND(7001, "Course not found", 404),
    COURSE_PUBLISH_INVALID(7002, "Course cannot be published", 400),
    COURSE_LESSON_NOT_FOUND(7003, "Lesson not found", 404),
    ENROLLMENT_NOT_FOUND(8001, "Enrollment not found", 404),
    ENROLLMENT_ALREADY_EXISTS(8002, "Enrollment already exists", 409),

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

