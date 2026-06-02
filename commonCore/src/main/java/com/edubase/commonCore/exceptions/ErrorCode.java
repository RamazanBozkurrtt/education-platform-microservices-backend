package com.edubase.commonCore.exceptions;

import lombok.Getter;

@Getter
public enum ErrorCode {

    // Auth
    AUTH_LOGIN_FAILED(1001, "Email veya sifre hatali", 401),
    AUTH_TOKEN_EXPIRED(1002, "Oturum sureniz dolmus, lutfen tekrar giris yapin.", 401),
    AUTH_INVALID_SIGNATURE(1003, "Gecersiz token.", 403),
    AUTH_LOCKED_OR_INACTIVE(1004, "Hesabiniz kilitlenmis veya pasif durumda.", 403),
    AUTH_UNAUTHORIZED(1005, "Yetkisiz erisim.", 401),
    AUTH_TOKEN_IS_BLACKLISTED(1006, "Token blacklist'e alinmis.", 403),
    AUTH_REFRESH_TOKEN_CONFLICT(1007, "Refresh token zaten kullanilmis.", 409),
    AUTH_TOO_MANY_ATTEMPTS(1008, "Too many failed login attempts. Try again later.", 429),
    AUTH_REACTIVATION_REQUIRED(1009, "Hesabiniz deaktif. Reactivation linki email adresinize gonderildi.", 403),

    // User
    USER_NOT_FOUND(2001, "Kullanici bulunamadi", 404),
    USER_ALREADY_EXISTS(2002, "Bu email zaten kullanimda", 400),
    USER_ALREADY_DEACTIVATED(2003, "Kullanici zaten deaktif durumda", 400),
    USER_PASSWORD_INVALID(2004, "Kullanici sifresi gecersiz", 400),
    USER_PASSWORD_MISMATCH(2005, "New password and confirm password do not match.", 400),
    USER_REACTIVATION_REQUIRED_FOR_PASSWORD_RESET(2006, "Account must be reactivated before resetting password.", 403),
    USER_PASSWORD_RESET_TOKEN_INVALID(2007, "Invalid or already used password reset token.", 400),

    VALIDATION_ERROR(3001, "Hatali degerler girildi", 400),
    UNSUPPORTED_MEDIA_TYPE(3002, "Desteklenmeyen content-type", 415),

    // Course
    COURSE_NOT_FOUND(7001, "Course not found", 404),
    COURSE_PUBLISH_INVALID(7002, "Course cannot be published", 400),
    COURSE_LESSON_NOT_FOUND(7003, "Lesson not found", 404),
    COURSE_MEDIA_STORAGE_ERROR(7004, "Medya depolama hatasi olustu", 500),
    COURSE_CATEGORY_NOT_FOUND(7005, "Course category not found", 404),
    COURSE_LEVEL_NOT_FOUND(7006, "Course level not found", 404),
    FINAL_EXAM_NOT_FOUND(7010, "Final exam not found", 404),
    FINAL_EXAM_NOT_ACTIVE(7011, "Final exam is not active", 400),
    FINAL_EXAM_ATTEMPTS_EXHAUSTED(7012, "Final exam attempts exhausted", 409),
    FINAL_EXAM_ACTIVE_ATTEMPT_EXISTS(7013, "Active final exam attempt already exists", 409),
    FINAL_EXAM_ATTEMPT_NOT_FOUND(7014, "Final exam attempt not found", 404),
    FINAL_EXAM_ATTEMPT_INVALID_STATE(7015, "Final exam attempt state is invalid for this operation", 409),
    FINAL_EXAM_COURSE_NOT_COMPLETED(7016, "Course is not completed for final exam", 403),
    FINAL_EXAM_NOT_READY(7017, "Final exam is not ready for student attempts", 400),
    FINAL_EXAM_ALREADY_EXISTS(7018, "Active final exam already exists for course", 409),

    ENROLLMENT_NOT_FOUND(8001, "Enrollment not found", 404),
    ENROLLMENT_ALREADY_EXISTS(8002, "Enrollment already exists", 409),

    PAYMENT_NOT_FOUND(8501, "Payment not found", 404),
    PAYMENT_ALREADY_COMPLETED(8502, "Payment is already completed", 409),
    PAYMENT_ALREADY_REFUNDED(8503, "Payment is already refunded", 409),
    PAYMENT_NOT_REQUIRED(8504, "Course does not require payment", 400),
    PAYMENT_REQUIRED_FOR_ENROLLMENT(8505, "Successful payment is required for this enrollment", 402),
    PAYMENT_VERIFICATION_FAILED(8506, "Payment verification failed", 400),
    MISSING_CONFIRMATION_PAYLOAD(8507, "MISSING_CONFIRMATION_PAYLOAD", 400),
    INVALID_SIGNATURE(8508, "INVALID_SIGNATURE", 400),
    INVALID_PROVIDER_STATUS(8509, "INVALID_PROVIDER_STATUS", 400),
    PAYMENT_NOT_PENDING(8510, "PAYMENT_NOT_PENDING", 400),
    PAYMENT_OWNER_MISMATCH(8511, "PAYMENT_OWNER_MISMATCH", 403),
    INVOICE_NOT_FOUND(8601, "Invoice not found", 404),

    REVIEW_NOT_FOUND(9001, "Review not found", 404),
    REVIEW_ALREADY_EXISTS(9002, "Review already exists", 409),
    REVIEW_OWN_COURSE_FORBIDDEN(9003, "You cannot review your own course", 403),

    // General
    INTERNAL_ERROR(5000, "Bilinmeyen bir hata olustu", 500),

    // Db
    ROLE_NOT_FOUND(6001, "Rol bulunamadi", 404);

    private final int code;
    private final String message;
    private final int httpStatus;

    ErrorCode(int code, String message, int httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }
}
