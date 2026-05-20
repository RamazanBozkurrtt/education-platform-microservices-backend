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
    USER_PASSWORD_INVALID(2004,"Kullanıcı Şifresi Geçersiz",400),

    VALIDATION_ERROR(3001, "Hatalı değerler girildi", 400),
    UNSUPPORTED_MEDIA_TYPE(3002, "Desteklenmeyen Content-Type", 415),
    // Course
    COURSE_NOT_FOUND(7001, "Course not found", 404),
    COURSE_PUBLISH_INVALID(7002, "Course cannot be published", 400),
    COURSE_LESSON_NOT_FOUND(7003, "Lesson not found", 404),
    COURSE_MEDIA_STORAGE_ERROR(7004, "Medya depolama hatasi olustu", 500),
    COURSE_CATEGORY_NOT_FOUND(7005, "Course category not found", 404),
    COURSE_LEVEL_NOT_FOUND(7006, "Course level not found", 404),
    ENROLLMENT_NOT_FOUND(8001, "Enrollment not found", 404),
    ENROLLMENT_ALREADY_EXISTS(8002, "Enrollment already exists", 409),
    REVIEW_NOT_FOUND(9001, "Review not found", 404),
    REVIEW_ALREADY_EXISTS(9002, "Review already exists", 409),
    REVIEW_OWN_COURSE_FORBIDDEN(9003, "You cannot review your own course", 403),

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

